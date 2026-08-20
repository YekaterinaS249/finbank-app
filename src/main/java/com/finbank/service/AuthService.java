package com.finbank.service;

import com.finbank.dto.FieldError;
import com.finbank.dto.RegisterRequest;
import com.finbank.exception.RegistrationValidationException;
import com.finbank.model.User;
import com.finbank.repository.UserRepository;
import com.finbank.validation.RegistrationValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final RegistrationValidator registrationValidator;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AccountService accountService, RegistrationValidator registrationValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.registrationValidator = registrationValidator;
    }

    @Transactional
    public User register(RegisterRequest request) {
        // Phase 1: format/boundary/cross-field rules — never touches the DB.
        List<FieldError> formatErrors = registrationValidator.validateFormat(request);
        if (!formatErrors.isEmpty()) {
            throw new RegistrationValidationException(HttpStatus.UNPROCESSABLE_ENTITY, formatErrors);
        }

        // Phase 2: only runs once format is clean — duplicate email/phone.
        List<FieldError> duplicateErrors = registrationValidator.validateDuplicates(request);
        if (!duplicateErrors.isEmpty()) {
            throw new RegistrationValidationException(HttpStatus.CONFLICT, duplicateErrors);
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(registrationValidator.normalizeEmail(request.getEmail()));
        user.setPhone(request.getPhone().trim());
        user.setDateOfBirth(LocalDate.parse(request.getDateOfBirth().trim()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setTermsAcceptedAt(Instant.now());

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request won the unique-constraint race
            // between our pre-check (phase 2) and this save. Same contract
            // the client would have seen if the pre-check alone had caught it.
            throw new RegistrationValidationException(HttpStatus.CONFLICT, List.of(
                    new FieldError("email", "EMAIL_ALREADY_REGISTERED",
                            "An account with this email or phone already exists")));
        }

        // Every new customer starts with one funded checking account so
        // there's something to see/transfer immediately after signup.
        accountService.openAccount(user, com.finbank.model.AccountType.CHECKING, "USD",
                new java.math.BigDecimal("1000.00"));

        return user;
    }
}
