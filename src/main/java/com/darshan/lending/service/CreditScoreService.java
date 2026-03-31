package com.darshan.lending.service;

import com.darshan.lending.dto.CreditScoreResponse;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.KycStatus;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoreService {

    private final UserRepository        userRepository;
    private final LoanSummaryRepository loanSummaryRepository;
    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final KycDocumentRepository kycDocumentRepository;

    /**
     * Score breakdown (max 100):
     *  - KYC verified           : 25 pts
     *  - Loan repayment history : 40 pts  (EMIs paid on time)
     *  - Active loan count      : 15 pts  (fewer active = better)
     *  - Profile completeness   : 20 pts  (email, DOB, address)
     */
    @Transactional(readOnly = true)
    public CreditScoreResponse calculateScore(Long borrowerId) {

        User user = userRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + borrowerId));

        int score = 0;
        StringBuilder breakdown = new StringBuilder();

        // 1. KYC Score (25 pts)
        int kycScore = 0;
        if (user.getKycStatus() == KycStatus.VERIFIED) {
            kycScore = 25;
        } else if (user.getKycStatus() == KycStatus.PENDING) {
            kycScore = 10;
        }
        score += kycScore;
        breakdown.append("KYC: ").append(kycScore).append("/25 | ");

        // 2. Repayment History Score (40 pts)
        int repaymentScore = 0;
        long totalLoans = loanSummaryRepository.countByBorrowerId(borrowerId);
        if (totalLoans > 0) {
            long completedLoans = loanSummaryRepository.countCompletedByBorrowerId(borrowerId);
            int paidEmis  = emiScheduleRepository.countPaidEmis(borrowerId, com.darshan.lending.entity.enums.EmiStatus.PAID);
            int totalEmis = (int) emiScheduleRepository.countTotalEmisByBorrower(borrowerId);

            if (totalEmis > 0) {
                double paymentRatio = (double) paidEmis / totalEmis;
                repaymentScore = (int) (paymentRatio * 40);
            }

            // Bonus for fully completed loans
            if (completedLoans > 0) repaymentScore = Math.min(40, repaymentScore + 5);
        } else {
            repaymentScore = 20; // No history — neutral
        }
        score += repaymentScore;
        breakdown.append("Repayment: ").append(repaymentScore).append("/40 | ");

        // 3. Active Loan Score (15 pts)
        // 0 active loans  → 15 pts (no debt burden)
        // 1 active loan   → 15 pts (normal, expected)
        // 2 active loans  → 5 pts  (moderate risk)
        // 3+ active loans → 0 pts  (high debt burden)
        long activeLoans = loanSummaryRepository.countActiveByBorrowerId(borrowerId);
        int activeScore = activeLoans == 0 ? 15 : activeLoans == 1 ? 10 : activeLoans == 2 ? 10 : 0;
        score += activeScore;
        breakdown.append("ActiveLoans: ").append(activeScore).append("/15 | ");

        // 4. Profile Completeness (20 pts)
        int profileScore = 0;
        if (user.getEmail() != null)         profileScore += 5;
        if (user.getDateOfBirth() != null)   profileScore += 5;
        if (user.getFullName() != null)      profileScore += 5;
        if (user.getPan() != null)           profileScore += 5;
        score += profileScore;
        breakdown.append("Profile: ").append(profileScore).append("/20");

        String grade = score >= 80 ? "EXCELLENT"
                : score >= 60 ? "GOOD"
                : score >= 40 ? "FAIR"
                : "POOR";

        log.info("Credit score calculated: borrowerId={} score={} grade={}", borrowerId, score, grade);

        return CreditScoreResponse.builder()
                .borrowerId(borrowerId)
                .borrowerName(user.getFullName())
                .score(score)
                .maxScore(100)
                .grade(grade)
                .breakdown(breakdown.toString())
                .recommendation(getRecommendation(grade))
                .build();
    }

    private String getRecommendation(String grade) {
        return switch (grade) {
            case "EXCELLENT" -> "Highly creditworthy. Eligible for maximum loan amounts.";
            case "GOOD"      -> "Good credit. Eligible for standard loan products.";
            case "FAIR"      -> "Moderate risk. Eligible for smaller loan amounts.";
            default          -> "High risk. Complete KYC and build repayment history first.";
        };
    }
}