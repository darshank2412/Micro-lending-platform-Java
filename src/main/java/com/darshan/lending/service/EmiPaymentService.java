package com.darshan.lending.service;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.Foreclosureresponse;
import com.darshan.lending.entity.BankAccount;
import com.darshan.lending.entity.EmiSchedule;
import com.darshan.lending.entity.LoanSummary;
import com.darshan.lending.entity.enums.AccountType;
import com.darshan.lending.entity.enums.EmiStatus;
import com.darshan.lending.entity.enums.LoanStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.BankAccountRepository;
import com.darshan.lending.repository.EmiScheduleRepository;
import com.darshan.lending.repository.LoanSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiPaymentService {

    // ── Constants ─────────────────────────────────────────────────────────

    /** Grace period: EMIs paid on/before this day of month have no penalty */
    private static final int GRACE_PERIOD_DAY = 5;

    /** Penal interest rate applied per day after grace period (annual rate basis) */
    private static final BigDecimal PENAL_INTEREST_RATE = new BigDecimal("0.24"); // 24% p.a.

    /** Foreclosure penalty as a fraction of outstanding principal */
    private static final BigDecimal FORECLOSURE_PENALTY_RATE = new BigDecimal("0.02"); // 2%

    private final LoanSummaryRepository  loanSummaryRepository;
    private final EmiScheduleRepository  emiScheduleRepository;
    private final BankAccountRepository  bankAccountRepository;
    private final LoanDisbursementService loanDisbursementService;

    // ─────────────────────────────────────────────────────────────────────
    // PAY NEXT EMI
    // ─────────────────────────────────────────────────────────────────────

    /**
     * BORROWER — Pay the next pending/overdue EMI.
     *
     * Business rules enforced:
     * 1. Loan must be ACTIVE
     * 2. Borrower identity verified
     * 3. Current month's EMI must be cleared before paying advance
     * 4. Max 1 EMI can be paid in advance (i.e. next month's)
     * 5. Late penalty applied if today > 5th of due month
     *    Penalty = emiAmount × penalRate × (daysLate / 365)
     * 6. Debit borrower savings, credit lender savings
     * 7. Update LoanSummary counters and mark COMPLETED when all paid
     */
    @Transactional
    public EmiScheduleResponse payNextEmi(Long loanSummaryId, Long borrowerId) {

        LoanSummary loan = loadActiveLoan(loanSummaryId);
        verifyBorrower(loan, borrowerId);

        List<EmiSchedule> pendingEmis = emiScheduleRepository
                .findPendingOrOverdueEmis(loanSummaryId);

        if (pendingEmis.isEmpty()) {
            throw new BusinessException("No pending EMIs found for loan: " + loanSummaryId);
        }

        EmiSchedule nextEmi = pendingEmis.get(0);
        LocalDate today     = LocalDate.now();

        // ── Advance payment guard ──────────────────────────────────────────
        // Allow paying advance only if we are past or in the current EMI's due month
        // and the very next EMI is being paid (not skipping ahead 2+)
        validateAdvancePayment(loan, nextEmi, today);

        // ── Late penalty calculation ───────────────────────────────────────
        BigDecimal penalty = calculateLatePenalty(nextEmi, today);
        BigDecimal totalPayable = nextEmi.getEmiAmount().add(penalty)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Bank account operations ────────────────────────────────────────
        BankAccount borrowerAccount = getBorrowerAccount(borrowerId);
        BankAccount lenderAccount   = getLenderAccount(loan.getLender().getId());

        if (borrowerAccount.getBalance().compareTo(totalPayable) < 0) {
            throw new BusinessException(
                    "Insufficient balance. Required: " + totalPayable
                            + ", Available: " + borrowerAccount.getBalance());
        }

        borrowerAccount.setBalance(borrowerAccount.getBalance().subtract(totalPayable));
        lenderAccount.setBalance(lenderAccount.getBalance().add(totalPayable));
        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        // ── Mark EMI as paid ───────────────────────────────────────────────
        nextEmi.setStatus(EmiStatus.PAID);
        nextEmi.setPaidDate(today);
        emiScheduleRepository.save(nextEmi);

        // ── Update loan summary counters ───────────────────────────────────
        loan.setEmisPaid(loan.getEmisPaid() + 1);
        loan.setEmisRemaining(loan.getEmisRemaining() - 1);
        loan.setOutstandingPrincipal(nextEmi.getOutstandingPrincipal());

        // ── Check if loan is fully repaid ──────────────────────────────────
        if (loan.getEmisRemaining() == 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loan.setOutstandingPrincipal(BigDecimal.ZERO);
            log.info("Loan COMPLETED: loanSummaryId={} borrower={}",
                    loanSummaryId, borrowerId);
        }

        loanSummaryRepository.save(loan);

        log.info("EMI paid: loanSummaryId={} emiNumber={} amount={} penalty={} borrower={}",
                loanSummaryId, nextEmi.getEmiNumber(), nextEmi.getEmiAmount(),
                penalty, borrowerId);

        EmiScheduleResponse response = loanDisbursementService.toEmiResponse(nextEmi);
        response.setPenaltyAmount(penalty);
        response.setTotalPaid(totalPayable);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────
    // FORECLOSURE
    // ─────────────────────────────────────────────────────────────────────


    @Transactional
    public Foreclosureresponse foreclose(Long loanSummaryId, Long borrowerId) {

        LoanSummary loan = loadActiveLoan(loanSummaryId);
        verifyBorrower(loan, borrowerId);

        LocalDate today = LocalDate.now();
        BigDecimal outstanding = loan.getOutstandingPrincipal();

        // ── Accrued interest since last EMI / disbursement ─────────────────
        LocalDate interestFrom = resolveInterestFromDate(loan);
        long daysAccrued = ChronoUnit.DAYS.between(interestFrom, today);
        if (daysAccrued < 0) daysAccrued = 0;

        BigDecimal accruedInterest = outstanding
                .multiply(loan.getInterestRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(daysAccrued))
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        // ── 2% foreclosure penalty ─────────────────────────────────────────
        BigDecimal foreclosureCharge = outstanding
                .multiply(FORECLOSURE_PENALTY_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPayable = outstanding.add(accruedInterest).add(foreclosureCharge)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Bank account operations ────────────────────────────────────────
        BankAccount borrowerAccount = getBorrowerAccount(borrowerId);
        BankAccount lenderAccount   = getLenderAccount(loan.getLender().getId());

        if (borrowerAccount.getBalance().compareTo(totalPayable) < 0) {
            throw new BusinessException(
                    "Insufficient balance for foreclosure. Required: " + totalPayable
                            + ", Available: " + borrowerAccount.getBalance());
        }

        borrowerAccount.setBalance(borrowerAccount.getBalance().subtract(totalPayable));
        lenderAccount.setBalance(lenderAccount.getBalance().add(totalPayable));
        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        // ── Mark all remaining EMIs as PAID ────────────────────────────────
        List<EmiSchedule> remaining = emiScheduleRepository
                .findPendingOrOverdueEmis(loanSummaryId);

        remaining.forEach(e -> {
            e.setStatus(EmiStatus.PAID);
            e.setPaidDate(today);
        });
        emiScheduleRepository.saveAll(remaining);

        // ── Close the loan ─────────────────────────────────────────────────
        loan.setStatus(LoanStatus.COMPLETED);
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setEmisPaid(loan.getTenureMonths());
        loan.setEmisRemaining(0);
        loanSummaryRepository.save(loan);

        log.info("Loan FORECLOSED: loanSummaryId={} borrower={} outstanding={} " +
                        "accruedInterest={} penalty={} totalPaid={}",
                loanSummaryId, borrowerId, outstanding,
                accruedInterest, foreclosureCharge, totalPayable);

        return Foreclosureresponse.builder()
                .loanSummaryId(loanSummaryId)
                .outstandingPrincipal(outstanding)
                .accruedInterest(accruedInterest)
                .foreclosureCharge(foreclosureCharge)
                .totalPaid(totalPayable)
                .foreclosureDate(today)
                .message("Loan foreclosed successfully. All dues cleared.")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private LoanSummary loadActiveLoan(Long loanSummaryId) {
        LoanSummary loan = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan not found: " + loanSummaryId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException(
                    "Loan is not active. Current status: " + loan.getStatus());
        }
        return loan;
    }

    private void verifyBorrower(LoanSummary loan, Long borrowerId) {
        if (!loan.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException(
                    "Borrower " + borrowerId + " is not the owner of this loan");
        }
    }


    /**
     * Advance payment rule:
     * - No advance payments allowed at all
     * - EMI can only be paid once today's date is in or past the due month
     * - Late payments (after 5th) are allowed with penalty — handled separately
     */
    private void validateAdvancePayment(LoanSummary loan, EmiSchedule nextEmi, LocalDate today) {
        int emiNum = nextEmi.getEmiNumber();

        // Ensure previous EMI is cleared before paying this one
        if (emiNum > 1) {
            EmiSchedule prevEmi = emiScheduleRepository
                    .findByLoanSummaryIdOrderByEmiNumberAsc(loan.getId())
                    .stream()
                    .filter(e -> e.getEmiNumber() == emiNum - 1)
                    .findFirst()
                    .orElse(null);

            if (prevEmi != null && prevEmi.getStatus() != EmiStatus.PAID) {
                throw new BusinessException(
                        "Please clear EMI #" + prevEmi.getEmiNumber()
                                + " (due " + prevEmi.getDueDate() + ") before paying EMI #" + emiNum);
            }
        }

        // Block advance payment: due month must have arrived
        LocalDate dueDate = nextEmi.getDueDate();
        boolean dueMonthReached = today.getYear() > dueDate.getYear()
                || (today.getYear() == dueDate.getYear()
                && today.getMonthValue() >= dueDate.getMonthValue());

        if (!dueMonthReached) {
            throw new BusinessException(
                    "EMI #" + emiNum + " is due on " + dueDate +
                            ". Advance EMI payments are not allowed. " +
                            "Payment window opens on " +
                            dueDate.withDayOfMonth(1) + ".");
        }
    }

    /**
     * Late penalty:
     * - Due date is the 1st of each month
     * - Grace period: up to 5th of the month
     * - After 5th: Penalty = emiAmount × penalRate × (daysLate / 365)
     *
     * daysLate = today − (dueDate + 5 days)
     */
    private BigDecimal calculateLatePenalty(EmiSchedule emi, LocalDate today) {
        LocalDate graceCutoff = emi.getDueDate().withDayOfMonth(GRACE_PERIOD_DAY);

        if (!today.isAfter(graceCutoff)) {
            return BigDecimal.ZERO; // within grace period — no penalty
        }

        long daysLate = ChronoUnit.DAYS.between(graceCutoff, today);

        BigDecimal penalty = emi.getEmiAmount()
                .multiply(PENAL_INTEREST_RATE)
                .multiply(BigDecimal.valueOf(daysLate))
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        log.info("Late penalty applied: emiNumber={} daysLate={} penalty={}",
                emi.getEmiNumber(), daysLate, penalty);

        return penalty;
    }

    /**
     * For accrued interest in foreclosure:
     * - If no EMIs paid → accrue from disbursement date
     * - Otherwise → accrue from the last paid EMI's due date
     */
    private LocalDate resolveInterestFromDate(LoanSummary loan) {
        return emiScheduleRepository
                .findByLoanSummaryIdOrderByEmiNumberAsc(loan.getId())
                .stream()
                .filter(e -> e.getStatus() == EmiStatus.PAID)
                .reduce((first, second) -> second) // last paid
                .map(EmiSchedule::getDueDate)
                .orElse(loan.getDisbursementDate());
    }

    private BankAccount getBorrowerAccount(Long borrowerId) {
        return bankAccountRepository
                .findByUserIdAndAccountType(borrowerId, AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Borrower does not have a savings account"));
    }

    private BankAccount getLenderAccount(Long lenderId) {
        return bankAccountRepository
                .findByUserIdAndAccountType(lenderId, AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Lender does not have a savings account"));
    }
}