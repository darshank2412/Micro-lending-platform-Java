package com.darshan.lending.config;

import com.darshan.lending.idempotency.IdempotencyFilter;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.security.CustomUserDetailsService;
import com.darshan.lending.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthFilter            jwtAuthFilter;
    private final IdempotencyFilter        idempotencyFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── JSON responses for 401 and 403 ───────────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Unauthorized. Please provide a valid JWT token.\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json");
                            response.setStatus(403);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Forbidden. You do not have permission.\",\"data\":null}");
                        })
                )

                .authorizeHttpRequests(auth -> auth

                        // ── Public ───────────────────────────────────────────────────
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/auth/otp/send", "/auth/otp/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/register").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/reset-password").permitAll()

                        // ── ADMIN ONLY — Loan Products ────────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/loan-products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/loan-products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/loan-products/**").hasRole("ADMIN")

                        // ── ADMIN ONLY — Savings Products ─────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/savings-products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/savings-products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/savings-products/**").hasRole("ADMIN")

                        // ── ADMIN ONLY — Admin Management ─────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/admins").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/admins").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/admins/**").hasRole("ADMIN")

                        // ── ADMIN — Loan Request Matchmaking ──────────────────────────
                        .requestMatchers(HttpMethod.PATCH, "/loan-requests/*/match").hasRole("ADMIN")

                        // ── LENDER — Accept a matched loan request ────────────────────
                        .requestMatchers(HttpMethod.PATCH, "/loan-requests/*/accept").hasRole("LENDER")

                        // ── BORROWER ONLY — Loan Requests ─────────────────────────────
                        .requestMatchers(HttpMethod.POST,  "/loan-requests").hasRole("BORROWER")
                        .requestMatchers(HttpMethod.PATCH, "/loan-requests/*/cancel").hasRole("BORROWER")
                        .requestMatchers(HttpMethod.GET,   "/loan-requests/my").hasRole("BORROWER")

                        // ── ADMIN ONLY — Loan Request Management ──────────────────────
                        .requestMatchers(HttpMethod.GET,   "/loan-requests").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/loan-requests/*/reject").hasRole("ADMIN")

                        // ── LENDER ONLY — Lender Preferences ─────────────────────────
                        .requestMatchers(HttpMethod.POST,  "/lender-preferences").hasRole("LENDER")
                        .requestMatchers(HttpMethod.PATCH, "/lender-preferences/deactivate").hasRole("LENDER")
                        .requestMatchers(HttpMethod.GET,   "/lender-preferences/my").hasRole("LENDER")

                        // ── ADMIN ONLY — View All Lender Preferences ──────────────────
                        .requestMatchers(HttpMethod.GET,   "/lender-preferences").hasRole("ADMIN")

                        // ── ADMIN ONLY — Loan Management ──────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/loans").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/loans/paged").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/loans/*/disburse").hasRole("ADMIN")

                        // ── BORROWER ONLY — Loan Actions ──────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/loans/my").hasRole("BORROWER")
                        .requestMatchers(HttpMethod.POST, "/loans/*/pay-emi").hasRole("BORROWER")
                        .requestMatchers(HttpMethod.POST, "/loans/*/foreclose").hasRole("BORROWER")
                        .requestMatchers(HttpMethod.POST, "/loans/*/partial-repayment").hasRole("BORROWER")

                        // ── LENDER ONLY — Funded Loans ────────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/loans/funded").hasRole("LENDER")

                        // ── SHARED — Loan Details & Schedule ──────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/loans/*").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/loans/*/schedule").authenticated()


                                .requestMatchers(HttpMethod.POST, "/notifications/emi-reminder/**").authenticated()
                                .requestMatchers(HttpMethod.POST, "/notifications/emi-reminder/bulk").hasRole("ADMIN")
                        // ── Everything else requires login ────────────────────────────
                        .anyRequest().authenticated()

                        // Notifications

                )

                // ── Idempotency filter runs before JWT filter ─────────────────────
                .addFilterBefore(idempotencyFilter, UsernamePasswordAuthenticationFilter.class)

                // ── JWT filter ────────────────────────────────────────────────────
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Debug: print all users at startup
    @Bean
    CommandLineRunner printUsers(UserRepository repo) {
        return args -> {
            repo.findAll().forEach(user ->
                    System.out.println("USER FOUND -> Phone: " + user.getPhoneNumber()
                            + " | Role: " + user.getRole()));
        };
    }
}


