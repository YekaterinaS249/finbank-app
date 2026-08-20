package com.finbank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * LOGIN-FIELD-EMAIL-001 / LOGIN-FIELD-PASSWORD-001 (Web only): a blank email
 * or blank password must be rejected with an exact, field-specific message
 * ("Введите email." / "Введите пароль.") BEFORE authentication is attempted
 * — distinct from the generic anti-enumeration "Неверный email или
 * пароль." shown for a wrong-but-present credential (LOGIN-WEB-003).
 * <p>
 * This only runs in front of the web form-login POST (/login). The API path
 * gets the same exact messages via {@code LoginRequest}'s Bean Validation
 * annotations, so both surfaces stay predictable per the spec's closing
 * line: "Все требования должны быть реализованы одинаково предсказуемо в
 * Web Login и API Login там, где это применимо."
 * <p>
 * Deliberately a presence-only check: it never trims the password (that
 * would violate LOGIN-FIELD-PASSWORD constraints) and never rejects on
 * anything but blank/missing — format checks for email are left to the
 * normal authentication failure path so JS-disabled and server behavior
 * never diverge (see Login-Requirements.md's LOGIN-WEB-004/005/006 note).
 */
@Component
public class LoginFieldPresenceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String email = request.getParameter("username");
            String password = request.getParameter("password");

            if (email == null || email.trim().isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/login?emailError");
                return;
            }
            // Presence only — password must NEVER be trimmed anywhere in the
            // login path, so an all-whitespace password is intentionally
            // treated as "present" here and left to fail authentication
            // normally (identical to any other wrong password).
            if (password == null || password.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/login?passwordError");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
