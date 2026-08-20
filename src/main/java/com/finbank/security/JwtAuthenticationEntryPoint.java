package com.finbank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finbank.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Without an explicit entry point, a stateless Spring Security chain with
 * no interactive auth mechanism (no formLogin/httpBasic) can fall back to
 * 403 for missing/invalid credentials instead of 401 — a well-known gotcha
 * for JWT-only APIs. The Login spec (section 8, "JWT Security") is
 * explicit: a protected endpoint without a valid JWT must return 401, not
 * 403. This entry point makes that contract certain rather than incidental,
 * and returns the same ApiError envelope the rest of the API uses.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                "Authentication is required to access this resource.", List.of(), request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
