package com.darshan.lending.service;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.dto.LoanSummaryResponse;
import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import com.darshan.lending.util.EmiCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementService {

    private final LoanOfferRepository        loanOfferRepository;
    private final LoanRequestRepository      loanRequestRepository;
    private final LoanSummaryRepository      loanSummaryRepository;
    private final EmiScheduleRepository      emiScheduleRepository;
    private final BankAccountRepository      bankAccountRepository;
    private final UserRepository             userRepository;
    private final EmiCalculator              emiCalculator;
    private final LenderPreferenceRepository lenderPreferenceRepository;

    @Transactional
    public LoanSummaryResponse disburseLoan(Long offerId) {

        LoanOffer offer = loanOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan offer not found: " + offerId));

        if (offer.getStatus() != LoanOfferStatus.ACCEPTED) {
            throw new BusinessException(
                    "Only ACCEPTED offers can be disbursed. Current: " + offer.getStatus());
        }

        if (loanSummaryRepository.findByLoanOfferId(offerId).isPresent()) {
            throw new BusinessException("Loan already disbursed for offer: " + offerId);
        }

        User borrower         = offer.getLoanRequest().getBorrower();
        User lender           = offer.getLender();
        BigDecimal amount     = offer.getLoanAmount();
        BigDecimal annualRate = offer.getOfferedInterestRate();
        int tenure            = offer.getLoanRequest().getTenureMonths();

        BankAccount lenderAccount = bankAccountRepository
                .findByUserIdAndAccountType(lender.getId(), AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Lender does not have a savings account"));

        BankAccount borrowerAccount = bankAccountRepository
                .findByUserIdAndAccountType(borrower.getId(), AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Borrower does not have a savings account"));

        if (lenderAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Lender has insufficient balance. Available: "
                            + lenderAccount.getBalance() + ", Required: " + amount);
        }

        lenderAccount.setBalance(lenderAccount.getBalance().subtract(amount));
        borrowerAccount.setBalance(borrowerAccount.getBalance().add(amount));
        bankAccountRepository.save(lenderAccount);
        bankAccountRepository.save(borrowerAccount);

        // ── Resolve preferred EMI day ─────────────────────────────────────
        // Priority: borrower's choice → lender's preference → null (default)
        Integer preferredDay = resolvePreferredEmiDay(
                offer.getLoanRequest(),
                lender.getId(),
                offer.getLoanRequest().getLoanProduct().getId());

        LocalDate disbursementDate = LocalDate.now();
        LocalDate firstEmiDate     = calculateFirstEmiDate(disbursementDate, preferredDay);
        LocalDate lastEmiDate      = firstEmiDate.plusMonths(tenure - 1);

        // ── Base EMI (standard, no gap adjustment) ────────────────────────
        BigDecimal emiAmount      = emiCalculator.calculateEmi(amount, annualRate, tenure);
        BigDecimal totalRepayment = emiAmount.multiply(BigDecimal.valueOf(tenure))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest  = totalRepayment.subtract(amount)
                .setScale(2, RoundingMode.HALF_UP);

        LoanSummary summary = LoanSummary.builder()
                .loanOffer(offer)
                .borrower(borrower)
                .lender(lender)
                .principalAmount(amount)
                .interestRate(annualRate)
                .tenureMonths(tenure)
                .emiAmount(emiAmount)
                .totalRepaymentAmount(totalRepayment)
                .totalInterestAmount(totalInterest)
                .outstandingPrincipal(amount)
                .disbursementDate(disbursementDate)
                .firstEmiDate(firstEmiDate)
                .lastEmiDate(lastEmiDate)
                .emisPaid(0)
                .emisRemaining(tenure)
                .status(LoanStatus.ACTIVE)
                .build();

        summary = loanSummaryRepository.save(summary);

        generateEmiSchedule(summary, amount, annualRate, emiAmount,
                tenure, disbursementDate, firstEmiDate);

        LoanRequest request = offer.getLoanRequest();
        request.setStatus(LoanRequestStatus.DISBURSED);
        loanRequestRepository.save(request);

        log.info("Loan disbursed: offerId={} borrower={} lender={} amount={} firstEmi={}",
                offerId, borrower.getId(), lender.getId(), amount, firstEmiDate);

        return toSummaryResponse(summary);
    }

    /**
     * Generate full amortization schedule.
     * Gap interest (extra days beyond 30) is added ONLY to the first EMI.
     */
    private void generateEmiSchedule(LoanSummary summary,
                                     BigDecimal principal,
                                     BigDecimal annualRate,
                                     BigDecimal emiAmount,
                                     int tenure,
                                     LocalDate disbursementDate,
                                     LocalDate firstEmiDate) {

        BigDecimal outstanding = principal;
        List<EmiSchedule> schedule = new ArrayList<>();

        // ── Calculate gap interest for first EMI ──────────────────────────
        // Standard period = 30 days. If actual gap > 30, charge extra interest.
        long actualGapDays = ChronoUnit.DAYS.between(disbursementDate, firstEmiDate);
        BigDecimal gapInterest = BigDecimal.ZERO;

        if (actualGapDays > 30) {
            long extraDays = actualGapDays - 30;
            // Daily interest = principal × annualRate / 100 / 365
            gapInterest = principal
                    .multiply(annualRate)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(extraDays))
                    .setScale(2, RoundingMode.HALF_UP);

            log.info("Gap interest applied: disbursement={} firstEmi={} gapDays={} extraDays={} gapInterest={}",
                    disbursementDate, firstEmiDate, actualGapDays, extraDays, gapInterest);
        }

        for (int i = 1; i <= tenure; i++) {

            BigDecimal interestComponent =
                    emiCalculator.calculateInterestComponent(outstanding, annualRate);
            BigDecimal principalComponent =
                    emiCalculator.calculatePrincipalComponent(emiAmount, interestComponent);

            // Last EMI: pay exact remaining principal to avoid rounding drift
            if (i == tenure) {
                principalComponent = outstanding;
            }

            outstanding = outstanding.subtract(principalComponent)
                    .setScale(2, RoundingMode.HALF_UP);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
                outstanding = BigDecimal.ZERO;
            }

            // EMI due date = firstEmiDate + (i-1) months
            LocalDate dueDate = firstEmiDate.plusMonths(i - 1);

            // First EMI gets the gap interest added
            BigDecimal thisEmiAmount = (i == 1)
                    ? emiAmount.add(gapInterest).setScale(2, RoundingMode.HALF_UP)
                    : emiAmount;

            BigDecimal thisInterestComponent = (i == 1)
                    ? interestComponent.add(gapInterest).setScale(2, RoundingMode.HALF_UP)
                    : interestComponent;

            EmiSchedule emi = EmiSchedule.builder()
                    .loanSummary(summary)
                    .emiNumber(i)
                    .dueDate(dueDate)
                    .emiAmount(thisEmiAmount)
                    .principalComponent(principalComponent)
                    .interestComponent(thisInterestComponent)
                    .outstandingPrincipal(outstanding)
                    .status(EmiStatus.PENDING)
                    .build();

            schedule.add(emi);
        }

        emiScheduleRepository.saveAll(schedule);
        log.info("EMI schedule generated: loanSummaryId={} emis={} firstDue={} lastDue={}",
                summary.getId(), schedule.size(),
                schedule.get(0).getDueDate(),
                schedule.get(schedule.size() - 1).getDueDate());
    }

    // ── Read operations ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmiScheduleResponse> getEmiSchedule(Long loanSummaryId) {
        return emiScheduleRepository
                .findByLoanSummaryIdOrderByEmiNumberAsc(loanSummaryId)
                .stream().map(this::toEmiResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LoanSummaryResponse getLoanSummary(Long loanSummaryId) {
        LoanSummary summary = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan summary not found: " + loanSummaryId));
        return toSummaryResponse(summary);
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getMyLoans(Long borrowerId) {
        return loanSummaryRepository.findByBorrowerId(borrowerId)
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getLoansFunded(Long lenderId) {
        return loanSummaryRepository.findByLenderId(lenderId)
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getAllLoans() {
        return loanSummaryRepository.findAll()
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<LoanSummaryResponse> getAllLoansPaged(Pageable pageable) {
        return loanSummaryRepository.findAll(pageable)
                .map(this::toSummaryResponse);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolve the preferred EMI day.
     * Priority: borrower's preferredEmiDay → lender's preferredPaymentDay → null
     */
    private Integer resolvePreferredEmiDay(LoanRequest request,
                                           Long lenderId, Long productId) {
        // 1. Borrower's explicit choice
        if (request.getPreferredEmiDay() != null) {
            return request.getPreferredEmiDay();
        }
        // 2. Lender's default preference
        return lenderPreferenceRepository
                .findByLenderIdAndLoanProductId(lenderId, productId)
                .map(LenderPreference::getPreferredPaymentDay)
                .orElse(null);
    }

    /**
     * Calculate first EMI date.
     * If preferredDay is null → disbursementDate + 1 month (same day).
     * If preferredDay is set → that day of the next month.
     *   - If disbursement day < preferredDay → next month's preferredDay
     *   - If disbursement day >= preferredDay → month after next's preferredDay
     *     (to ensure at least ~30 days before first payment)
     */
    private LocalDate calculateFirstEmiDate(LocalDate disbursementDate,
                                            Integer preferredDay) {
        if (preferredDay == null) {
            return disbursementDate.plusMonths(1);
        }

        // If disbursement is on the 25th and preferred day is 27:
        // first EMI = April 27 (same next month, gives ~33 days)
        // If disbursement is on the 28th and preferred day is 5:
        // first EMI = month-after-next 5th (gives ~38 days, avoids <30 day gap)
        LocalDate candidateNextMonth = disbursementDate.plusMonths(1)
                .withDayOfMonth(Math.min(preferredDay,
                        disbursementDate.plusMonths(1).lengthOfMonth()));

        long gapDays = ChronoUnit.DAYS.between(disbursementDate, candidateNextMonth);

        if (gapDays < 15) {
            // Too soon — push to month after next
            return disbursementDate.plusMonths(2)
                    .withDayOfMonth(Math.min(preferredDay,
                            disbursementDate.plusMonths(2).lengthOfMonth()));
        }

        return candidateNextMonth;
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    public LoanSummaryResponse toSummaryResponse(LoanSummary s) {
        return LoanSummaryResponse.builder()
                .id(s.getId())
                .loanOfferId(s.getLoanOffer().getId())
                .borrowerId(s.getBorrower().getId())
                .borrowerName(s.getBorrower().getFullName())
                .lenderId(s.getLender().getId())
                .lenderName(s.getLender().getFullName())
                .principalAmount(s.getPrincipalAmount())
                .interestRate(s.getInterestRate())
                .tenureMonths(s.getTenureMonths())
                .emiAmount(s.getEmiAmount())
                .totalRepaymentAmount(s.getTotalRepaymentAmount())
                .totalInterestAmount(s.getTotalInterestAmount())
                .outstandingPrincipal(s.getOutstandingPrincipal())
                .disbursementDate(s.getDisbursementDate())
                .firstEmiDate(s.getFirstEmiDate())
                .lastEmiDate(s.getLastEmiDate())
                .emisPaid(s.getEmisPaid())
                .emisRemaining(s.getEmisRemaining())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .build();
    }

    public EmiScheduleResponse toEmiResponse(EmiSchedule e) {
        return EmiScheduleResponse.builder()
                .id(e.getId())
                .loanSummaryId(e.getLoanSummary().getId())
                .emiNumber(e.getEmiNumber())
                .dueDate(e.getDueDate())
                .paidDate(e.getPaidDate())
                .emiAmount(e.getEmiAmount())
                .principalComponent(e.getPrincipalComponent())
                .interestComponent(e.getInterestComponent())
                .outstandingPrincipal(e.getOutstandingPrincipal())
                .status(e.getStatus())
                .build();
    }
}