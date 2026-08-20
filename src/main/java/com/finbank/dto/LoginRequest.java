package com.finbank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    // LOGIN-FIELD-EMAIL-001/002/003: required, valid format, max length 254
    // (RFC 5321 mailbox length limit — same boundary used at registration).
    @NotBlank(message = "Введите email.")
    @Email(message = "Введите email.")
    @Size(max = 254, message = "Введите email.")
    private String email;

    // LOGIN-FIELD-PASSWORD-001: required. No length/composition checks here
    // on purpose — login only ever checks for presence + match against the
    // stored hash; the password policy (length, etc.) is enforced only at
    // registration time, not re-validated on every login attempt.
    @NotBlank(message = "Введите пароль.")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
