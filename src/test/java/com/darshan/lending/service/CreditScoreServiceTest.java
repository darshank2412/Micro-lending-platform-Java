package com.darshan.lending.service;

import com.darshan.lending.dto.CreditScoreResponse;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.EmiStatus;
import com.darshan.lending.entity.enums.KycStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditScoreServiceTest {

    @Mock UserRepository        userRepository;
    @Mock LoanSummaryRepository loanSummaryRepository;
    @Mock EmiScheduleRepository emiScheduleRepository;
    @Mock LoanRequestRepository loanRequestRepository;
    @Mock KycDocumentRepository kycDocumentRepository;

    @InjectMocks CreditScoreService service;

    private User fullProfileUser;

    @BeforeEach
    void setUp() {
        fullProfileUser = User.builder()
                .id(1L)
                .role(Role.BORROWER)
                .fullName("Alice Smith")
                .email("alice@example.com")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .pan("ABCDE1234F")
                .kycStatus(KycStatus.VERIFIED)
                .build();
    }

    @Test
    @DisplayName("calculateScore: EXCELLENT score — verified KYC, full profile, no active loans")
    void calculateScore_excellent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(3L);
        when(loanSummaryRepository.countCompletedByBorrowerId(1L)).thenReturn(3L);
        when(emiScheduleRepository.countPaidEmis(eq(1L), eq(EmiStatus.PAID))).thenReturn(36);
        when(emiScheduleRepository.countTotalEmisByBorrower(1L)).thenReturn(36L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(0L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getScore()).isGreaterThanOrEqualTo(80);
        assertThat(response.getGrade()).isEqualTo("EXCELLENT");
        assertThat(response.getRecommendation()).contains("Highly creditworthy");
    }

    @Test
    @DisplayName("calculateScore: GOOD score — verified KYC, partial repayment history")
    void calculateScore_good() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(1L);
        when(loanSummaryRepository.countCompletedByBorrowerId(1L)).thenReturn(0L);
        when(emiScheduleRepository.countPaidEmis(eq(1L), eq(EmiStatus.PAID))).thenReturn(6);
        when(emiScheduleRepository.countTotalEmisByBorrower(1L)).thenReturn(12L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(1L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getScore()).isBetween(60, 79);
        assertThat(response.getGrade()).isEqualTo("GOOD");
        assertThat(response.getRecommendation()).contains("Good credit");
    }

    @Test
    @DisplayName("calculateScore: FAIR score — pending KYC, 1 active loan")
    void calculateScore_fair() {
        fullProfileUser.setKycStatus(KycStatus.PENDING);
        fullProfileUser.setEmail(null);
        fullProfileUser.setDateOfBirth(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(1L);
        when(loanSummaryRepository.countCompletedByBorrowerId(1L)).thenReturn(0L);
        when(emiScheduleRepository.countPaidEmis(eq(1L), eq(EmiStatus.PAID))).thenReturn(3);
        when(emiScheduleRepository.countTotalEmisByBorrower(1L)).thenReturn(12L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(2L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getScore()).isBetween(40, 59);
        assertThat(response.getGrade()).isEqualTo("FAIR");
        assertThat(response.getRecommendation()).contains("Moderate risk");
    }

    @Test
    @DisplayName("calculateScore: POOR score — rejected KYC, many active loans, incomplete profile")
    void calculateScore_poor() {
        User poorUser = User.builder()
                .id(2L)
                .role(Role.BORROWER)
                .fullName(null)
                .email(null)
                .dateOfBirth(null)
                .pan(null)
                .kycStatus(KycStatus.REJECTED)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(poorUser));
        when(loanSummaryRepository.countByBorrowerId(2L)).thenReturn(1L);
        when(loanSummaryRepository.countCompletedByBorrowerId(2L)).thenReturn(0L);
        when(emiScheduleRepository.countPaidEmis(eq(2L), eq(EmiStatus.PAID))).thenReturn(0);
        when(emiScheduleRepository.countTotalEmisByBorrower(2L)).thenReturn(12L);
        when(loanSummaryRepository.countActiveByBorrowerId(2L)).thenReturn(5L);

        CreditScoreResponse response = service.calculateScore(2L);

        assertThat(response.getScore()).isLessThan(40);
        assertThat(response.getGrade()).isEqualTo("POOR");
        assertThat(response.getRecommendation()).contains("High risk");
    }

    @Test
    @DisplayName("calculateScore: no loan history → neutral repayment score (20)")
    void calculateScore_noLoanHistory_neutralScore() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(0L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(0L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getBreakdown()).contains("Repayment: 20/40");
    }

    @Test
    @DisplayName("calculateScore: user not found → ResourceNotFoundException")
    void calculateScore_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateScore(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("calculateScore: breakdown contains all sections")
    void calculateScore_breakdownContainsAllSections() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(0L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(0L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getBreakdown()).contains("KYC:");
        assertThat(response.getBreakdown()).contains("Repayment:");
        assertThat(response.getBreakdown()).contains("ActiveLoans:");
        assertThat(response.getBreakdown()).contains("Profile:");
    }

    @Test
    @DisplayName("calculateScore: completed loans bonus applied")
    void calculateScore_completedLoanBonus() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(2L);
        when(loanSummaryRepository.countCompletedByBorrowerId(1L)).thenReturn(2L);
        when(emiScheduleRepository.countPaidEmis(eq(1L), eq(EmiStatus.PAID))).thenReturn(24);
        when(emiScheduleRepository.countTotalEmisByBorrower(1L)).thenReturn(24L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(0L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getScore()).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("calculateScore: maxScore is always 100")
    void calculateScore_maxScoreIs100() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(fullProfileUser));
        when(loanSummaryRepository.countByBorrowerId(1L)).thenReturn(0L);
        when(loanSummaryRepository.countActiveByBorrowerId(1L)).thenReturn(0L);

        CreditScoreResponse response = service.calculateScore(1L);

        assertThat(response.getMaxScore()).isEqualTo(100);
        assertThat(response.getBorrowerId()).isEqualTo(1L);
    }
}