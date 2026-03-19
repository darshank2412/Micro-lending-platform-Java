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
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanRequestServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired LoanProductService loanProductService;
    @Autowired LoanRequestService loanRequestService;

    private User borrower;
    private User lender;
    private Long loanProductId;

    @BeforeEach
    void setUp() {
        borrower = userRepository.save(User.builder()
                .phoneNumber("9111111111").countryCode("+91")
                .email("borrower@test.com").password("encoded")
                .fullName("Test Borrower").role(Role.BORROWER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        lender = userRepository.save(User.builder()
                .phoneNumber("9222222222").countryCode("+91")
                .email("lender@test.com").password("encoded")
                .fullName("Test Lender").role(Role.LENDER)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED).build());

        loanProductId = loanProductService.create(LoanProductRequest.builder()
                .name("Test Loan Product").minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).minInterest(new BigDecimal("8"))
                .maxInterest(new BigDecimal("24")).minTenure(6).maxTenure(60)
                .build()).getId();
    }

    @Test
    void borrower_canSubmitLoanRequest() {
        LoanRequestResponse response = loanRequestService.createRequest(
                borrower.getId(), LoanRequestDto.builder()
                        .loanProductId(loanProductId).amount(new BigDecimal("50000"))
                        .tenureMonths(12).purpose(LoanPurpose.EDUCATION)
                        .purposeDescription("College fees").build());
        assertNotNull(response.getId());
        assertEquals(LoanRequestStatus.PENDING, response.getStatus());
        assertEquals(borrower.getId(), response.getBorrowerId());
    }

    @Test
    void lender_cannotSubmitLoanRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanRequestService.createRequest(
                        lender.getId(), LoanRequestDto.builder()
                                .loanProductId(loanProductId).amount(new BigDecimal("50000"))
                                .tenureMonths(12).purpose(LoanPurpose.EDUCATION).build()));
        assertTrue(ex.getMessage().contains("Only BORROWER"));
    }

    @Test
    void borrower_cannotHaveTwoPendingRequests() {
        loanRequestService.createRequest(borrower.getId(), LoanRequestDto.builder()
                .loanProductId(loanProductId).amount(new BigDecimal("50000"))
                .tenureMonths(12).purpose(LoanPurpose.EMERGENCY).build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanRequestService.createRequest(borrower.getId(), LoanRequestDto.builder()
                        .loanProductId(loanProductId).amount(new BigDecimal("20000"))
                        .tenureMonths(6).purpose(LoanPurpose.MEDICAL).build()));
        assertTrue(ex.getMessage().contains("already have a PENDING"));
    }

    @Test
    void borrower_canCancelPendingRequest() {
        LoanRequestResponse created = createLoanRequest(LoanPurpose.TRAVEL);
        LoanRequestResponse cancelled = loanRequestService.cancelRequest(borrower.getId(), created.getId());
        assertEquals(LoanRequestStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void lender_cannotAcceptPendingRequest() {
        LoanRequestResponse created = createLoanRequest(LoanPurpose.EDUCATION);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> loanRequestService.acceptRequest(lender.getId(), created.getId()));
        assertTrue(ex.getMessage().contains("Only MATCHED"));
    }

    @Test
    void stateMachine_pendingToMatchedToAccepted() {
        LoanRequestResponse created = createLoanRequest(LoanPurpose.SMALL_BUSINESS);
        assertEquals(LoanRequestStatus.PENDING, created.getStatus());

        LoanRequestResponse matched = loanRequestService.markAsMatched(created.getId());
        assertEquals(LoanRequestStatus.MATCHED, matched.getStatus());

        LoanRequestResponse accepted = loanRequestService.acceptRequest(lender.getId(), created.getId());
        assertEquals(LoanRequestStatus.ACCEPTED, accepted.getStatus());
    }

    private LoanRequestResponse createLoanRequest(LoanPurpose purpose) {
        return loanRequestService.createRequest(borrower.getId(), LoanRequestDto.builder()
                .loanProductId(loanProductId).amount(new BigDecimal("50000"))
                .tenureMonths(12).purpose(purpose).build());
    }
}
