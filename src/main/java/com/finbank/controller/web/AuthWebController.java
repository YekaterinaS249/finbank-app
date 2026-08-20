package com.finbank.controller.web;

import com.finbank.dto.FieldError;
import com.finbank.dto.RegisterRequest;
import com.finbank.exception.RegistrationValidationException;
import com.finbank.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AuthWebController {

    private final AuthService authService;

    public AuthWebController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registerRequest") RegisterRequest registerRequest, Model model) {
        try {
            authService.register(registerRequest);
        } catch (RegistrationValidationException e) {
            // Group by field so the template can show every error for a
            // field, and re-render with the values the user already typed
            // (password fields intentionally excluded — never echo those back).
            Map<String, List<FieldError>> byField = e.getErrors().stream()
                    .collect(Collectors.groupingBy(FieldError::getField));
            model.addAttribute("fieldErrors", byField);
            registerRequest.setPassword(null);
            registerRequest.setConfirmPassword(null);
            model.addAttribute("registerRequest", registerRequest);
            return "register";
        }

        return "redirect:/login?registered";
    }
}
