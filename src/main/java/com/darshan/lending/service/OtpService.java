package com.darshan.lending.service;

import com.darshan.lending.dto.OtpRequest;
import com.darshan.lending.dto.OtpVerifyRequest;
import com.darshan.lending.dto.OtpVerifyResponse;
import com.darshan.lending.entity.OtpVerification;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.OtpPurpose;
import com.darshan.lending.entity.enums.OtpType;
import com.darshan.lending.entity.enums.UserStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.repository.OtpVerificationRepository;
import com.darshan.lending.repository.UserRepository;
import com.darshan.lending.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES    = 10;
    private static final int TOKEN_EXPIRY_MINUTES  = 15;
    private static final SecureRandom RANDOM       = new SecureRandom();

    private static final String PHONE_REGEX = "^[6-9][0-9]{9}$";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

    private final OtpVerificationRepository otpRepo;
    private final UserRepository            userRepository;
    private final PasswordEncoder           passwordEncoder;
    private final JwtUtil                   jwtUtil;

    // ── Send OTP ─────────────────────────────────────────────────────────

    @Transactional
    public String sendOtp(OtpRequest request) {

        validateIdentifierMatchesOtpType(request.getIdentifier(), request.getOtpType());

        if (request.getOtpType() == OtpType.PHONE &&
                (request.getCountryCode() == null || request.getCountryCode().isBlank())) {
            throw new BusinessException("Country code is required for PHONE type OTP");
        }

        if (request.getPurpose() == OtpPurpose.REGISTRATION) {
            boolean exists = userRepository.findByPhoneNumber(request.getIdentifier()).isPresent();
            if (exists) {
                throw new BusinessException("User already registered with "
                        + request.getIdentifier() + ". Please login instead.");
            }
        }

        if (request.getPurpose() == OtpPurpose.RESET) {
            boolean exists = userRepository.findByPhoneNumber(request.getIdentifier()).isPresent();
            if (!exists) {
                throw new BusinessException("No account found with "
                        + request.getIdentifier() + ". Please register first.");
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

    // ── Verify OTP ───────────────────────────────────────────────────────

    @Transactional
    public OtpVerifyResponse verifyOtpAndCreateUser(OtpVerifyRequest request) {

        OtpVerification otp = otpRepo
                .findTopByIdentifierAndVerifiedFalseOrderByCreatedAtDesc(
                        request.getIdentifier())
                .orElseThrow(() -> new BusinessException(
                        "No pending OTP found for " + request.getIdentifier()));

        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            throw new BusinessException("OTP has expired. Please request a new one.");
        }

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            throw new BusinessException("Invalid OTP. Please try again.");
        }

        otp.setVerified(true);
        otpRepo.save(otp);

        log.info("OTP verified for identifier={}", request.getIdentifier());

        // ── REGISTRATION ─────────────────────────────────────────────────
        if (otp.getPurpose() == OtpPurpose.REGISTRATION) {
            Optional<User> existingUser = userRepository.findByPhoneNumber(request.getIdentifier());
            if (existingUser.isPresent()) {
                return OtpVerifyResponse.builder()
                        .userId(existingUser.get().getId())
                        .message("User already exists. Please login.")
                        .build();
            }

            User user = new User();
            user.setCountryCode(otp.getCountryCode());
            user.setPhoneNumber(request.getIdentifier());
            user.setPassword(passwordEncoder.encode(request.getIdentifier()));
            user.setPhoneVerified(true);
            user.setStatus(UserStatus.MOBILE_VERIFIED);
            userRepository.save(user);

            log.info("[DEV] New user created. Default password = phone number: {}",
                    request.getIdentifier());

            return OtpVerifyResponse.builder()
                    .userId(user.getId())
                    .message("Registration successful. Please complete your profile.")
                    .build();
        }

        // ── LOGIN ─────────────────────────────────────────────────────────
        if (otp.getPurpose() == OtpPurpose.LOGIN) {
            User user = userRepository.findByPhoneNumber(request.getIdentifier())
                    .orElseThrow(() -> new BusinessException(
                            "No account found. Please register first."));

            String token = jwtUtil.generateToken(
                    user.getPhoneNumber(),
                    user.getRole().name()
            );

            log.info("JWT generated for userId={} role={}", user.getId(), user.getRole());

            return OtpVerifyResponse.builder()
                    .userId(user.getId())
                    .token(token)
                    .role(user.getRole().name())
                    .fullName(user.getFullName())
                    .message("Login successful.")
                    .build();
        }

        // ── RESET — generate resetToken, save on user, return in response ─
        if (otp.getPurpose() == OtpPurpose.RESET) {
            User user = userRepository.findByPhoneNumber(request.getIdentifier())
                    .orElseThrow(() -> new BusinessException(
                            "No account found for " + request.getIdentifier()));

            // Generate a short-lived token — valid 15 minutes
            String resetToken = UUID.randomUUID().toString();
            user.setResetToken(resetToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
            userRepository.save(user);

            log.info("Reset token generated for userId={}", user.getId());

            return OtpVerifyResponse.builder()
                    .userId(user.getId())
                    .token(resetToken)   // client uses this token to call /auth/reset-password
                    .message("OTP verified. Use the token to reset your password within 15 minutes.")
                    .build();
        }

        throw new BusinessException("Unknown OTP purpose: " + otp.getPurpose());
    }

    // ── Reset Password ───────────────────────────────────────────────────

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {

        // 1. Find user by resetToken
        User user = userRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new BusinessException(
                        "Invalid or expired reset token. Please request a new OTP."));

        // 2. Check token hasn't expired
        if (user.getResetTokenExpiry() == null ||
                LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new BusinessException(
                    "Reset token has expired. Please request a new OTP.");
        }

        // 3. Validate new password
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new BusinessException("Password must be at least 6 characters.");
        }

        // 4. Update password and invalidate token so it can't be reused
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        log.info("Password reset successful for userId={}", user.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void validateIdentifierMatchesOtpType(String identifier, OtpType otpType) {
        if (otpType == OtpType.PHONE) {
            if (!identifier.matches(PHONE_REGEX)) {
                throw new BusinessException(
                        "Identifier must be a valid 10-digit Indian mobile number " +
                                "(starting with 6-9) when otpType is PHONE");
            }
        } else if (otpType == OtpType.EMAIL) {
            if (!identifier.matches(EMAIL_REGEX)) {
                throw new BusinessException(
                        "Identifier must be a valid email address when otpType is EMAIL");
            }
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}