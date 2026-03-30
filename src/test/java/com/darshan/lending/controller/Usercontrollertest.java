package com.darshan.lending.controller;

import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password("hash")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
    }

    // ── GET /users/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{id} → 200 for existing user")
    void getUser_shouldReturn200() throws Exception {
        mockMvc.perform(get("/users/{id}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUser.getId()));
    }

    @Test
    @DisplayName("GET /users/{id} → 404 when user not found")
    void getUser_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/users/999999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /register ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register → 200 and status PLATFORM_ACCOUNT_CREATED")
    void registerUser_shouldReturn200() throws Exception {
        mockMvc.perform(post("/register")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegistrationBody("ABCDE1234F", "testuser@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLATFORM_ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.data.platformAccountNumber").isString());
    }

    @Test
    @DisplayName("POST /register → 400 when user is not MOBILE_VERIFIED")
    void registerUser_shouldReturn400WhenNotMobileVerified() throws Exception {
        testUser.setStatus(UserStatus.PLATFORM_ACCOUNT_CREATED);
        userRepository.save(testUser);
        mockMvc.perform(post("/register")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegistrationBody("ZZZZZ9999Z", "other@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register → 400 on duplicate PAN")
    void registerUser_shouldReturn400OnDuplicatePan() throws Exception {
        mockMvc.perform(post("/register")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegistrationBody("PANXX1234Y", "first@example.com")))
                .andExpect(status().isOk());

        User secondUser = userRepository.save(User.builder()
                .phoneNumber("9000011111").countryCode("+91")
                .email("second@lending.com").password("hash")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());

        mockMvc.perform(post("/register")
                        .param("userId", secondUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegistrationBody("PANXX1234Y", "second@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register → 404 when userId not found")
    void registerUser_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(post("/register")
                        .param("userId", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegistrationBody("ABCDE1234F", "testuser@example.com")))
                .andExpect(status().isNotFound());
    }

    // ── PUT /users/profile ────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/profile → 200 and updated fullName")
    void updateProfile_shouldReturn200() throws Exception {
        mockMvc.perform(put("/users/profile")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\",\"email\":\"new@email.com\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("New Name"))
                .andExpect(jsonPath("$.data.email").value("new@email.com"));
    }

    @Test
    @DisplayName("PUT /users/profile → 200 with only fullName changed (partial update)")
    void updateProfile_partialUpdate_shouldNotOverwriteOtherFields() throws Exception {
        mockMvc.perform(put("/users/profile")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Partial Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Partial Name"));
    }

    @Test
    @DisplayName("PUT /users/profile → 404 when user not found")
    void updateProfile_shouldReturn404WhenUserNotFound() throws Exception {
        mockMvc.perform(put("/users/profile")
                        .param("userId", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ghost\"}"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildRegistrationBody(String pan, String email) {
        return String.format(
                "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"%s\"," +
                        "\"phoneNumber\":\"9876543210\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\"," +
                        "\"role\":\"BORROWER\",\"pan\":\"%s\",\"incomeBracket\":\"5-10 LPA\"," +
                        "\"p2pExperience\":\"BEGINNER\",\"password\":\"Test@1234\"," +
                        "\"address\":{\"line1\":\"123 Street\",\"city\":\"Bengaluru\"," +
                        "\"state\":\"Karnataka\",\"pincode\":\"560001\"}}", email, pan);
    }
}