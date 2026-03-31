package com.darshan.lending.service;

import com.darshan.lending.config.LoanRepaymentConfig;
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

    private final LoanSummaryRepository   loanSummaryRepository;
    private final EmiScheduleRepository   emiScheduleRepository;
    private final BankAccountRepository   bankAccountRepository;
    private final LoanDisbursementService loanDisbursementService;
    private final LoanRepaymentConfig     repaymentConfig;

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
        LocalDate today = LocalDate.now();

        validateSequential(loan, nextEmi);

        BigDecimal penalty = calculateLatePenalty(nextEmi, today);
        BigDecimal totalPayable = nextEmi.getEmiAmount().add(penalty)
                .setScale(2, RoundingMode.HALF_UP);

        BankAccount borrowerAccount = getBorrowerAccount(borrowerId);
        BankAccount lenderAccount = getLenderAccount(loan.getLender().getId());

        if (borrowerAccount.getBalance().compareTo(totalPayable) < 0) {
            throw new BusinessException(
                    "Insufficient balance. Required: " + totalPayable
                            + ", Available: " + borrowerAccount.getBalance());
        }

        borrowerAccount.setBalance(borrowerAccount.getBalance().subtract(totalPayable));
        lenderAccount.setBalance(lenderAccount.getBalance().add(totalPayable));
        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        nextEmi.setStatus(EmiStatus.PAID);
        nextEmi.setPaidDate(today);
        emiScheduleRepository.save(nextEmi);

        loan.setEmisPaid(loan.getEmisPaid() + 1);
        loan.setEmisRemaining(loan.getEmisRemaining() - 1);
        loan.setOutstandingPrincipal(nextEmi.getOutstandingPrincipal());

        if (loan.getEmisRemaining() == 0) {
            loan.setStatus(LoanStatus.COMPLETED);
            loan.setOutstandingPrincipal(BigDecimal.ZERO);
            log.info("Loan COMPLETED: loanSummaryId={} borrower={}", loanSummaryId, borrowerId);
        }

        loanSummaryRepository.save(loan);

        log.info("EMI paid: loanSummaryId={} emiNumber={} amount={} penalty={} borrower={}",
                loanSummaryId, nextEmi.getEmiNumber(), nextEmi.getEmiAmount(), penalty, borrowerId);

        EmiScheduleResponse response = loanDisbursementService.toEmiResponse(nextEmi);
        response.setPenaltyAmount(penalty);
        response.setTotalPaid(totalPayable);
        return response;
    }

    @Transactional
    public Foreclosureresponse foreclose(Long loanSummaryId, Long borrowerId) {

        LoanSummary loan = loadActiveLoan(loanSummaryId);
        verifyBorrower(loan, borrowerId);

        LocalDate today = LocalDate.now();
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
        BankAccount lenderAccount = getLenderAccount(loan.getLender().getId());

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

    // ── Private helpers ───────────────────────────────────────────────────

    private LoanSummary loadActiveLoan(Long loanSummaryId) {
        LoanSummary loan = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanSummaryId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException("Loan is not active. Current status: " + loan.getStatus());
        }
        return loan;
    }

    private void verifyBorrower(LoanSummary loan, Long borrowerId) {
        if (!loan.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException("Borrower " + borrowerId + " is not the owner of this loan");
        }
    }

    /**
     * Sequential rule: EMI N-1 must be PAID before EMI N.
     * No cap on advance payments — borrower can pay all remaining EMIs freely.
     */
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

    /**
     * Grace period = dueDate + gracePeriodDays (from config).
     * Loan on 20-Mar → EMI #1 due 20-Apr → grace ends 23-Apr (3 days).
     * Paying on 24-Apr = 1 day late → penalty applied.
     */
    private BigDecimal calculateLatePenalty(EmiSchedule emi, LocalDate today) {
        LocalDate graceCutoff = emi.getDueDate().plusDays(repaymentConfig.getGracePeriodDays());

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
                .orElseThrow(() -> new BusinessException("Borrower does not have a savings account"));
    }

    private BankAccount getLenderAccount(Long lenderId) {
        return bankAccountRepository
                .findByUserIdAndAccountType(lenderId, AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException("Lender does not have a savings account"));
    }
}