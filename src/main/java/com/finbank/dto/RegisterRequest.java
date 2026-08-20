package com.finbank.dto;

/**
 * Deliberately NOT annotated with Bean Validation (@NotBlank/@Email/etc)
 * and dateOfBirth is intentionally a raw String, not LocalDate: if Spring's
 * DataBinder rejected a malformed date before the request reaches the
 * controller, that would short-circuit our collect-all error contract with
 * a generic 400. All rules (format, boundaries, cross-field, and DB-backed
 * duplicate checks) are instead enforced by
 * {@link com.finbank.validation.RegistrationValidator}, so every rule gets
 * its own stable error code and every field is validated together in one
 * pass, per the Registration Field Validation spec.
 */
public class RegisterRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String dateOfBirth; // expected format: yyyy-MM-dd
    private String password;
    private String confirmPassword;
    private boolean termsAccepted;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }
}
