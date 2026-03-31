package com.darshan.lending.service;

import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock LoanRequestRepository      loanRequestRepository;
    @Mock LenderPreferenceRepository lenderPreferenceRepository;
    @Mock LoanOfferRepository        loanOfferRepository;
    @Mock LoanOfferService           loanOfferService;

    @InjectMocks MatchmakingService service;

    private User        borrower;
    private User        lender1;
    private User        lender2;
    private LoanProduct loanProduct;
    private LoanRequest pendingRequest;
    private LenderPreference pref1;
    private LenderPreference pref2;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender1  = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();
        lender2  = User.builder().id(3L).role(Role.LENDER).fullName("Carol").build();

        loanProduct = LoanProduct.builder()
                .id(5L).name("Personal Loan")
                .minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("100000"))
                .minTenure(6).maxTenure(24)
                .status(ProductStatus.ACTIVE)
                .build();

        pendingRequest = LoanRequest.builder()
                .id(10L).borrower(borrower).loanProduct(loanProduct)
                .amount(new BigDecimal("50000")).tenureMonths(12)
                .status(LoanRequestStatus.PENDING)
                .purpose(LoanPurpose.EDUCATION)
                .build();

        pref1 = LenderPreference.builder()
                .id(1L).lender(lender1).loanProduct(loanProduct)
                .minLoanAmount(new BigDecimal("10000"))
                .maxLoanAmount(new BigDecimal("100000"))
                .minTenureMonths(6).maxTenureMonths(24)
                .minInterestRate(new BigDecimal("10"))
                .maxInterestRate(new BigDecimal("14"))
                .isActive(true)
                .build();

        pref2 = LenderPreference.builder()
                .id(2L).lender(lender2).loanProduct(loanProduct)
                .minLoanAmount(new BigDecimal("10000"))
                .maxLoanAmount(new BigDecimal("100000"))
                .minTenureMonths(6).maxTenureMonths(24)
                .minInterestRate(new BigDecimal("10"))
                .maxInterestRate(new BigDecimal("12"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("matchLoanRequest: 2 matching lenders → 2 offers created, request MATCHED")
    void matchLoanRequest_happyPath_twoOffers() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(lenderPreferenceRepository.findMatchingLenders(5L, new BigDecimal("50000"), 12))
                .thenReturn(List.of(pref1, pref2));
        when(loanOfferRepository.existsByLoanRequestIdAndLenderId(10L, 2L)).thenReturn(false);
        when(loanOfferRepository.existsByLoanRequestIdAndLenderId(10L, 3L)).thenReturn(false);
        when(loanOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanRequestRepository.save(any())).thenReturn(pendingRequest);
        when(loanOfferService.toResponse(any())).thenReturn(LoanOfferResponse.builder().build());

        List<LoanOfferResponse> result = service.matchLoanRequest(10L, 5);

        assertThat(result).hasSize(2);
        assertThat(pendingRequest.getStatus()).isEqualTo(LoanRequestStatus.MATCHED);
        verify(loanOfferRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("matchLoanRequest: no matching lenders → empty list, request still MATCHED")
    void matchLoanRequest_noMatchingLenders_emptyList() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(lenderPreferenceRepository.findMatchingLenders(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(loanRequestRepository.save(any())).thenReturn(pendingRequest);

        List<LoanOfferResponse> result = service.matchLoanRequest(10L, 5);

        assertThat(result).isEmpty();
        assertThat(pendingRequest.getStatus()).isEqualTo(LoanRequestStatus.MATCHED);
    }

    @Test
    @DisplayName("matchLoanRequest: request not found → ResourceNotFoundException")
    void matchLoanRequest_requestNotFound_throws() {
        when(loanRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.matchLoanRequest(99L, 5))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("matchLoanRequest: request not PENDING → BusinessException")
    void matchLoanRequest_notPending_throws() {
        pendingRequest.setStatus(LoanRequestStatus.MATCHED);
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.matchLoanRequest(10L, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("matchLoanRequest: offer already exists for lender → skipped")
    void matchLoanRequest_offerAlreadyExists_skipped() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(lenderPreferenceRepository.findMatchingLenders(5L, new BigDecimal("50000"), 12))
                .thenReturn(List.of(pref1));
        when(loanOfferRepository.existsByLoanRequestIdAndLenderId(10L, 2L)).thenReturn(true);
        when(loanRequestRepository.save(any())).thenReturn(pendingRequest);

        List<LoanOfferResponse> result = service.matchLoanRequest(10L, 5);

        assertThat(result).isEmpty();
        verify(loanOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("matchLoanRequest: maxOffers=1 → only 1 offer created (lowest interest lender)")
    void matchLoanRequest_maxOffers_limitsResults() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(lenderPreferenceRepository.findMatchingLenders(5L, new BigDecimal("50000"), 12))
                .thenReturn(List.of(pref1, pref2));
        when(loanOfferRepository.existsByLoanRequestIdAndLenderId(anyLong(), anyLong()))
                .thenReturn(false);
        when(loanOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanRequestRepository.save(any())).thenReturn(pendingRequest);
        when(loanOfferService.toResponse(any())).thenReturn(LoanOfferResponse.builder().build());

        List<LoanOfferResponse> result = service.matchLoanRequest(10L, 1);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("matchLoanRequest: same lender with two preferences → deduplicated")
    void matchLoanRequest_duplicateLender_deduplicated() {
        LenderPreference pref1b = LenderPreference.builder()
                .id(3L).lender(lender1).loanProduct(loanProduct)
                .minLoanAmount(new BigDecimal("10000"))
                .maxLoanAmount(new BigDecimal("100000"))
                .minTenureMonths(6).maxTenureMonths(24)
                .minInterestRate(new BigDecimal("10"))
                .maxInterestRate(new BigDecimal("16"))
                .isActive(true)
                .build();

        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(lenderPreferenceRepository.findMatchingLenders(5L, new BigDecimal("50000"), 12))
                .thenReturn(List.of(pref1, pref1b));
        when(loanOfferRepository.existsByLoanRequestIdAndLenderId(10L, 2L)).thenReturn(false);
        when(loanOfferRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanRequestRepository.save(any())).thenReturn(pendingRequest);
        when(loanOfferService.toResponse(any())).thenReturn(LoanOfferResponse.builder().build());

        List<LoanOfferResponse> result = service.matchLoanRequest(10L, 5);

        assertThat(result).hasSize(1);
        verify(loanOfferRepository, times(1)).save(any());
    }
}