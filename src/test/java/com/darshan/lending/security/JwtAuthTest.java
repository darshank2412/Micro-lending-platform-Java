package com.darshan.lending.security;

import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.entity.enums.UserStatus;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    @Test
    @DisplayName("POST /auth/login → 200 with valid credentials")
    void login_validCredentials_returns200() throws Exception {
        userRepository.save(User.builder()
                .phoneNumber("9000000001").countryCode("+91")
                .email("test@test.com")
                .password(passwordEncoder.encode("password123"))
                .fullName("Test User").role(Role.BORROWER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9000000001\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.role").value("BORROWER"));
    }

    @Test
    @DisplayName("POST /auth/login → 401 with wrong password")
    void login_wrongPassword_returns401() throws Exception {
        userRepository.save(User.builder()
                .phoneNumber("9000000002").countryCode("+91")
                .email("test2@test.com")
                .password(passwordEncoder.encode("correctpass"))
                .fullName("Test User 2").role(Role.BORROWER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"9000000002\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /loan-products → 401 without token")
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/loan-products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /loan-products → 200 with valid JWT token")
    void protectedEndpoint_validToken_returns200() throws Exception {
        String token = jwtUtil.generateToken("9000000003", "ADMIN");

        mockMvc.perform(get("/loan-products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /loan-products → 401 with expired/invalid token")
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/loan-products")
                        .header("Authorization", "Bearer invalidtoken123"))
                .andExpect(status().isUnauthorized());
    }
}