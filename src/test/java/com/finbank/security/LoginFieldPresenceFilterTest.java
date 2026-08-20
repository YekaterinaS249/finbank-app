package com.finbank.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LOGIN-FIELD-EMAIL-001 / LOGIN-FIELD-PASSWORD-001: blank email or blank
 * password on the web login POST must redirect with the field-specific
 * error flag BEFORE authentication runs, and must never trim the password
 * value while deciding "blank".
 */
class LoginFieldPresenceFilterTest {

    private final LoginFieldPresenceFilter filter = new LoginFieldPresenceFilter();

    private HttpServletRequest requestFor(String method, String servletPath, String email, String password) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getParameter("username")).thenReturn(email);
        when(request.getParameter("password")).thenReturn(password);
        when(request.getContextPath()).thenReturn("");
        return request;
    }

    @Test
    void blankEmail_redirectsWithEmailError() throws Exception {
        HttpServletRequest request = requestFor("POST", "/login", "  ", "somePassword");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/login?emailError");
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void blankPassword_redirectsWithPasswordError() throws Exception {
        HttpServletRequest request = requestFor("POST", "/login", "jane@example.com", "");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/login?passwordError");
        verify(chain, never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void whitespaceOnlyPassword_isTreatedAsPresent_notTrimmed() throws Exception {
        // A password of only spaces is technically "present" — it must fall
        // through to normal authentication (and fail there as a wrong
        // password), never be judged "blank" by trimming it here.
        HttpServletRequest request = requestFor("POST", "/login", "jane@example.com", "   ");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response, never()).sendRedirect(anyString());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void nonLoginRequest_passesThroughUntouched() throws Exception {
        HttpServletRequest request = requestFor("GET", "/dashboard", null, null);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response, never()).sendRedirect(anyString());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void validEmailAndPassword_passesThrough() throws Exception {
        HttpServletRequest request = requestFor("POST", "/login", "jane@example.com", "somePassword");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(response, never()).sendRedirect(anyString());
        verify(chain, times(1)).doFilter(request, response);
    }
}
