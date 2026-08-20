package com.finbank.security;

import com.finbank.model.AccountStatus;
import com.finbank.model.User;
import com.finbank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LOGIN-STATUS-001/002: an ACTIVE user's UserDetails must be enabled, a
 * BLOCKED user's must be disabled — this is the single switch that makes
 * Spring Security reject a blocked login the same way it rejects a wrong
 * password (see AuthApiController's unified AuthenticationException catch).
 */
class FinbankUserDetailsServiceTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final FinbankUserDetailsService service = new FinbankUserDetailsService(userRepository);

    private User userWith(AccountStatus status) {
        User u = new User();
        u.setEmail("jane@example.com");
        u.setPasswordHash("hashed-value-not-relevant-here");
        u.setStatus(status);
        return u;
    }

    @Test
    void activeUser_isEnabled() {
        Mockito.when(userRepository.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(Optional.of(userWith(AccountStatus.ACTIVE)));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertTrue(details.isEnabled());
    }

    @Test
    void blockedUser_isDisabled() {
        Mockito.when(userRepository.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(Optional.of(userWith(AccountStatus.BLOCKED)));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertFalse(details.isEnabled());
    }

    @Test
    void unknownEmail_throwsUsernameNotFound() {
        Mockito.when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nobody@example.com"));
    }

    @Test
    void emailWithLeadingAndTrailingWhitespace_isTrimmedBeforeLookup() {
        // LOGIN-FIELD-EMAIL-004/005: whitespace must be stripped before the
        // repository lookup, uniformly for Web and API — both paths funnel
        // through this one method.
        Mockito.when(userRepository.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(Optional.of(userWith(AccountStatus.ACTIVE)));

        UserDetails details = service.loadUserByUsername("  jane@example.com  ");

        assertEquals("jane@example.com", details.getUsername());
        Mockito.verify(userRepository).findByEmailIgnoreCase("jane@example.com");
    }

    @Test
    void passwordHashIsCarriedThroughUnchanged() {
        User user = userWith(AccountStatus.ACTIVE);
        user.setPasswordHash("$argon2id$v=19$m=16384,t=2,p=1$somesalt$somehash");
        Mockito.when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jane@example.com");

        assertEquals("$argon2id$v=19$m=16384,t=2,p=1$somesalt$somehash", details.getPassword());
    }
}
