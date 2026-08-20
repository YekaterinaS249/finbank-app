package com.finbank.validation;

import com.finbank.dto.FieldError;
import com.finbank.dto.RegisterRequest;
import com.finbank.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Implements every rule from the Registration Field & Boundary Validation
 * spec: required fields, format/boundary validation, password policy,
 * password confirmation match, terms acceptance, and duplicate
 * email/phone.
 *
 * <p>Password policy follows the dedicated Password Requirements spec:
 * length 15-128, no composition requirements (no mandatory
 * uppercase/lowercase/digit/special-character categories), case-sensitive,
 * no common-password/breach/Unicode/whitespace rules, no strength meter —
 * those are explicitly out of scope. Do not reintroduce them here without
 * updating that spec first.
 *
 * <p>Two-phase, collect-all: {@link #validateFormat} runs every rule that
 * doesn't need the database and returns ALL violations at once (never just
 * the first). Only if that list is empty does the caller proceed to
 * {@link #validateDuplicates}, which hits the DB. This keeps a single
 * response from ever mixing 422-class (format) and 409-class (conflict)
 * causes, matching the documented ordering.
 */
@Component
public class RegistrationValidator {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s'-]*$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private static final int NAME_MAX_LENGTH = 60;
    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int PASSWORD_MIN_LENGTH = 15;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final int MIN_AGE_YEARS = 18;
    private static final int MAX_AGE_YEARS = 120;

    private final UserRepository userRepository;

    public RegistrationValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<FieldError> validateFormat(RegisterRequest request) {
        List<FieldError> errors = new ArrayList<>();

        validateName("firstName", request.getFirstName(), errors);
        validateName("lastName", request.getLastName(), errors);
        validateEmail(request.getEmail(), errors);
        validatePhone(request.getPhone(), errors);
        validateDateOfBirth(request.getDateOfBirth(), errors);
        validatePasswords(request.getPassword(), request.getConfirmPassword(), errors);
        validateTerms(request.isTermsAccepted(), errors);

        return errors;
    }

    public List<FieldError> validateDuplicates(RegisterRequest request) {
        List<FieldError> errors = new ArrayList<>();

        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            errors.add(new FieldError("email", "EMAIL_ALREADY_REGISTERED",
                    "An account with this email already exists"));
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            errors.add(new FieldError("phone", "PHONE_ALREADY_REGISTERED",
                    "An account with this phone number already exists"));
        }

        return errors;
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    // ---- individual field rules -------------------------------------------------

    private void validateName(String field, String value, List<FieldError> errors) {
        String trimmed = value == null ? "" : value.trim();
        String code = field.equals("firstName") ? "FIRST_NAME" : "LAST_NAME";

        if (trimmed.isEmpty()) {
            errors.add(new FieldError(field, code + "_REQUIRED", humanize(field) + " is required"));
            return;
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            errors.add(new FieldError(field, code + "_TOO_LONG",
                    humanize(field) + " must be at most " + NAME_MAX_LENGTH + " characters"));
            return;
        }
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            errors.add(new FieldError(field, code + "_INVALID_CHARACTERS",
                    humanize(field) + " may only contain letters, spaces, hyphens and apostrophes"));
        }
    }

    /** Returns the normalized (trimmed, lower-cased) email for reuse by password checks. */
    private String validateEmail(String email, List<FieldError> errors) {
        String trimmed = email == null ? "" : email.trim();

        if (trimmed.isEmpty()) {
            errors.add(new FieldError("email", "EMAIL_REQUIRED", "Email is required"));
            return null;
        }
        if (trimmed.length() > EMAIL_MAX_LENGTH) {
            errors.add(new FieldError("email", "EMAIL_TOO_LONG",
                    "Email must be at most " + EMAIL_MAX_LENGTH + " characters"));
            return null;
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            errors.add(new FieldError("email", "EMAIL_INVALID_FORMAT", "Email must be a valid address"));
            return null;
        }
        return trimmed.toLowerCase();
    }

    private void validatePhone(String phone, List<FieldError> errors) {
        if (phone == null || phone.isBlank()) {
            errors.add(new FieldError("phone", "PHONE_REQUIRED", "Phone number is required"));
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            errors.add(new FieldError("phone", "PHONE_INVALID_FORMAT",
                    "Phone number must be in E.164 format, e.g. +14155552671"));
        }
    }

    private void validateDateOfBirth(String rawDate, List<FieldError> errors) {
        if (rawDate == null || rawDate.isBlank()) {
            errors.add(new FieldError("dateOfBirth", "DATE_OF_BIRTH_REQUIRED", "Date of birth is required"));
            return;
        }

        LocalDate dob;
        try {
            dob = LocalDate.parse(rawDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errors.add(new FieldError("dateOfBirth", "DATE_OF_BIRTH_INVALID_FORMAT",
                    "Date of birth must be in yyyy-MM-dd format"));
            return;
        }

        LocalDate today = LocalDate.now();
        if (dob.isAfter(today)) {
            errors.add(new FieldError("dateOfBirth", "DATE_OF_BIRTH_IN_FUTURE",
                    "Date of birth cannot be in the future"));
            return;
        }
        if (ChronoUnit.YEARS.between(dob, today) > MAX_AGE_YEARS) {
            errors.add(new FieldError("dateOfBirth", "DATE_OF_BIRTH_UNREALISTIC",
                    "Date of birth is not realistic"));
            return;
        }
        if (ChronoUnit.YEARS.between(dob, today) < MIN_AGE_YEARS) {
            errors.add(new FieldError("dateOfBirth", "USER_UNDERAGE",
                    "You must be at least " + MIN_AGE_YEARS + " years old to register"));
        }
    }

    /**
     * Length only (15-128), case-sensitive, no composition requirements —
     * see the Password Requirements spec. Whitespace and Unicode are not
     * specially handled in either direction (no rule exists for them), so
     * a password is never trimmed here — trimming would silently change
     * what the user typed, which the spec forbids ("no silent truncation").
     */
    private void validatePasswords(String password, String confirmPassword, List<FieldError> errors) {
        boolean passwordPresent = true;

        if (password == null || password.isEmpty()) {
            errors.add(new FieldError("password", "PASSWORD_REQUIRED", "Password is required"));
            passwordPresent = false;
        } else if (password.length() < PASSWORD_MIN_LENGTH) {
            errors.add(new FieldError("password", "PASSWORD_TOO_SHORT",
                    "Password must be at least " + PASSWORD_MIN_LENGTH + " characters"));
        } else if (password.length() > PASSWORD_MAX_LENGTH) {
            errors.add(new FieldError("password", "PASSWORD_TOO_LONG",
                    "Password must be at most " + PASSWORD_MAX_LENGTH + " characters"));
        }

        if (confirmPassword == null || confirmPassword.isEmpty()) {
            errors.add(new FieldError("confirmPassword", "CONFIRM_PASSWORD_REQUIRED",
                    "Please confirm your password"));
        } else if (passwordPresent && !confirmPassword.equals(password)) {
            errors.add(new FieldError("confirmPassword", "PASSWORD_MISMATCH", "Пароли не совпадают."));
        }
    }

    private void validateTerms(boolean termsAccepted, List<FieldError> errors) {
        if (!termsAccepted) {
            errors.add(new FieldError("termsAccepted", "TERMS_NOT_ACCEPTED",
                    "You must accept the Terms & Conditions to register"));
        }
    }

    private String humanize(String field) {
        return field.equals("firstName") ? "First name" : "Last name";
    }
}
