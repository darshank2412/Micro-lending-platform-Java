package com.darshan.lending.service;

import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.entity.LoanOffer;
import com.darshan.lending.entity.LoanRequest;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.LoanOfferStatus;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LoanOfferRepository;
import com.darshan.lending.repository.LoanRequestRepository;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanOfferServiceTest {

    @Mock LoanOfferRepository   loanOfferRepository;
    @Mock LoanRequestRepository loanRequestRepository;
    @Mock UserRepository        userRepository;

    @InjectMocks LoanOfferService service;

    private User        borrower;
    private User        lender;
    private LoanRequest matchedRequest;
    private LoanOffer   pendingOffer;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender   = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();

        matchedRequest = LoanRequest.builder()
                .id(10L)
                .borrower(borrower)
                .status(LoanRequestStatus.MATCHED)
                .amount(new BigDecimal("50000"))
                .tenureMonths(12)
                .build();

        pendingOffer = LoanOffer.builder()
                .id(100L)
                .loanRequest(matchedRequest)
                .lender(lender)
                .offeredInterestRate(new BigDecimal("8.50"))
                .loanAmount(new BigDecimal("50000"))
                .status(LoanOfferStatus.PENDING)
                .matchRank(1)
                .build();
    }

    @Test
    @DisplayName("acceptOffer: PENDING → ACCEPTED, request → FUNDED, others auto-rejected")
    void acceptOffer_happyPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));
        when(loanRequestRepository.save(any())).thenReturn(matchedRequest);
        when(loanOfferRepository.save(any())).thenReturn(pendingOffer);
        when(loanOfferRepository.bulkRejectOtherOffers(10L, 100L)).thenReturn(2);

        LoanOfferResponse result = service.acceptOffer(1L, 100L);

        assertThat(result.getStatus()).isEqualTo(LoanOfferStatus.ACCEPTED);
        assertThat(matchedRequest.getStatus()).isEqualTo(LoanRequestStatus.DISBURSED);
        verify(loanOfferRepository).bulkRejectOtherOffers(10L, 100L);
    }

    @Test
    @DisplayName("acceptOffer: already ACCEPTED → throws BusinessException")
    void acceptOffer_alreadyAccepted_throws() {
        pendingOffer.setStatus(LoanOfferStatus.ACCEPTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));

        assertThatThrownBy(() -> service.acceptOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PENDING offers can be accepted");
    }

    @Test
    @DisplayName("acceptOffer: wrong borrower → BusinessException")
    void acceptOffer_wrongBorrower_throws() {
        User otherBorrower = User.builder().id(99L).role(Role.BORROWER).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherBorrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));

        assertThatThrownBy(() -> service.acceptOffer(99L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only accept offers on your own");
    }

    @Test
    @DisplayName("rejectOffer: PENDING → REJECTED with reason stored")
    void rejectOffer_happyPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));
        when(loanOfferRepository.save(any())).thenReturn(pendingOffer);

        LoanOfferResponse result = service.rejectOffer(1L, 100L, "Rate too high");

        assertThat(result.getStatus()).isEqualTo(LoanOfferStatus.REJECTED);
        assertThat(pendingOffer.getRejectionReason()).isEqualTo("Rate too high");
        verify(loanOfferRepository, never()).bulkRejectOtherOffers(anyLong(), anyLong());
    }

    @Test
    @DisplayName("rejectOffer: already REJECTED → throws BusinessException")
    void rejectOffer_alreadyRejected_throws() {
        pendingOffer.setStatus(LoanOfferStatus.REJECTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));

        assertThatThrownBy(() -> service.rejectOffer(1L, 100L, "again"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PENDING offers can be rejected");
    }

    @Test
    @DisplayName("acceptOffer: LENDER user → throws BusinessException")
    void acceptOffer_lenderCannotAccept_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));

        assertThatThrownBy(() -> service.acceptOffer(2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only BORROWER users can accept");
    }

    @Test
    @DisplayName("acceptOffer: offer not found → ResourceNotFoundException")
    void acceptOffer_offerNotFound_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptOffer(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("acceptOffer: request not MATCHED → BusinessException")
    void acceptOffer_requestNotMatched_throws() {
        matchedRequest.setStatus(LoanRequestStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdWithLock(100L)).thenReturn(Optional.of(pendingOffer));

        assertThatThrownBy(() -> service.acceptOffer(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not in MATCHED state");
    }
}