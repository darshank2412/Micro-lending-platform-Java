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

    @Test
    void sendOtp_shouldReturnSixDigitCode() {
        OtpRequest req = OtpRequest.builder()
                .identifier("9876543210").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN).countryCode("+91").build();
        String otp = otpService.sendOtp(req);
        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("[0-9]{6}"));
    }

    @Test
    void verifyOtp_shouldCreateNewUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("8888877777").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION).countryCode("+91").build());
        Long userId = otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                .identifier("8888877777").otpCode(otp)
                .otpType(OtpType.PHONE).purpose(OtpPurpose.REGISTRATION)
                .countryCode("+91").role(Role.BORROWER).build());
        assertNotNull(userId);
        assertTrue(userRepository.findById(userId).isPresent());
    }

    @Test
    void verifyOtp_shouldReturnExistingUser() {
        String otp = otpService.sendOtp(OtpRequest.builder()
                .identifier("9876543210").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.LOGIN).countryCode("+91").build());
        Long userId = otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                .identifier("9876543210").otpCode(otp)
                .otpType(OtpType.PHONE).purpose(OtpPurpose.LOGIN)
                .countryCode("+91").role(Role.BORROWER).build());
        assertEquals(testUser.getId(), userId);
    }

    @Test
    void verifyOtp_shouldFailWhenNoPendingOtp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                        .identifier("9000000000")  // ← valid phone, no OTP sent
                        .otpCode("123456")
                        .otpType(OtpType.PHONE).purpose(OtpPurpose.REGISTRATION)
                        .countryCode("+91").role(Role.BORROWER).build()));
        assertTrue(ex.getMessage().contains("No pending OTP found"));
    }

    @Test
    void verifyOtp_shouldFailWhenWrongCode() {
        otpService.sendOtp(OtpRequest.builder()
                .identifier("7777766666").otpType(OtpType.PHONE)
                .purpose(OtpPurpose.REGISTRATION).countryCode("+91").build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtpAndCreateUser(OtpVerifyRequest.builder()
                        .identifier("7777766666").otpCode("000000")
                        .otpType(OtpType.PHONE).purpose(OtpPurpose.REGISTRATION)
                        .countryCode("+91").role(Role.BORROWER).build()));
        assertTrue(ex.getMessage().contains("Invalid OTP"));
    }
}
