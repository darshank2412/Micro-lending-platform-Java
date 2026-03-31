package com.darshan.lending.service;

import com.darshan.lending.config.LoanRepaymentConfig;
import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.Foreclosureresponse;
import com.darshan.lending.dto.LoanSummaryResponse;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Add this to make stubbings lenient
class EmiPaymentServiceTest {

    @Mock LoanSummaryRepository   loanSummaryRepository;
    @Mock EmiScheduleRepository   emiScheduleRepository;
    @Mock BankAccountRepository   bankAccountRepository;
    @Mock LoanDisbursementService loanDisbursementService;
    @Mock LoanRepaymentConfig     repaymentConfig;

    @InjectMocks EmiPaymentService service;

    private User        borrower;
    private User        lender;
    private LoanSummary activeLoan;
    private BankAccount borrowerAccount;
    private BankAccount lenderAccount;
    private EmiSchedule emi1;
    private EmiSchedule emi2;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender   = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();

        activeLoan = LoanSummary.builder()
                .id(10L)
                .borrower(borrower)
                .lender(lender)
                .status(LoanStatus.ACTIVE)
                .principalAmount(new BigDecimal("50000"))
                .outstandingPrincipal(new BigDecimal("50000"))
                .interestRate(new BigDecimal("12"))
                .tenureMonths(12)
                .emiAmount(new BigDecimal("4442.44"))
                .emisPaid(0)
                .emisRemaining(12)
                .disbursementDate(LocalDate.now().minusMonths(1))
                .firstEmiDate(LocalDate.now().minusMonths(1).plusMonths(1))
                .lastEmiDate(LocalDate.now().plusMonths(11))
                .build();

        borrowerAccount = BankAccount.builder()
                .id(1L).user(borrower)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("20000"))
                .status(AccountStatus.ACTIVE)
                .build();

        lenderAccount = BankAccount.builder()
                .id(2L).user(lender)
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("100000"))
                .status(AccountStatus.ACTIVE)
                .build();

        emi1 = EmiSchedule.builder()
                .id(1L)
                .emiNumber(1)
                .emiAmount(new BigDecimal("4442.44"))
                .dueDate(LocalDate.now())
                .status(EmiStatus.PENDING)
                .outstandingPrincipal(new BigDecimal("46000"))
                .principalComponent(new BigDecimal("3942.44"))
                .interestComponent(new BigDecimal("500.00"))
                .build();

        emi2 = EmiSchedule.builder()
                .id(2L)
                .emiNumber(2)
                .emiAmount(new BigDecimal("4442.44"))
                .dueDate(LocalDate.now().plusMonths(1))
                .status(EmiStatus.PENDING)
                .outstandingPrincipal(new BigDecimal("42000"))
                .principalComponent(new BigDecimal("3942.44"))
                .interestComponent(new BigDecimal("500.00"))
                .build();
    }

    // ── payEmi ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("payEmi: full payment on time → EMI marked PAID")
    void payEmi_fullPayment_success() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(emi1, emi2));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        EmiScheduleResponse mockResponse = EmiScheduleResponse.builder()
                .emiNumber(1).emiAmount(new BigDecimal("4442.44")).build();
        when(loanDisbursementService.toEmiResponse(any())).thenReturn(mockResponse);

        EmiScheduleResponse result = service.payEmi(10L, 1L, new BigDecimal("4442.44"));

        assertThat(result).isNotNull();
        assertThat(emi1.getStatus()).isEqualTo(EmiStatus.PAID);
        assertThat(borrowerAccount.getBalance()).isLessThan(new BigDecimal("20000"));
    }

    @Test
    @DisplayName("payEmi: partial payment → EMI marked PARTIAL, shortfall added to next")
    void payEmi_partialPayment_shortfallAddedToNext() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(emi1, emi2));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        EmiScheduleResponse mockResponse = EmiScheduleResponse.builder()
                .emiNumber(1).emiAmount(new BigDecimal("4442.44")).build();
        when(loanDisbursementService.toEmiResponse(any())).thenReturn(mockResponse);

        EmiScheduleResponse result = service.payEmi(10L, 1L, new BigDecimal("2000"));

        assertThat(emi1.getStatus()).isEqualTo(EmiStatus.PARTIAL);
        assertThat(result.getMessage()).contains("Partial payment accepted");
    }

    @Test
    @DisplayName("payEmi: excess payment → next EMI amount reduced")
    void payEmi_excessPayment_nextEmiReduced() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(emi1, emi2));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        EmiScheduleResponse mockResponse = EmiScheduleResponse.builder()
                .emiNumber(1).emiAmount(new BigDecimal("4442.44")).build();
        when(loanDisbursementService.toEmiResponse(any())).thenReturn(mockResponse);

        BigDecimal originalEmi2Amount = emi2.getEmiAmount();
        service.payEmi(10L, 1L, new BigDecimal("6000"));

        assertThat(emi2.getEmiAmount()).isLessThan(originalEmi2Amount);
    }

    @Test
    @DisplayName("payEmi: last EMI paid → loan marked COMPLETED")
    void payEmi_lastEmi_loanCompleted() {
        activeLoan.setEmisRemaining(1);
        activeLoan.setEmisPaid(11);

        EmiSchedule lastEmi = EmiSchedule.builder()
                .id(1L).emiNumber(1)
                .emiAmount(new BigDecimal("4442.44"))
                .dueDate(LocalDate.now())
                .status(EmiStatus.PENDING)
                .outstandingPrincipal(BigDecimal.ZERO)
                .principalComponent(new BigDecimal("4442.44"))
                .interestComponent(BigDecimal.ZERO)
                .build();

        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(lastEmi));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(lastEmi));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        EmiScheduleResponse mockResponse = EmiScheduleResponse.builder().build();
        when(loanDisbursementService.toEmiResponse(any())).thenReturn(mockResponse);

        service.payEmi(10L, 1L, new BigDecimal("4442.44"));

        assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.COMPLETED);
    }

    @Test
    @DisplayName("payEmi: zero payment amount → BusinessException")
    void payEmi_zeroAmount_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.payEmi(10L, 1L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("payEmi: loan not found → ResourceNotFoundException")
    void payEmi_loanNotFound_throws() {
        when(loanSummaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payEmi(99L, 1L, new BigDecimal("4442.44")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("payEmi: loan not ACTIVE → BusinessException")
    void payEmi_loanNotActive_throws() {
        activeLoan.setStatus(LoanStatus.COMPLETED);
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.payEmi(10L, 1L, new BigDecimal("4442.44")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("payEmi: wrong borrower → BusinessException")
    void payEmi_wrongBorrower_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.payEmi(10L, 99L, new BigDecimal("4442.44")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not the owner");
    }

    @Test
    @DisplayName("payEmi: no pending EMIs → BusinessException")
    void payEmi_noPendingEmis_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.payEmi(10L, 1L, new BigDecimal("4442.44")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No pending EMIs");
    }

    @Test
    @DisplayName("payEmi: insufficient balance → BusinessException")
    void payEmi_insufficientBalance_throws() {
        borrowerAccount.setBalance(new BigDecimal("100"));
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(emi1));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));

        assertThatThrownBy(() -> service.payEmi(10L, 1L, new BigDecimal("4442.44")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("payEmi: late payment → penalty calculated")
    void payEmi_latePayment_penaltyApplied() {
        emi1.setDueDate(LocalDate.now().minusDays(10));

        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L)).thenReturn(List.of(emi1, emi2));
        when(repaymentConfig.getGracePeriodDays()).thenReturn(3);
        when(repaymentConfig.getPenalInterestRate()).thenReturn(new BigDecimal("0.02"));
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        EmiScheduleResponse mockResponse = EmiScheduleResponse.builder().build();
        when(loanDisbursementService.toEmiResponse(any())).thenReturn(mockResponse);

        service.payEmi(10L, 1L, new BigDecimal("20000"));

        verify(repaymentConfig, atLeastOnce()).getPenalInterestRate();
    }

    // ── foreclose ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("foreclose: happy path → loan COMPLETED, total payable returned")
    void foreclose_happyPath() {
        borrowerAccount.setBalance(new BigDecimal("100000"));
        activeLoan.setDisbursementDate(LocalDate.now().minusMonths(2));

        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(repaymentConfig.getForeclosurePenaltyRate()).thenReturn(new BigDecimal("0.02"));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.findPendingOrOverdueEmis(10L))
                .thenReturn(List.of(emi1, emi2));
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        Foreclosureresponse result = service.foreclose(10L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("foreclosed successfully");
        assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.COMPLETED);
    }

    @Test
    @DisplayName("foreclose: insufficient balance → BusinessException")
    void foreclose_insufficientBalance_throws() {
        borrowerAccount.setBalance(new BigDecimal("100"));
        activeLoan.setDisbursementDate(LocalDate.now().minusMonths(1));

        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(repaymentConfig.getForeclosurePenaltyRate()).thenReturn(new BigDecimal("0.02"));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(emiScheduleRepository.findByLoanSummaryIdOrderByEmiNumberAsc(10L))
                .thenReturn(List.of(emi1, emi2));

        assertThatThrownBy(() -> service.foreclose(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient balance for foreclosure");
    }

    @Test
    @DisplayName("foreclose: wrong borrower → BusinessException")
    void foreclose_wrongBorrower_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.foreclose(10L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not the owner");
    }

    @Test
    @DisplayName("foreclose: loan not ACTIVE → BusinessException")
    void foreclose_loanNotActive_throws() {
        activeLoan.setStatus(LoanStatus.COMPLETED);
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.foreclose(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    // ── partialRepayment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("partialRepayment: happy path → outstanding reduced")
    void partialRepayment_happyPath() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        LoanSummaryResponse mockResp = LoanSummaryResponse.builder().build();
        when(loanDisbursementService.toSummaryResponse(any())).thenReturn(mockResp);

        LoanSummaryResponse result = service.partialRepayment(10L, 1L, new BigDecimal("5000"));

        assertThat(result).isNotNull();
        assertThat(activeLoan.getOutstandingPrincipal())
                .isEqualByComparingTo("45000.00");
    }

    @Test
    @DisplayName("partialRepayment: full outstanding paid → loan COMPLETED")
    void partialRepayment_fullOutstanding_loanCompleted() {
        activeLoan.setOutstandingPrincipal(new BigDecimal("5000"));
        borrowerAccount.setBalance(new BigDecimal("10000"));

        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));
        when(loanSummaryRepository.save(any())).thenReturn(activeLoan);

        LoanSummaryResponse mockResp = LoanSummaryResponse.builder().build();
        when(loanDisbursementService.toSummaryResponse(any())).thenReturn(mockResp);

        service.partialRepayment(10L, 1L, new BigDecimal("5000"));

        assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.COMPLETED);
    }

    @Test
    @DisplayName("partialRepayment: loan not found → ResourceNotFoundException")
    void partialRepayment_loanNotFound_throws() {
        when(loanSummaryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.partialRepayment(99L, 1L, new BigDecimal("1000")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("partialRepayment: wrong borrower → BusinessException")
    void partialRepayment_wrongBorrower_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.partialRepayment(10L, 99L, new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own loans");
    }

    @Test
    @DisplayName("partialRepayment: loan not ACTIVE → BusinessException")
    void partialRepayment_notActive_throws() {
        activeLoan.setStatus(LoanStatus.COMPLETED);
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.partialRepayment(10L, 1L, new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not ACTIVE");
    }

    @Test
    @DisplayName("partialRepayment: zero amount → BusinessException")
    void partialRepayment_zeroAmount_throws() {
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.partialRepayment(10L, 1L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("partialRepayment: amount exceeds outstanding → BusinessException")
    void partialRepayment_exceedsOutstanding_throws() {
        activeLoan.setOutstandingPrincipal(new BigDecimal("1000"));
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));

        assertThatThrownBy(() -> service.partialRepayment(10L, 1L, new BigDecimal("5000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds outstanding");
    }

    @Test
    @DisplayName("partialRepayment: insufficient balance → BusinessException")
    void partialRepayment_insufficientBalance_throws() {
        borrowerAccount.setBalance(new BigDecimal("100"));
        when(loanSummaryRepository.findById(10L)).thenReturn(Optional.of(activeLoan));
        when(bankAccountRepository.findByUserIdAndAccountType(1L, AccountType.SAVINGS))
                .thenReturn(Optional.of(borrowerAccount));
        when(bankAccountRepository.findByUserIdAndAccountType(2L, AccountType.SAVINGS))
                .thenReturn(Optional.of(lenderAccount));

        assertThatThrownBy(() -> service.partialRepayment(10L, 1L, new BigDecimal("5000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient balance");
    }
}