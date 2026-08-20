package com.finbank.security;

import com.finbank.model.AccountStatus;
import com.finbank.model.User;
import com.finbank.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class FinbankUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public FinbankUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // LOGIN-FIELD-EMAIL-004/005: leading/trailing whitespace must be
        // trimmed before lookup, uniformly for both Web and API login (this
        // is the single shared place both paths funnel through). Only
        // whitespace is stripped — case-insensitivity is handled separately
        // by findByEmailIgnoreCase, and no other normalization is applied.
        String normalizedEmail = email == null ? null : email.trim();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));

        // LOGIN-STATUS-002/003: a BLOCKED user must never get an
        // authenticated session/JWT, and the failure must look identical to
        // a wrong password from the outside. Marking the UserDetails
        // disabled lets Spring Security's own pre-authentication checks
        // reject it (DisabledException) — for the web flow this lands on
        // the same generic "/login?error" page as bad credentials with no
        // extra code, and for the API flow AuthApiController maps every
        // AuthenticationException subtype to the same 401 response.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(user.getStatus() == AccountStatus.BLOCKED)
                .authorities(Collections.emptyList())
                .build();
    }
}
