package com.finbank.config;

import com.finbank.security.FinbankUserDetailsService;
import com.finbank.security.JwtAuthFilter;
import com.finbank.security.JwtAuthenticationEntryPoint;
import com.finbank.security.LoginFieldPresenceFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Argon2id, per the Password Requirements spec — memory-hard, resistant
     * to GPU/ASIC cracking, the currently recommended default for new
     * password storage (OWASP Password Storage Cheat Sheet). Spring
     * Security's {@code defaultsForSpringSecurity_v5_8()} preset already
     * targets Argon2id with reasonable memory/iteration/parallelism cost
     * parameters — no need to hand-tune those for this project.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(FinbankUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * JwtAuthFilter is wired explicitly into the API chain below via
     * addFilterBefore. Without this, Spring Boot would ALSO auto-register
     * it as a global servlet filter (because it's a @Component implementing
     * Filter), running it twice and on every non-API route too.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> disableAutoRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Same reasoning as disableAutoRegistration above, for the other
     * @Component-annotated Filter: without this, Spring Boot would also
     * auto-register LoginFieldPresenceFilter globally in addition to its
     * explicit wiring via addFilterBefore below.
     */
    @Bean
    public FilterRegistrationBean<LoginFieldPresenceFilter> disableLoginFieldFilterAutoRegistration(
            LoginFieldPresenceFilter filter) {
        FilterRegistrationBean<LoginFieldPresenceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Stateless JWT chain for the REST API surface — this is what your
     * Playwright/REST-Assured API tests will hit.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                               JwtAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                // Explicit entry point: without one, a stateless chain with
                // no formLogin/httpBasic can default to 403 instead of 401
                // for missing/invalid credentials — see JwtAuthenticationEntryPoint.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Classic session-based form login for the server-rendered Thymeleaf UI.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, LoginFieldPresenceFilter loginFieldPresenceFilter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/css/**", "/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                // LOGIN-FIELD-EMAIL-001/LOGIN-FIELD-PASSWORD-001: exact
                // field-required messages, checked before Spring Security's
                // own UsernamePasswordAuthenticationFilter runs.
                .addFilterBefore(loginFieldPresenceFilter, UsernamePasswordAuthenticationFilter.class)
                // H2 console renders in a frame and posts plain forms; relax just for it (dev-only).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
