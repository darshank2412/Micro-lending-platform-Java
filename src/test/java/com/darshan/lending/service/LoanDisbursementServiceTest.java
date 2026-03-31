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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import java.util.List;
import java.time.LocalDate;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanDisbursementServiceTest {

    @Mock LoanOfferRepository    loanOfferRepository;
    @Mock LoanRequestRepository  loanRequestRepository;
    @Mock LoanSummaryRepository  loanSummaryRepository;
    @Mock EmiScheduleRepository  emiScheduleRepository;
    @Mock BankAccountRepository  bankAccountRepository;
    @Mock UserRepository         userRepository;
    @Mock EmiCalculator          emiCalculator;
    @Mock LenderPreferenceRepository lenderPreferenceRepository;

    @InjectMocks LoanDisbursementService service;

    private User        borrower;
    private User        lender;
    private LoanProduct loanProduct;
    private LoanRequest loanRequest;
    private LoanOffer   acceptedOffer;
    private BankAccount lenderAccount;
    private BankAccount borrowerAccount;

    @BeforeEach
    void setUp() {
        borrower = User.builder().id(1L).role(Role.BORROWER).fullName("Alice").build();
        lender   = User.builder().id(2L).role(Role.LENDER).fullName("Bob").build();

        loanProduct = LoanProduct.builder()
                .id(5L)
                .name("Personal Loan")
                .build();

        loanRequest = LoanRequest.builder()
                .id(10L)
                .borrower(borrower)
                .loanProduct(loanProduct)
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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        LoanSummaryResponse result = service.disburseLoan(100L);

        assertThat(result.getPrincipalAmount()).isEqualByComparingTo("50000");
        assertThat(result.getInterestRate()).isEqualByComparingTo("12.00");
        assertThat(lenderAccount.getBalance()).isEqualByComparingTo("150000");
        assertThat(borrowerAccount.getBalance()).isEqualByComparingTo("55000");
        assertThat(loanRequest.getStatus()).isEqualTo(LoanRequestStatus.DISBURSED);
    }

    @Test
    @DisplayName("disburseLoan: offer not ACCEPTED → BusinessException")
    void disburseLoan_offerNotAccepted_throws() {
        acceptedOffer.setStatus(LoanOfferStatus.PENDING);
        when(loanOfferRepository.findById(100L)).thenReturn(Optional.of(acceptedOffer));

        assertThatThrownBy(() -> service.disburseLoan(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only ACCEPTED offers can be disbursed");
    }

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

    @Test
    @DisplayName("disburseLoan: lender insufficient balance → BusinessException")
    void disburseLoan_insufficientBalance_throws() {
        lenderAccount.setBalance(new BigDecimal("1000"));
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

    @Test
    @DisplayName("disburseLoan: offer not found → ResourceNotFoundException")
    void disburseLoan_offerNotFound_throws() {
        when(loanOfferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disburseLoan(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

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

    @Test
    @DisplayName("EmiCalculator: interest on 50000 at 12% annual = 500.00")
    void emiCalculator_interestComponent_correct() {
        EmiCalculator calc = new EmiCalculator();
        BigDecimal interest = calc.calculateInterestComponent(
                new BigDecimal("50000"),
                new BigDecimal("12"));
        assertThat(interest).isEqualByComparingTo("500.00");
    }



    // ── preferredEmiDay — borrower choice ─────────────────────────────────────

    @Test
    @DisplayName("disburseLoan: borrower sets preferredEmiDay=5, disbursed on 25th → first EMI on 5th next month")
    void disburseLoan_borrowerPreferredEmiDay_usedForFirstEmi() {
        loanRequest.setPreferredEmiDay(5);

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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        LoanSummaryResponse result = service.disburseLoan(100L);

        // First EMI day should be 5
        assertThat(result.getFirstEmiDate().getDayOfMonth()).isEqualTo(5);
    }

    @Test
    @DisplayName("disburseLoan: borrower sets preferredEmiDay=27, disbursed on 25th → first EMI on 27th next month")
    void disburseLoan_borrowerPreferredDay27_disbursed25th_firstEmiApril27() {
        loanRequest.setPreferredEmiDay(27);

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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        LoanSummaryResponse result = service.disburseLoan(100L);

        // Day should be 27
        assertThat(result.getFirstEmiDate().getDayOfMonth()).isEqualTo(27);
    }

    @Test
    @DisplayName("disburseLoan: borrower no preference, lender preferredPaymentDay=10 → first EMI on 10th")
    void disburseLoan_fallbackToLenderPreferredDay() {
        // borrower has no preferredEmiDay set (null)
        loanRequest.setPreferredEmiDay(null);

        LenderPreference lenderPref = LenderPreference.builder()
                .id(2L)
                .preferredPaymentDay(10)
                .build();

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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.of(lenderPref));

        LoanSummaryResponse result = service.disburseLoan(100L);

        assertThat(result.getFirstEmiDate().getDayOfMonth()).isEqualTo(10);
    }

    @Test
    @DisplayName("disburseLoan: neither borrower nor lender preference → default same day next month")
    void disburseLoan_noPreference_defaultsToSameDayNextMonth() {
        loanRequest.setPreferredEmiDay(null);

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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        LocalDate today = LocalDate.now();
        LoanSummaryResponse result = service.disburseLoan(100L);

        // Default: same day next month
        assertThat(result.getFirstEmiDate())
                .isEqualTo(today.plusMonths(1));
    }

    @Test
    @DisplayName("disburseLoan: borrower preferredEmiDay takes priority over lender preference")
    void disburseLoan_borrowerDayOverridesLenderDay() {
        loanRequest.setPreferredEmiDay(15);

        LenderPreference lenderPref = LenderPreference.builder()
                .id(2L)
                .preferredPaymentDay(10)
                .build();

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
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.of(lenderPref));

        LoanSummaryResponse result = service.disburseLoan(100L);

        // Borrower's 15 wins over lender's 10
        assertThat(result.getFirstEmiDate().getDayOfMonth()).isEqualTo(15);
    }

    @Test
    @DisplayName("disburseLoan: gap > 30 days → first EMI amount is higher than regular EMI")
    void disburseLoan_gapOver30Days_firstEmiHigher() {
        // preferredEmiDay=28, disbursed on 1st → gap = ~27 days (next month 28th)
        // preferredEmiDay=28, disbursed on 26th → gap = ~32 days → gap interest added
        loanRequest.setPreferredEmiDay(28);

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
        when(emiScheduleRepository.saveAll(any())).thenAnswer(i -> {
            // Capture and verify schedule
            List<EmiSchedule> saved = i.getArgument(0);
            // If gap > 30 days, first EMI amount must be >= standard EMI
            assertThat(saved.get(0).getEmiAmount())
                    .isGreaterThanOrEqualTo(new BigDecimal("4442.44"));
            return null;
        });
        when(loanRequestRepository.save(any())).thenReturn(loanRequest);
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        service.disburseLoan(100L);
    }

    @Test
    @DisplayName("disburseLoan: gap <= 30 days → first EMI same as regular EMI amount")
    void disburseLoan_gapUnder30Days_firstEmiSameAsRegular() {
        loanRequest.setPreferredEmiDay(null); // default = same day next month = exactly 30/31 days

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
        when(emiScheduleRepository.saveAll(any())).thenAnswer(i -> {
            List<EmiSchedule> saved = i.getArgument(0);
            // No gap interest — first EMI = standard EMI
            assertThat(saved.get(0).getEmiAmount())
                    .isEqualByComparingTo("4442.44");
            return null;
        });
        when(loanRequestRepository.save(any())).thenReturn(loanRequest);
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        service.disburseLoan(100L);
    }

    @Test
    @DisplayName("disburseLoan: EMI schedule has correct number of entries = tenure months")
    void disburseLoan_emiScheduleCount_equalsTenure() {
        loanRequest.setPreferredEmiDay(null);

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
        when(emiScheduleRepository.saveAll(any())).thenAnswer(i -> {
            List<EmiSchedule> saved = i.getArgument(0);
            assertThat(saved).hasSize(12); // tenure = 12
            return null;
        });
        when(loanRequestRepository.save(any())).thenReturn(loanRequest);
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        service.disburseLoan(100L);
    }

    @Test
    @DisplayName("disburseLoan: each subsequent EMI due date is one month after previous")
    void disburseLoan_emiDueDates_oneMonthApart() {
        loanRequest.setPreferredEmiDay(15);

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
        when(emiScheduleRepository.saveAll(any())).thenAnswer(i -> {
            List<EmiSchedule> saved = i.getArgument(0);
            for (int idx = 1; idx < saved.size(); idx++) {
                LocalDate prev = saved.get(idx - 1).getDueDate();
                LocalDate curr = saved.get(idx).getDueDate();
                assertThat(curr).isEqualTo(prev.plusMonths(1));
            }
            return null;
        });
        when(loanRequestRepository.save(any())).thenReturn(loanRequest);
        when(lenderPreferenceRepository.findByLenderIdAndLoanProductId(any(), any()))
                .thenReturn(Optional.empty());

        service.disburseLoan(100L);
    }
}