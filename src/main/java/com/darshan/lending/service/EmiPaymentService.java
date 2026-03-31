package com.darshan.lending.service;

import com.darshan.lending.config.LoanRepaymentConfig;
import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.Foreclosureresponse;
import com.darshan.lending.dto.LoanSummaryResponse;
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

    private final LoanSummaryRepository   loanSummaryRepository;
    private final EmiScheduleRepository   emiScheduleRepository;
    private final BankAccountRepository   bankAccountRepository;
    private final LoanDisbursementService loanDisbursementService;
    private final LoanRepaymentConfig     repaymentConfig;

    // ── Pay next EMI ──────────────────────────────────────────────────────────

    /**
     *  borrower enters any amount they can afford.
     * - If amount >= EMI due → EMI marked PAID, excess reduces next EMI
     * - If amount < EMI due → EMI marked PARTIAL, shortfall added to next EMI
     * - Penalty calculated if paying late
     */
    @Transactional
    public EmiScheduleResponse payEmi(Long loanSummaryId, Long borrowerId, BigDecimal amountPaid) {

        LoanSummary loan = loadActiveLoan(loanSummaryId);
        verifyBorrower(loan, borrowerId);

        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        List<EmiSchedule> pendingEmis = emiScheduleRepository
                .findPendingOrOverdueEmis(loanSummaryId);

        if (pendingEmis.isEmpty()) {
            throw new BusinessException("No pending EMIs for loan: " + loanSummaryId);
        }

        EmiSchedule currentEmi = pendingEmis.get(0);
        LocalDate today = LocalDate.now();

        validateSequential(loan, currentEmi);

        // Calculate penalty if late
        BigDecimal penalty = calculateLatePenalty(currentEmi, today);
        BigDecimal totalDue = currentEmi.getEmiAmount().add(penalty)
                .setScale(2, RoundingMode.HALF_UP);

        // Check borrower balance
        BankAccount borrowerAccount = getBorrowerAccount(borrowerId);
        BankAccount lenderAccount   = getLenderAccount(loan.getLender().getId());

        if (borrowerAccount.getBalance().compareTo(amountPaid) < 0) {
            throw new BusinessException(
                    "Insufficient balance. Available: " + borrowerAccount.getBalance()
                            + ", Trying to pay: " + amountPaid);
        }

        // Transfer money
        borrowerAccount.setBalance(borrowerAccount.getBalance().subtract(amountPaid));
        lenderAccount.setBalance(lenderAccount.getBalance().add(amountPaid));
        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        BigDecimal shortfall = totalDue.subtract(amountPaid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal excess    = amountPaid.subtract(totalDue).setScale(2, RoundingMode.HALF_UP);
        boolean isPartial    = amountPaid.compareTo(totalDue) < 0;
        boolean isExcess     = amountPaid.compareTo(totalDue) > 0;

        // Update current EMI
        currentEmi.setPaidDate(today);
        currentEmi.setPaidAmount(amountPaid);

        if (isPartial) {
            // ── PARTIAL PAYMENT ───────────────────────────────────────────────
            currentEmi.setStatus(EmiStatus.PARTIAL);

            // Add shortfall to next EMI
            if (pendingEmis.size() > 1) {
                EmiSchedule nextEmi = pendingEmis.get(1);
                nextEmi.setEmiAmount(
                        nextEmi.getEmiAmount().add(shortfall).setScale(2, RoundingMode.HALF_UP));
                emiScheduleRepository.save(nextEmi);
                log.info("Shortfall of {} added to EMI #{}", shortfall, nextEmi.getEmiNumber());
            } else {
                // Last EMI and partial — mark loan as still active
                log.warn("Partial payment on last EMI. Shortfall: {}", shortfall);
            }

            log.info("PARTIAL EMI: loanSummaryId={} emiNumber={} paid={} due={} shortfall={}",
                    loanSummaryId, currentEmi.getEmiNumber(), amountPaid, totalDue, shortfall);

        } else {
            // ── FULL OR EXCESS PAYMENT ────────────────────────────────────────
            currentEmi.setStatus(EmiStatus.PAID);

            if (isExcess && pendingEmis.size() > 1) {
                // Reduce next EMI by excess amount
                EmiSchedule nextEmi = pendingEmis.get(1);
                BigDecimal reduced = nextEmi.getEmiAmount().subtract(excess)
                        .setScale(2, RoundingMode.HALF_UP);
                if (reduced.compareTo(BigDecimal.ZERO) > 0) {
                    nextEmi.setEmiAmount(reduced);
                    emiScheduleRepository.save(nextEmi);
                    log.info("Excess of {} reduced EMI #{}", excess, nextEmi.getEmiNumber());
                }
            }

            // Update loan summary only on full payment
            loan.setEmisPaid(loan.getEmisPaid() + 1);
            loan.setEmisRemaining(loan.getEmisRemaining() - 1);
            loan.setOutstandingPrincipal(currentEmi.getOutstandingPrincipal());

            if (loan.getEmisRemaining() == 0) {
                loan.setStatus(LoanStatus.COMPLETED);
                loan.setOutstandingPrincipal(BigDecimal.ZERO);
                log.info("Loan COMPLETED: loanSummaryId={}", loanSummaryId);
            }

            log.info("FULL EMI paid: loanSummaryId={} emiNumber={} paid={} penalty={}",
                    loanSummaryId, currentEmi.getEmiNumber(), amountPaid, penalty);
        }

        emiScheduleRepository.save(currentEmi);
        loanSummaryRepository.save(loan);

        EmiScheduleResponse response = loanDisbursementService.toEmiResponse(currentEmi);
        response.setPenaltyAmount(penalty);
        response.setTotalPaid(amountPaid);
        response.setShortfall(isPartial ? shortfall : BigDecimal.ZERO);
        response.setMessage(isPartial
                ? "Partial payment accepted. Shortfall of ₹" + shortfall + " added to next EMI."
                : "EMI paid successfully.");
        return response;
    }

    // ── Foreclose loan ────────────────────────────────────────────────────────

    @Transactional
    public Foreclosureresponse foreclose(Long loanSummaryId, Long borrowerId) {

        LoanSummary loan = loadActiveLoan(loanSummaryId);
        verifyBorrower(loan, borrowerId);

        LocalDate today       = LocalDate.now();
        BigDecimal outstanding = loan.getOutstandingPrincipal();

        LocalDate interestFrom = resolveInterestFromDate(loan);
        long daysAccrued = ChronoUnit.DAYS.between(interestFrom, today);
        if (daysAccrued < 0) daysAccrued = 0;

        BigDecimal accruedInterest = outstanding
                .multiply(loan.getInterestRate()
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(daysAccrued))
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        BigDecimal foreclosureCharge = outstanding
                .multiply(repaymentConfig.getForeclosurePenaltyRate())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPayable = outstanding.add(accruedInterest).add(foreclosureCharge)
                .setScale(2, RoundingMode.HALF_UP);

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

        List<EmiSchedule> remaining = emiScheduleRepository.findPendingOrOverdueEmis(loanSummaryId);
        remaining.forEach(e -> { e.setStatus(EmiStatus.PAID); e.setPaidDate(today); });
        emiScheduleRepository.saveAll(remaining);

        loan.setStatus(LoanStatus.COMPLETED);
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setEmisPaid(loan.getTenureMonths());
        loan.setEmisRemaining(0);
        loanSummaryRepository.save(loan);

        log.info("Loan FORECLOSED: loanSummaryId={} borrower={} totalPaid={}",
                loanSummaryId, borrowerId, totalPayable);

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

    // ── Partial repayment ─────────────────────────────────────────────────────

    @Transactional
    public LoanSummaryResponse partialRepayment(Long loanSummaryId, Long borrowerId,
                                                BigDecimal amount) {

        LoanSummary summary = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan not found: " + loanSummaryId));

        if (!summary.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException("You can only repay your own loans");
        }
        if (summary.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException("Loan is not ACTIVE");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Repayment amount must be positive");
        }
        if (amount.compareTo(summary.getOutstandingPrincipal()) > 0) {
            throw new BusinessException("Amount exceeds outstanding principal: "
                    + summary.getOutstandingPrincipal());
        }

        BankAccount borrowerAccount = getBorrowerAccount(borrowerId);
        BankAccount lenderAccount   = getLenderAccount(summary.getLender().getId());

        if (borrowerAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Insufficient balance");
        }

        borrowerAccount.setBalance(borrowerAccount.getBalance().subtract(amount));
        lenderAccount.setBalance(lenderAccount.getBalance().add(amount));
        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        BigDecimal newOutstanding = summary.getOutstandingPrincipal().subtract(amount)
                .setScale(2, RoundingMode.HALF_UP);
        summary.setOutstandingPrincipal(newOutstanding);

        if (newOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            summary.setStatus(LoanStatus.COMPLETED);
            summary.setEmisRemaining(0);
            log.info("Loan COMPLETED via partial repayment: loanSummaryId={}", loanSummaryId);
        }

        loanSummaryRepository.save(summary);
        log.info("Partial repayment: loanSummaryId={} amount={} newOutstanding={}",
                loanSummaryId, amount, newOutstanding);

        return loanDisbursementService.toSummaryResponse(summary);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    private void validateSequential(LoanSummary loan, EmiSchedule nextEmi) {
        int emiNum = nextEmi.getEmiNumber();
        if (emiNum <= 1) return;

        List<EmiSchedule> allEmis = emiScheduleRepository
                .findByLoanSummaryIdOrderByEmiNumberAsc(loan.getId());

        allEmis.stream()
                .filter(e -> e.getEmiNumber() == emiNum - 1)
                .findFirst()
                .ifPresent(prevEmi -> {
                    if (prevEmi.getStatus() != EmiStatus.PAID) {
                        throw new BusinessException(
                                "Please clear EMI #" + prevEmi.getEmiNumber()
                                        + " (due " + prevEmi.getDueDate()
                                        + ") before paying EMI #" + emiNum);
                    }
                });
    }

    private BigDecimal calculateLatePenalty(EmiSchedule emi, LocalDate today) {
        LocalDate graceCutoff = emi.getDueDate()
                .plusDays(repaymentConfig.getGracePeriodDays());

        if (!today.isAfter(graceCutoff)) {
            return BigDecimal.ZERO;
        }

        long daysLate = ChronoUnit.DAYS.between(graceCutoff, today);

        BigDecimal penalty = emi.getEmiAmount()
                .multiply(repaymentConfig.getPenalInterestRate())
                .multiply(BigDecimal.valueOf(daysLate))
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        log.info("Late penalty: emiNumber={} dueDate={} graceCutoff={} daysLate={} penalty={}",
                emi.getEmiNumber(), emi.getDueDate(), graceCutoff, daysLate, penalty);

        return penalty;
    }

    private LocalDate resolveInterestFromDate(LoanSummary loan) {
        return emiScheduleRepository
                .findByLoanSummaryIdOrderByEmiNumberAsc(loan.getId())
                .stream()
                .filter(e -> e.getStatus() == EmiStatus.PAID)
                .reduce((first, second) -> second)
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