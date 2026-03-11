package com.darshan.lending.config;

import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.security.CustomUserDetailsService;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(customAuthEntryPoint())
                )

                .authorizeHttpRequests(auth -> auth

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // OTP APIs
                        .requestMatchers(
                                "/auth/otp/send",
                                "/auth/otp/verify"
                        ).permitAll()

                        // ✅ Registration (PUBLIC)
                        .requestMatchers(HttpMethod.POST, "/users/register").permitAll()

                        // ===============================
                        // ADMIN ONLY - Loan Products
                        // ===============================
                        .requestMatchers(HttpMethod.POST,   "/loan-products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/loan-products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/loan-products/**").hasRole("ADMIN")

                        // ===============================
                        // ADMIN ONLY - Savings Products
                        // ===============================
                        .requestMatchers(HttpMethod.POST,   "/savings-products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/savings-products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/savings-products/**").hasRole("ADMIN")

                        // Everything else requires login
                        .anyRequest().authenticated()
                )

                .userDetailsService(userDetailsService)

                .httpBasic(basic ->
                        basic.authenticationEntryPoint(customAuthEntryPoint())
                );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint customAuthEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Unauthorized: Please provide valid credentials\",\"data\":null}"
            );
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Optional: Print users at startup (for debugging)
    @Bean
    CommandLineRunner printUsers(UserRepository repo) {
        return args -> {
            repo.findAll().forEach(user -> {
                System.out.println("USER FOUND -> Phone: " + user.getPhoneNumber() +
                        " | Role: " + user.getRole());
            });
        };
    }
}