package com.darshan.lending.service;

import com.darshan.lending.dto.OtpRequest;
import com.darshan.lending.dto.OtpVerifyRequest;
import com.darshan.lending.entity.OtpVerification;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.OtpPurpose;
import com.darshan.lending.entity.enums.OtpType;
import com.darshan.lending.entity.enums.UserStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.repository.OtpVerificationRepository;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String PHONE_REGEX = "^[6-9][0-9]{9}$";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

    private final OtpVerificationRepository otpRepo;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String sendOtp(OtpRequest request) {

        validateIdentifierMatchesOtpType(request.getIdentifier(), request.getOtpType());

        if (request.getOtpType() == OtpType.PHONE && (request.getCountryCode() == null || request.getCountryCode().isBlank())) {
            throw new BusinessException("Country code is required for PHONE type OTP");
        }

        if (request.getPurpose() == OtpPurpose.REGISTRATION) {
            boolean exists = userRepository.findByPhoneNumber(request.getIdentifier()).isPresent();
            if (exists) {
                throw new BusinessException("User already registered with " + request.getIdentifier() + ". Please login instead.");
            }
        }

        String otpCode = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        OtpVerification otp = OtpVerification.builder()
                .identifier(request.getIdentifier())
                .otpCode(otpCode)
                .otpType(request.getOtpType())
                .purpose(request.getPurpose())
                .countryCode(request.getCountryCode())
                .expiresAt(expiresAt)
                .verified(false)
                .build();

        otpRepo.save(otp);

        log.info("[DEV] OTP for {}: {}", request.getIdentifier(), otpCode);

        return otpCode;
    }

    @Transactional
    public Long verifyOtpAndCreateUser(OtpVerifyRequest request) {

        Optional<OtpVerification> optOtp = otpRepo
                .findTopByIdentifierAndVerifiedFalseOrderByCreatedAtDesc(
                        request.getIdentifier());

        OtpVerification otp = optOtp.orElseThrow(() ->
                new BusinessException("No pending OTP found for " + request.getIdentifier()));

        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            throw new BusinessException("OTP has expired. Please request a new one.");
        }

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            throw new BusinessException("Invalid OTP. Please try again.");
        }

        otp.setVerified(true);
        otpRepo.save(otp);

        log.info("OTP verified for identifier={}", request.getIdentifier());

        Optional<User> existingUser = userRepository.findByPhoneNumber(request.getIdentifier());
        if (existingUser.isPresent()) {
            return existingUser.get().getId();
        }

        User user = new User();
        user.setCountryCode(otp.getCountryCode());
        user.setPhoneNumber(request.getIdentifier());
        user.setPassword(passwordEncoder.encode(request.getIdentifier()));
        user.setPhoneVerified(true);
        user.setStatus(UserStatus.MOBILE_VERIFIED);

        userRepository.save(user);

        log.info("[DEV] New user created. Default password = phone number: {}", request.getIdentifier());

        return user.getId();
    }

    private void validateIdentifierMatchesOtpType(String identifier, OtpType otpType) {
        if (otpType == OtpType.PHONE) {
            if (!identifier.matches(PHONE_REGEX)) {
                throw new BusinessException(
                        "Identifier must be a valid 10-digit Indian mobile number (starting with 6-9) when otpType is PHONE"
                );
            }
        } else if (otpType == OtpType.EMAIL) {
            if (!identifier.matches(EMAIL_REGEX)) {
                throw new BusinessException(
                        "Identifier must be a valid email address when otpType is EMAIL"
                );
            }
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}