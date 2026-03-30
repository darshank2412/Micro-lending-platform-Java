package com.darshan.lending.controller;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.service.OtpService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired OtpService otpService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password("hash")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
    }

    // ── Send OTP ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/otp/send → 200 for valid REGISTRATION request")
    void sendOtp_shouldReturn200() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"9111122233\",\"otpType\":\"PHONE\"," +
                                "\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/otp/send → 200 for LOGIN purpose")
    void sendOtp_login_shouldReturn200() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"9876543210\",\"otpType\":\"PHONE\"," +
                                "\"purpose\":\"LOGIN\",\"countryCode\":\"+91\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/otp/send → 400 when body is empty")
    void sendOtp_shouldReturn400OnEmptyBody() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/otp/send → 400 when identifier is missing")
    void sendOtp_shouldReturn400WhenIdentifierMissing() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otpType\":\"PHONE\",\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/otp/verify → 200 and returns userId for new BORROWER")
    void verifyOtp_shouldReturn200AndUserId() throws Exception {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9222233344").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION).countryCode("+91").build());
        String body = String.format(
                "{\"identifier\":\"9222233344\",\"otpCode\":\"%s\",\"otpType\":\"PHONE\"," +
                        "\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\",\"role\":\"BORROWER\"}", otp);
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    @DisplayName("POST /auth/otp/verify → 200 and returns existing userId on LOGIN")
    void verifyOtp_login_shouldReturnExistingUserId() throws Exception {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9876543210").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN).countryCode("+91").build());
        String body = String.format(
                "{\"identifier\":\"9876543210\",\"otpCode\":\"%s\",\"otpType\":\"PHONE\"," +
                        "\"purpose\":\"LOGIN\",\"countryCode\":\"+91\",\"role\":\"BORROWER\"}", otp);
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(testUser.getId()));
    }

    @Test
    @DisplayName("POST /auth/otp/verify → 400 when OTP code is wrong")
    void verifyOtp_shouldReturn400OnWrongCode() throws Exception {
        otpService.sendOtp(OtpRequest.builder()
                .identifier("9333344455").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION).countryCode("+91").build());
        String body = "{\"identifier\":\"9333344455\",\"otpCode\":\"000000\",\"otpType\":\"PHONE\"," +
                "\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\",\"role\":\"BORROWER\"}";
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/otp/verify → 400 when no pending OTP exists")
    void verifyOtp_shouldReturn400WhenNoPendingOtp() throws Exception {
        String body = "{\"identifier\":\"9000000000\",\"otpCode\":\"123456\",\"otpType\":\"PHONE\"," +
                "\"purpose\":\"REGISTRATION\",\"countryCode\":\"+91\",\"role\":\"BORROWER\"}";
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}