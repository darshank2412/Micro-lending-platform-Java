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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementService {

    private final LoanOfferRepository    loanOfferRepository;
    private final LoanRequestRepository  loanRequestRepository;
    private final LoanSummaryRepository  loanSummaryRepository;
    private final EmiScheduleRepository  emiScheduleRepository;
    private final BankAccountRepository  bankAccountRepository;
    private final UserRepository         userRepository;
    private final EmiCalculator          emiCalculator;

    /**
     * ADMIN — Disburse a loan for an ACCEPTED offer.
     *
     * Steps:
     * 1. Validate offer is ACCEPTED
     * 2. Check lender has enough balance
     * 3. Debit lender account, credit borrower account
     * 4. Create LoanSummary
     * 5. Generate full EMI schedule (amortization table)
     * 6. Transition LoanRequest → DISBURSED
     */
    @Transactional
    public LoanSummaryResponse disburseLoan(Long offerId) {

        // 1. Load and validate offer
        LoanOffer offer = loanOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan offer not found: " + offerId));

        if (offer.getStatus() != LoanOfferStatus.ACCEPTED) {
            throw new BusinessException(
                    "Only ACCEPTED offers can be disbursed. Current: " + offer.getStatus());
        }

        // Check not already disbursed
        if (loanSummaryRepository.findByLoanOfferId(offerId).isPresent()) {
            throw new BusinessException("Loan already disbursed for offer: " + offerId);
        }

        User borrower = offer.getLoanRequest().getBorrower();
        User lender   = offer.getLender();
        BigDecimal amount = offer.getLoanAmount();
        BigDecimal annualRate = offer.getOfferedInterestRate();
        int tenure = offer.getLoanRequest().getTenureMonths();

        // 2. Find lender and borrower bank accounts
        BankAccount lenderAccount = bankAccountRepository
                .findByUserIdAndAccountType(lender.getId(), AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Lender does not have a savings account"));

        BankAccount borrowerAccount = bankAccountRepository
                .findByUserIdAndAccountType(borrower.getId(), AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Borrower does not have a savings account"));

        // 3. Check lender has sufficient balance
        if (lenderAccount.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "Lender has insufficient balance. Available: "
                            + lenderAccount.getBalance() + ", Required: " + amount);
        }

        // 4. Debit lender, Credit borrower
        lenderAccount.setBalance(lenderAccount.getBalance().subtract(amount));
        borrowerAccount.setBalance(borrowerAccount.getBalance().add(amount));
        bankAccountRepository.save(lenderAccount);
        bankAccountRepository.save(borrowerAccount);

        // 5. Calculate EMI
        BigDecimal emiAmount = emiCalculator.calculateEmi(amount, annualRate, tenure);
        BigDecimal totalRepayment = emiAmount.multiply(BigDecimal.valueOf(tenure))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = totalRepayment.subtract(amount)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate disbursementDate = LocalDate.now();
        LocalDate firstEmiDate = disbursementDate.plusMonths(1);
        LocalDate lastEmiDate  = disbursementDate.plusMonths(tenure);

        // 6. Create LoanSummary
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

        // 7. Generate EMI schedule (amortization table)
        generateEmiSchedule(summary, amount, annualRate, emiAmount, tenure, firstEmiDate);

        // 8. Transition LoanRequest → DISBURSED
        LoanRequest request = offer.getLoanRequest();
        request.setStatus(LoanRequestStatus.DISBURSED);
        loanRequestRepository.save(request);

        log.info("Loan disbursed: offerId={} borrower={} lender={} amount={}",
                offerId, borrower.getId(), lender.getId(), amount);

        return toSummaryResponse(summary);
    }

    /**
     * Generate full amortization table for the loan.
     * Each row = one EMI with principal & interest breakdown.
     */
    private void generateEmiSchedule(LoanSummary summary,
                                     BigDecimal principal,
                                     BigDecimal annualRate,
                                     BigDecimal emiAmount,
                                     int tenure,
                                     LocalDate firstEmiDate) {

        BigDecimal outstanding = principal;
        List<EmiSchedule> schedule = new ArrayList<>();

        for (int i = 1; i <= tenure; i++) {
            BigDecimal interestComponent =
                    emiCalculator.calculateInterestComponent(outstanding, annualRate);
            BigDecimal principalComponent =
                    emiCalculator.calculatePrincipalComponent(emiAmount, interestComponent);

            // Last EMI — adjust for rounding difference
            if (i == tenure) {
                principalComponent = outstanding;
            }

            outstanding = outstanding.subtract(principalComponent)
                    .setScale(2, RoundingMode.HALF_UP);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
                outstanding = BigDecimal.ZERO;
            }

            EmiSchedule emi = EmiSchedule.builder()
                    .loanSummary(summary)
                    .emiNumber(i)
                    .dueDate(firstEmiDate.plusMonths(i - 1))
                    .emiAmount(emiAmount)
                    .principalComponent(principalComponent)
                    .interestComponent(interestComponent)
                    .outstandingPrincipal(outstanding)
                    .status(EmiStatus.PENDING)
                    .build();

            schedule.add(emi);
        }

        emiScheduleRepository.saveAll(schedule);
        log.info("EMI schedule generated: loanSummaryId={} emis={}",
                summary.getId(), schedule.size());
    }

    // ── View EMI schedule ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<EmiScheduleResponse> getEmiSchedule(Long loanSummaryId) {
        return emiScheduleRepository
                .findByLoanSummaryIdOrderByEmiNumberAsc(loanSummaryId)
                .stream().map(this::toEmiResponse).collect(Collectors.toList());
    }

    // ── View loan summary ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public LoanSummaryResponse getLoanSummary(Long loanSummaryId) {
        LoanSummary summary = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan summary not found: " + loanSummaryId));
        return toSummaryResponse(summary);
    }

    // ── View my loans (borrower) ──────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getMyLoans(Long borrowerId) {
        return loanSummaryRepository.findByBorrowerId(borrowerId)
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    // ── View loans I funded (lender) ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getLoansFunded(Long lenderId) {
        return loanSummaryRepository.findByLenderId(lenderId)
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    // ── Admin: all loans ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LoanSummaryResponse> getAllLoans() {
        return loanSummaryRepository.findAll()
                .stream().map(this::toSummaryResponse).collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────
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