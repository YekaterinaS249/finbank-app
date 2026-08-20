package com.finbank.validation;

import com.finbank.dto.FieldError;
import com.finbank.dto.RegisterRequest;
import com.finbank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the boundary/negative table from the Registration Field &
 * Boundary Validation spec directly against RegistrationValidator, without
 * needing a running Spring context or database.
 */
class RegistrationValidatorTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final RegistrationValidator validator = new RegistrationValidator(userRepository);

    private RegisterRequest validRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setFirstName("Jane");
        r.setLastName("Doe");
        r.setEmail("jane.doe@example.com");
        r.setPhone("+14155552671");
        r.setDateOfBirth(LocalDate.now().minusYears(30).toString());
        r.setPassword("correcthorsebattery");
        r.setConfirmPassword("correcthorsebattery");
        r.setTermsAccepted(true);
        return r;
    }

    private Set<String> codesFor(RegisterRequest request) {
        return validator.validateFormat(request).stream().map(FieldError::getCode).collect(Collectors.toSet());
    }

    @Test
    void validRequestProducesNoErrors() {
        assertTrue(validator.validateFormat(validRequest()).isEmpty());
    }

    @Test
    void collectAll_returnsEveryViolationInOneCall() {
        RegisterRequest r = validRequest();
        r.setEmail("not-an-email");
        r.setPassword("short1");
        r.setConfirmPassword("different");

        List<FieldError> errors = validator.validateFormat(r);

        assertTrue(errors.stream().anyMatch(e -> e.getCode().equals("EMAIL_INVALID_FORMAT")));
        assertTrue(errors.stream().anyMatch(e -> e.getCode().equals("PASSWORD_TOO_SHORT")));
        assertTrue(errors.stream().anyMatch(e -> e.getCode().equals("PASSWORD_MISMATCH")));
    }

    // ---- firstName / lastName ----------------------------------------------------

    @Test
    void firstName_blank_isRequired() {
        RegisterRequest r = validRequest();
        r.setFirstName("   ");
        assertTrue(codesFor(r).contains("FIRST_NAME_REQUIRED"));
    }

    @Test
    void firstName_60Chars_isValid() {
        RegisterRequest r = validRequest();
        r.setFirstName("A".repeat(60));
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void firstName_61Chars_tooLong() {
        RegisterRequest r = validRequest();
        r.setFirstName("A".repeat(61));
        assertTrue(codesFor(r).contains("FIRST_NAME_TOO_LONG"));
    }

    @Test
    void firstName_withDigits_invalidCharacters() {
        RegisterRequest r = validRequest();
        r.setFirstName("John123");
        assertTrue(codesFor(r).contains("FIRST_NAME_INVALID_CHARACTERS"));
    }

    @Test
    void firstName_withHyphenAndApostrophe_isValid() {
        RegisterRequest r = validRequest();
        r.setFirstName("Jean-Luc");
        r.setLastName("O'Brien");
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void firstName_cyrillic_isValid() {
        RegisterRequest r = validRequest();
        r.setFirstName("Анна");
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void firstName_scriptTag_rejectedAsInvalidCharacters() {
        RegisterRequest r = validRequest();
        r.setFirstName("<script>alert(1)</script>");
        assertTrue(codesFor(r).contains("FIRST_NAME_INVALID_CHARACTERS"));
    }

    // ---- email ---------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "plainaddress",
            "'@missing-local.com'",
            "missing-domain@",
            "two@@at.com"
    })
    void email_invalidFormats_rejected(String email) {
        RegisterRequest r = validRequest();
        r.setEmail(email);
        assertTrue(codesFor(r).contains("EMAIL_INVALID_FORMAT"));
    }

    @Test
    void email_plusAddressing_isValid() {
        RegisterRequest r = validRequest();
        r.setEmail("user+tag@domain.com");
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void email_tooLong_rejected() {
        RegisterRequest r = validRequest();
        r.setEmail("a".repeat(250) + "@a.co"); // > 254 chars total
        assertTrue(codesFor(r).contains("EMAIL_TOO_LONG"));
    }

    // ---- phone ---------------------------------------------------------------------

    @Test
    void phone_missingPlus_invalidFormat() {
        RegisterRequest r = validRequest();
        r.setPhone("14155552671");
        assertTrue(codesFor(r).contains("PHONE_INVALID_FORMAT"));
    }

    @Test
    void phone_15Digits_isValid() {
        RegisterRequest r = validRequest();
        r.setPhone("+123456789012345");
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void phone_16Digits_invalidFormat() {
        RegisterRequest r = validRequest();
        r.setPhone("+1234567890123456");
        assertTrue(codesFor(r).contains("PHONE_INVALID_FORMAT"));
    }

    @Test
    void phone_leadingZeroAfterPlus_invalidFormat() {
        RegisterRequest r = validRequest();
        r.setPhone("+0123456789");
        assertTrue(codesFor(r).contains("PHONE_INVALID_FORMAT"));
    }

    // ---- dateOfBirth -----------------------------------------------------------------

    @Test
    void dateOfBirth_exactly18YearsAgo_isValid() {
        RegisterRequest r = validRequest();
        r.setDateOfBirth(LocalDate.now().minusYears(18).toString());
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void dateOfBirth_oneDayShyOf18_underage() {
        RegisterRequest r = validRequest();
        r.setDateOfBirth(LocalDate.now().minusYears(18).plusDays(1).toString());
        assertTrue(codesFor(r).contains("USER_UNDERAGE"));
    }

    @Test
    void dateOfBirth_inFuture_rejected() {
        RegisterRequest r = validRequest();
        r.setDateOfBirth(LocalDate.now().plusDays(1).toString());
        assertTrue(codesFor(r).contains("DATE_OF_BIRTH_IN_FUTURE"));
    }

    @Test
    void dateOfBirth_wrongFormat_rejected() {
        RegisterRequest r = validRequest();
        r.setDateOfBirth("01/01/2000");
        assertTrue(codesFor(r).contains("DATE_OF_BIRTH_INVALID_FORMAT"));
    }

    @Test
    void dateOfBirth_unrealisticallyOld_rejected() {
        RegisterRequest r = validRequest();
        r.setDateOfBirth("1850-01-01");
        assertTrue(codesFor(r).contains("DATE_OF_BIRTH_UNREALISTIC"));
    }

    // ---- password / confirmPassword (Password Requirements spec: 15-128 chars,
    //      no composition rules, case-sensitive) -----------------------------------------

    @Test
    void password_14Chars_tooShort() {
        RegisterRequest r = validRequest();
        String pwd = "a".repeat(14);
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).contains("PASSWORD_TOO_SHORT"));
    }

    @Test
    void password_15Chars_isValid() {
        RegisterRequest r = validRequest();
        String pwd = "a".repeat(15);
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_128Chars_isValid() {
        RegisterRequest r = validRequest();
        String pwd = "a".repeat(128);
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_129Chars_tooLong() {
        RegisterRequest r = validRequest();
        String pwd = "a".repeat(129);
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).contains("PASSWORD_TOO_LONG"));
    }

    @Test
    void password_onlyLowercaseLetters_isValid_noCompositionRuleEnforced() {
        RegisterRequest r = validRequest();
        String pwd = "onlylowercaseletters"; // no digit, no uppercase, no special char
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_onlyUppercaseLetters_isValid() {
        RegisterRequest r = validRequest();
        String pwd = "ONLYUPPERCASELETTERS";
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_withoutDigits_isValid() {
        RegisterRequest r = validRequest();
        String pwd = "PasswordNoDigitsHere";
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_withoutSpecialCharacters_isValid() {
        RegisterRequest r = validRequest();
        String pwd = "Password12345678"; // letters + digits, no special char
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void password_noLegacyCompositionCodesExist() {
        // Regression guard: these codes must never be emitted again — the
        // spec explicitly removed the letter/digit composition requirement.
        RegisterRequest r = validRequest();
        String pwd = "aaaaaaaaaaaaaaa"; // 15 lowercase letters, no digit
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        Set<String> codes = codesFor(r);
        assertFalse(codes.contains("PASSWORD_MISSING_DIGIT"));
        assertFalse(codes.contains("PASSWORD_MISSING_LETTER"));
        assertFalse(codes.contains("PASSWORD_CONTAINS_PERSONAL_INFO"));
    }

    @Test
    void confirmPassword_matches_noMismatchError() {
        RegisterRequest r = validRequest();
        String pwd = "CorrectPassword1234567";
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    @Test
    void confirmPassword_mismatch_rejectedWithExactMessage() {
        RegisterRequest r = validRequest();
        r.setPassword("CorrectPassword1234567");
        r.setConfirmPassword("DifferentPassword123456");

        List<FieldError> errors = validator.validateFormat(r);
        FieldError mismatch = errors.stream()
                .filter(e -> e.getCode().equals("PASSWORD_MISMATCH"))
                .findFirst().orElseThrow();

        assertEquals("Пароли не совпадают.", mismatch.getMessage());
    }

    @Test
    void confirmPassword_differsOnlyByCase_treatedAsMismatch() {
        // Case-sensitivity: same characters, different case, must NOT be
        // treated as equal.
        RegisterRequest r = validRequest();
        r.setPassword("SecurePassword1234567");
        r.setConfirmPassword("securePassword1234567");
        assertTrue(codesFor(r).contains("PASSWORD_MISMATCH"));
    }

    @Test
    void confirmPassword_blank_requiredError() {
        RegisterRequest r = validRequest();
        r.setPassword("CorrectPassword1234567");
        r.setConfirmPassword("");
        assertTrue(codesFor(r).contains("CONFIRM_PASSWORD_REQUIRED"));
    }

    @Test
    void password_withSpacesAndUnicode_isValid() {
        // No dedicated whitespace/Unicode rule exists — length is the only gate.
        RegisterRequest r = validRequest();
        String pwd = "Pass word Unïcödé"; // 18 chars incl. spaces/diacritics
        r.setPassword(pwd);
        r.setConfirmPassword(pwd);
        assertTrue(codesFor(r).isEmpty());
    }

    // ---- termsAccepted -----------------------------------------------------------------

    @Test
    void termsNotAccepted_rejected() {
        RegisterRequest r = validRequest();
        r.setTermsAccepted(false);
        assertTrue(codesFor(r).contains("TERMS_NOT_ACCEPTED"));
    }

    // ---- duplicates (DB-backed phase) --------------------------------------------------

    @Test
    void duplicateEmail_and_duplicatePhone_bothReportedTogether() {
        Mockito.when(userRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(true);
        Mockito.when(userRepository.existsByPhone("+14155552671")).thenReturn(true);

        List<FieldError> errors = validator.validateDuplicates(validRequest());

        Set<String> codes = errors.stream().map(FieldError::getCode).collect(Collectors.toSet());
        assertTrue(codes.contains("EMAIL_ALREADY_REGISTERED"));
        assertTrue(codes.contains("PHONE_ALREADY_REGISTERED"));
        assertEquals(2, errors.size());
    }

    @Test
    void noDuplicates_returnsEmpty() {
        Mockito.when(userRepository.existsByEmailIgnoreCase(Mockito.anyString())).thenReturn(false);
        Mockito.when(userRepository.existsByPhone(Mockito.anyString())).thenReturn(false);

        assertTrue(validator.validateDuplicates(validRequest()).isEmpty());
    }
}
