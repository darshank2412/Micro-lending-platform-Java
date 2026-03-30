package com.darshan.lending.service;

import com.darshan.lending.dto.LoanSummaryResponse;
import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import com.darshan.lending.util.EmiCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanDisbursementServiceTest {

    @Mock LoanOfferRepository    loanOfferRepository;
    @Mock LoanRequestRepository  loanRequestRepository;
    @Mock LoanSummaryRepository  loanSummaryRepository;
    @Mock EmiScheduleRepository  emiScheduleRepository;
    @Mock BankAccountRepository  bankAccountRepository;
    @Mock UserRepository         userRepository;
    @Mock EmiCalculator          emiCalculator;

    @InjectMocks LoanDisbursementService service;

    private User        borrower;
    private User        lender;
    private LoanRequest loanRequest;
    private LoanOffer   acceptedOffer;
    private BankAccount lenderAccount;
    private BankAccount borrowerAccount;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender   = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();

        loanRequest = LoanRequest.builder()
                .id(10L).borrower(borrower)
                .status(LoanRequestStatus.DISBURSED)
                .amount(new BigDecimal("50000"))
                .tenureMonths(12)
                .build();

        acceptedOffer = LoanOffer.builder()
                .id(100L).loanRequest(loanRequest).lender(lender)
                .offeredInterestRate(new BigDecimal("12.00"))
                .loanAmount(new BigDecimal("50000"))
                .status(LoanOfferStatus.ACCEPTED)
                .matchRank(1)
                .build();

        lenderAccount = BankAccount.builder()
                .id(1L).user(lender)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("200000"))
                .status(AccountStatus.ACTIVE)
                .build();

        borrowerAccount = BankAccount.builder()
                .id(2L).user(borrower)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("5000"))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    // TEST 1: Disburse happy path
    @Test
    @DisplayName("disburseLoan: ACCEPTED offer → loan disbursed, EMI schedule generated")
    void disburseLoan_happyPath() {
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));
        when(loanSummaryRepository.findByLoanOfferId(100L)).thenReturn(Optional.empty());
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(emiCalculator.calculateEmi(any(), any(), anyInt()))
                .thenReturn(new BigDecimal("4442.44"));
        when(emiCalculator.calculateInterestComponent(any(), any()))
                .thenReturn(new BigDecimal("500.00"));
        when(emiCalculator.calculatePrincipalComponent(any(), any()))
                .thenReturn(new BigDecimal("3942.44"));
        when(loanSummaryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emiScheduleRepository.saveAll(any())).thenReturn(null);
        when(loanRequestRepository.save(any())).thenReturn(loanRequest);

        LoanSummaryResponse result = service.disburseLoan(100L);

        assertThat(result.getPrincipalAmount()).isEqualByComparingTo("50000");
        assertThat(result.getInterestRate()).isEqualByComparingTo("12.00");
        assertThat(lenderAccount.getBalance()).isEqualByComparingTo("150000");
        assertThat(borrowerAccount.getBalance()).isEqualByComparingTo("55000");
        assertThat(loanRequest.getStatus()).isEqualTo(LoanRequestStatus.DISBURSED);
    }

    // TEST 2: Offer not ACCEPTED
    @Test
    @DisplayName("disburseLoan: offer not ACCEPTED → BusinessException")
    void disburseLoan_offerNotAccepted_throws() {
        acceptedOffer.setStatus(LoanOfferStatus.PENDING);
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));

        assertThatThrownBy(() -> service.disburseLoan(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only ACCEPTED offers can be disbursed");
    }

    // TEST 3: Already disbursed
    @Test
    @DisplayName("disburseLoan: already disbursed → BusinessException")
    void disburseLoan_alreadyDisbursed_throws() {
        LoanSummary existing = LoanSummary.builder().id(1L).build();
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));
        when(loanSummaryRepository.findByLoanOfferId(100L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.disburseLoan(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Loan already disbursed");
    }

    // TEST 4: Lender insufficient balance
    @Test
    @DisplayName("disburseLoan: lender insufficient balance → BusinessException")
    void disburseLoan_insufficientBalance_throws() {
        lenderAccount.setBalance(new BigDecimal("1000")); // less than 50000
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));
        when(loanSummaryRepository.findByLoanOfferId(100L)).thenReturn(Optional.empty());
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));

        assertThatThrownBy(() -> service.disburseLoan(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("insufficient balance");
    }

    // TEST 5: Offer not found
    @Test
    @DisplayName("disburseLoan: offer not found → ResourceNotFoundException")
    void disburseLoan_offerNotFound_throws() {
        when(loanOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disburseLoan(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // TEST 6: Lender has no bank account
    @Test
    @DisplayName("disburseLoan: lender has no savings account → BusinessException")
    void disburseLoan_lenderNoAccount_throws() {
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));
        when(loanSummaryRepository.findByLoanOfferId(100L)).thenReturn(Optional.empty());
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disburseLoan(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lender does not have a savings account");
    }

    // TEST 7: EMI calculation correctness
    @Test
    @DisplayName("EmiCalculator: EMI for 50000 at 12% for 12 months = 4442.44")
    void emiCalculator_correctValue() {
        EmiCalculator calc = new EmiCalculator();
        BigDecimal emi = calc.calculateEmi(
                new BigDecimal("50000"),
                new BigDecimal("12"),
                12);
        assertThat(emi).isEqualByComparingTo("4442.44");
    }

    // TEST 8: EMI interest component correctness
    @Test
    @DisplayName("EmiCalculator: interest on 50000 at 12% annual = 500.00")
    void emiCalculator_interestComponent_correct() {
        EmiCalculator calc = new EmiCalculator();
        BigDecimal interest = calc.calculateInterestComponent(
                new BigDecimal("50000"),
                new BigDecimal("12"));
        assertThat(interest).isEqualByComparingTo("500.00");
    }
}