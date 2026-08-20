package com.finbank.controller.api;

import com.finbank.dto.ApiError;
import com.finbank.dto.JwtResponse;
import com.finbank.dto.LoginRequest;
import com.finbank.dto.RegisterRequest;
import com.finbank.model.User;
import com.finbank.security.JwtUtil;
import com.finbank.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    /**
     * LOGIN-API-006/007/LOGIN-STATUS-002 + section 7: one identical message
     * for wrong password, unknown email, AND blocked account — the client
     * can never distinguish which case it hit.
     */
    private static final String INVALID_CREDENTIALS_MESSAGE = "Неверный email или пароль.";

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthApiController(AuthService authService, AuthenticationManager authenticationManager,
                              JwtUtil jwtUtil) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public JwtResponse register(@RequestBody RegisterRequest request) {
        User user = authService.register(request);
        String token = jwtUtil.generateToken(user.getEmail());
        return new JwtResponse(token, jwtUtil.expirationSeconds(), user.getEmail());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            // AuthenticationException covers BOTH BadCredentialsException
            // (wrong password / unknown email — hidden as the same thing by
            // DaoAuthenticationProvider) AND DisabledException (blocked
            // account, see FinbankUserDetailsService). Catching the common
            // superclass, rather than just BadCredentialsException, is what
            // keeps a blocked account from leaking a different error shape.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException e) {
            ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                    INVALID_CREDENTIALS_MESSAGE, List.of(), httpRequest.getRequestURI());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String token = jwtUtil.generateToken(normalizedEmail);
        return ResponseEntity.ok(new JwtResponse(token, jwtUtil.expirationSeconds(), normalizedEmail));
    }
}
