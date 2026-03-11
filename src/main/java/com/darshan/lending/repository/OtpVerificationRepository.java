package com.darshan.lending.repository;

import com.darshan.lending.entity.OtpVerification;
import com.darshan.lending.entity.enums.OtpPurpose;
import com.darshan.lending.entity.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByIdentifierAndOtpTypeAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
            String identifier, OtpType otpType, OtpPurpose purpose);
}
