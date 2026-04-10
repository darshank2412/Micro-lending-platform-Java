package com.darshan.lending.integration;

import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.repository.*;
import com.darshan.lending.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class LoanFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired UserRepository           userRepository;
    @Autowired LoanProductRepository    loanProductRepository;
    @Autowired LoanRequestRepository    loanRequestRepository;
    @Autowired LoanRequestService       loanRequestService;
    @Autowired BankAccountRepository    bankAccountRepository;

    private User borrower;
    private User lender;
    private LoanProduct product;

    @BeforeEach
    void setUp() {
        borrower = userRepository.save(User.builder()
                .fullName("Alice Borrower")
                .phoneNumber("9111111111")
                .countryCode("+91")
                .password("pass")
                .role(Role.BORROWER)
                .kycStatus(KycStatus.VERIFIED)

                .emailVerified(false)
                .phoneVerified(true)
                .build());

        lender = userRepository.save(User.builder()
                .fullName("Bob Lender")
                .phoneNumber("9222222222")
                .countryCode("+91")
                .password("pass")
                .role(Role.LENDER)
                .kycStatus(KycStatus.VERIFIED)
                .emailVerified(false)
                .phoneVerified(true)
                .build());

        product = loanProductRepository.save(LoanProduct.builder()
                .name("Personal Loan IT")
                .minAmount(new BigDecimal("1000"))
                .maxAmount(new BigDecimal("500000"))
                .minTenure(6)
                .maxTenure(60)
                .minInterest(new BigDecimal("8"))
                .maxInterest(new BigDecimal("24"))
                .status(ProductStatus.ACTIVE)
                .build());

        bankAccountRepository.save(BankAccount.builder()
                .user(borrower)
                .accountNumber("SAV-IT-001")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("10000"))
                .status(AccountStatus.ACTIVE)
                .build());

        bankAccountRepository.save(BankAccount.builder()
                .user(lender)
                .accountNumber("SAV-IT-002")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("500000"))
                .status(AccountStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Borrower can create a loan request with preferred EMI day")
    void borrowerCreatesLoanRequest_withPreferredEmiDay() {
        LoanRequest request = loanRequestRepository.save(LoanRequest.builder()
                .borrower(borrower)
                .loanProduct(product)
                .amount(new BigDecimal("50000"))
                .tenureMonths(12)
                .purpose(LoanPurpose.EDUCATION)
                .preferredEmiDay(15)
                .status(LoanRequestStatus.PENDING)
                .build());

        assertThat(request.getId()).isNotNull();
        assertThat(request.getPreferredEmiDay()).isEqualTo(15);
        assertThat(request.getStatus()).isEqualTo(LoanRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Loan request without preferred EMI day saves null correctly")
    void borrowerCreatesLoanRequest_noPreferredEmiDay() {
        LoanRequest request = loanRequestRepository.save(LoanRequest.builder()
                .borrower(borrower)
                .loanProduct(product)
                .amount(new BigDecimal("30000"))
                .tenureMonths(6)
                .purpose(LoanPurpose.MEDICAL)
                .preferredEmiDay(null)
                .status(LoanRequestStatus.PENDING)
                .build());

        assertThat(request.getPreferredEmiDay()).isNull();
    }

    @Test
    @DisplayName("Preferred EMI day persists and retrieves correctly from DB")
    void preferredEmiDay_persistsCorrectly() {
        loanRequestRepository.save(LoanRequest.builder()
                .borrower(borrower)
                .loanProduct(product)
                .amount(new BigDecimal("20000"))
                .tenureMonths(12)
                .purpose(LoanPurpose.EMERGENCY)
                .preferredEmiDay(10)
                .status(LoanRequestStatus.PENDING)
                .build());

        var requests = loanRequestRepository.findByBorrowerId(borrower.getId());
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getPreferredEmiDay()).isEqualTo(10);
    }

    @Test
    @DisplayName("Lender account balance is sufficient for loan disbursement")
    void lenderAccount_hasSufficientBalance() {
        BankAccount lenderAccount = bankAccountRepository
                .findByUserIdAndAccountType(lender.getId(), AccountType.SAVINGS)
                .orElseThrow();

        assertThat(lenderAccount.getBalance())
                .isGreaterThanOrEqualTo(new BigDecimal("50000"));
    }
}