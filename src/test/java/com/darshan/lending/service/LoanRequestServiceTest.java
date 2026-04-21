package com.darshan.lending.service;

import com.darshan.lending.dto.LoanRequestDto;
import com.darshan.lending.dto.LoanRequestResponse;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanRequestServiceTest {

    @Mock LoanRequestRepository      loanRequestRepository;
    @Mock UserRepository             userRepository;
    @Mock LoanProductRepository      loanProductRepository;
    @Mock LenderPreferenceRepository lenderPreferenceRepository;
    @Mock AuditLogService         auditLogService;

    @InjectMocks LoanRequestService service;

    private User        borrower;
    private User        lender;
    private User        admin;
    private LoanProduct activeProduct;
    private LoanRequest pendingRequest;
    private LoanRequestDto validDto;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender   = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();
        admin    = User.builder().id(3L).role(Role.ADMIN).fullName("Admin").build();

        activeProduct = LoanProduct.builder()
                .id(5L).name("Personal Loan")
                .status(ProductStatus.ACTIVE)
                .minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("100000"))
                .minTenure(6).maxTenure(24)
                .build();

        pendingRequest = LoanRequest.builder()
                .id(10L).borrower(borrower).loanProduct(activeProduct)
                .amount(new BigDecimal("50000")).tenureMonths(12)
                .status(LoanRequestStatus.PENDING)
                .purpose(LoanPurpose.EDUCATION)
                .build();

        validDto = LoanRequestDto.builder()
                .loanProductId(5L)
                .amount(new BigDecimal("50000"))
                .tenureMonths(12)
                .purpose(LoanPurpose.EDUCATION)
                .purposeDescription("For college fees")
                .build();
    }

    // ── createRequest ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createRequest: valid borrower → loan request PENDING")
    void createRequest_happyPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.of(activeProduct));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanRequestResponse response = service.createRequest(1L, validDto);

        assertThat(response.getStatus()).isEqualTo(LoanRequestStatus.PENDING);
        assertThat(response.getAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("createRequest: non-borrower → BusinessException")
    void createRequest_notBorrower_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));

        assertThatThrownBy(() -> service.createRequest(2L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only BORROWER");
    }

    @Test
    @DisplayName("createRequest: already has PENDING request → BusinessException")
    void createRequest_alreadyPending_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING loan request");
    }

    @Test
    @DisplayName("createRequest: loan product not found → ResourceNotFoundException")
    void createRequest_productNotFound_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createRequest: product INACTIVE → BusinessException")
    void createRequest_productInactive_throws() {
        activeProduct.setStatus(ProductStatus.INACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("createRequest: amount below minimum → BusinessException")
    void createRequest_amountBelowMin_throws() {
        validDto.setAmount(new BigDecimal("500"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount must be between");
    }

    @Test
    @DisplayName("createRequest: amount above maximum → BusinessException")
    void createRequest_amountAboveMax_throws() {
        validDto.setAmount(new BigDecimal("999999"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount must be between");
    }

    @Test
    @DisplayName("createRequest: tenure out of range → BusinessException")
    void createRequest_tenureOutOfRange_throws() {
        validDto.setTenureMonths(60);
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.existsByBorrowerIdAndStatus(1L, LoanRequestStatus.PENDING))
                .thenReturn(false);
        when(loanProductRepository.findById(5L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.createRequest(1L, validDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenure must be between");
    }

    // ── getMyRequests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyRequests: borrower → returns their requests")
    void getMyRequests_happyPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRequestRepository.findByBorrowerId(1L)).thenReturn(List.of(pendingRequest));

        List<LoanRequestResponse> result = service.getMyRequests(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getMyRequests: non-borrower → BusinessException")
    void getMyRequests_notBorrower_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));

        assertThatThrownBy(() -> service.getMyRequests(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only BORROWER");
    }

    // ── cancelRequest ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelRequest: own pending request → CANCELLED")
    void cancelRequest_happyPath() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanRequestResponse response = service.cancelRequest(1L, 10L);

        assertThat(response.getStatus()).isEqualTo(LoanRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelRequest: different borrower → BusinessException")
    void cancelRequest_wrongBorrower_throws() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.cancelRequest(99L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own");
    }

    // ── getAllRequests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllRequests: no filter → returns all requests")
    void getAllRequests_noFilter() {
        when(loanRequestRepository.findAll()).thenReturn(List.of(pendingRequest));

        List<LoanRequestResponse> result = service.getAllRequests(null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllRequests: with status filter → returns filtered requests")
    void getAllRequests_withStatusFilter() {
        when(loanRequestRepository.findByStatus(LoanRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest));

        List<LoanRequestResponse> result = service.getAllRequests(LoanRequestStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LoanRequestStatus.PENDING);
    }

    // ── rejectRequest ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("rejectRequest: pending request → REJECTED with reason")
    void rejectRequest_happyPath() {
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanRequestResponse response = service.rejectRequest(10L, "Not eligible");

        assertThat(response.getStatus()).isEqualTo(LoanRequestStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Not eligible");
    }

    @Test
    @DisplayName("rejectRequest: request not found → ResourceNotFoundException")
    void rejectRequest_notFound_throws() {
        when(loanRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectRequest(99L, "reason"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getOpenRequests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getOpenRequests: lender → returns PENDING requests")
    void getOpenRequests_happyPath() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));
        when(loanRequestRepository.findByStatus(LoanRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest));

        List<LoanRequestResponse> result = service.getOpenRequests(2L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getOpenRequests: non-lender → BusinessException")
    void getOpenRequests_notLender_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> service.getOpenRequests(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only LENDER");
    }

    // ── getRequestsMatchingLenderPreference ───────────────────────────────────

    @Test
    @DisplayName("getRequestsMatchingLenderPreference: active prefs → matching requests")
    void getRequestsMatchingLenderPreference_happyPath() {
        LenderPreference pref = LenderPreference.builder()
                .id(1L).lender(lender)
                .minLoanAmount(new BigDecimal("10000"))
                .maxLoanAmount(new BigDecimal("100000"))
                .minTenureMonths(6).maxTenureMonths(24)
                .isActive(true).build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));
        when(lenderPreferenceRepository.findByLenderIdAndIsActiveTrue(2L))
                .thenReturn(List.of(pref));
        when(loanRequestRepository.findPendingMatchingPreference(
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingRequest));

        List<LoanRequestResponse> result = service.getRequestsMatchingLenderPreference(2L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRequestsMatchingLenderPreference: no preferences → BusinessException")
    void getRequestsMatchingLenderPreference_noPrefs_throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));
        when(lenderPreferenceRepository.findByLenderIdAndIsActiveTrue(2L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getRequestsMatchingLenderPreference(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active preferences");
    }

    @Test
    @DisplayName("getRequestsMatchingLenderPreference: non-lender → BusinessException")
    void getRequestsMatchingLenderPreference_notLender_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> service.getRequestsMatchingLenderPreference(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only LENDER");
    }

    // ── acceptRequest ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptRequest: matched request → ACCEPTED")
    void acceptRequest_happyPath() {
        pendingRequest.setStatus(LoanRequestStatus.MATCHED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(lender));
        when(loanRequestRepository.findById(10L)).thenReturn(Optional.of(pendingRequest));
        when(loanRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LoanRequestResponse response = service.acceptRequest(2L, 10L);

        assertThat(response.getStatus()).isEqualTo(LoanRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("acceptRequest: non-lender → BusinessException")
    void acceptRequest_notLender_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> service.acceptRequest(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only LENDER");
    }
}