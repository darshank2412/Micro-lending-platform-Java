package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OtpServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired OtpService     otpService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210")
                .countryCode("+91")
                .email("test@lending.com")
                .password("hash")
                .role(Role.BORROWER)
//                .state(UserStatus.MOBILE_VERIFIED)
                .emailVerified(false)
                .phoneVerified(true)
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    // ── Send OTP ──────────────────────────────────────────────────────────

    @Test
    void sendOtp_shouldReturnSixDigitCode() {
        OtpRequest req = OtpRequest.builder()
                .identifier("9876543210")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN)
                .countryCode("+91")
                .build();

        String otp = otpService.sendOtp(req);

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("[0-9]{6}"));
    }

    // ── Verify OTP — REGISTRATION ─────────────────────────────────────────

    @Test
    void verifyOtp_shouldCreateNewUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("8888877777")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .build());

        // FIX: verifyOtpAndCreateUser returns OtpVerifyResponse, not Long
        OtpVerifyResponse response = otpService.verifyOtpAndCreateUser(
                OtpVerifyRequest.builder()
                        .identifier("8888877777")
                        .otpCode(otp)
                        .build());

        assertNotNull(response);
        assertNotNull(response.getUserId());
        assertTrue(userRepository.findById(response.getUserId()).isPresent());
        assertTrue(response.getMessage().contains("Registration successful"));
    }

    // ── Verify OTP — LOGIN (existing user) ────────────────────────────────

    @Test
    void verifyOtp_shouldReturnExistingUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9876543210")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN)
                .countryCode("+91")
                .build());

        // FIX: returns OtpVerifyResponse with userId + token for LOGIN
        OtpVerifyResponse response = otpService.verifyOtpAndCreateUser(
                OtpVerifyRequest.builder()
                        .identifier("9876543210")
                        .otpCode(otp)
                        .build());

        assertNotNull(response);
        assertEquals(testUser.getId(), response.getUserId());
        assertNotNull(response.getToken()); // JWT token present for LOGIN
        assertEquals("Login successful.", response.getMessage());
    }

    // ── Verify OTP — no pending OTP ───────────────────────────────────────

    @Test
    void verifyOtp_shouldFailWhenNoPendingOtp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(
                        OtpVerifyRequest.builder()
                                .identifier("9000000000")
                                .otpCode("123456")
                                .build()));

        assertTrue(ex.getMessage().contains("No pending OTP found"));
    }

    // ── Verify OTP — wrong code ───────────────────────────────────────────

    @Test
    void verifyOtp_shouldFailWhenWrongCode() {
        otpService.sendOtp(OtpRequest.builder()
                .identifier("7777766666")
                .otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91")
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(
                        OtpVerifyRequest.builder()
                                .identifier("7777766666")
                                .otpCode("000000")
                                .build()));

        assertTrue(ex.getMessage().contains("Invalid OTP"));
    }
}