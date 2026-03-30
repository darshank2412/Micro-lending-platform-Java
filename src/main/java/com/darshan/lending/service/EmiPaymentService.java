package com.darshan.lending.service;

import com.darshan.lending.dto.EmiScheduleResponse;
import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiPaymentService {

    private final EmiScheduleRepository  emiScheduleRepository;
    private final LoanSummaryRepository  loanSummaryRepository;
    private final BankAccountRepository  bankAccountRepository;
    private final LoanDisbursementService loanDisbursementService;

    @Transactional
    public EmiScheduleResponse payNextEmi(Long loanSummaryId, Long borrowerId) {

        // 1. Load loan summary
        LoanSummary summary = loanSummaryRepository.findById(loanSummaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan not found: " + loanSummaryId));

        if (!summary.getBorrower().getId().equals(borrowerId)) {
            throw new BusinessException("You can only pay EMIs for your own loans");
        }

        if (summary.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException(
                    "Loan is not ACTIVE. Current status: " + summary.getStatus());
        }

        List<EmiSchedule> pendingEmis = emiScheduleRepository
                .findPendingEmis(loanSummaryId, EmiStatus.PENDING);
        if (pendingEmis.isEmpty()) {
            throw new BusinessException("No pending EMIs found for loan: " + loanSummaryId);
        }
        EmiSchedule emi = pendingEmis.get(0);

        // 3. Find borrower and lender accounts
        BankAccount borrowerAccount = bankAccountRepository
                .findByUserIdAndAccountType(borrowerId, AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Borrower does not have a savings account"));

        BankAccount lenderAccount = bankAccountRepository
                .findByUserIdAndAccountType(
                        summary.getLender().getId(), AccountType.SAVINGS)
                .orElseThrow(() -> new BusinessException(
                        "Lender does not have a savings account"));

        // 4. Check borrower has sufficient balance
        if (borrowerAccount.getBalance().compareTo(emi.getEmiAmount()) < 0) {
            throw new BusinessException(
                    "Insufficient balance. Available: " + borrowerAccount.getBalance()
                            + ", Required: " + emi.getEmiAmount());
        }

        // 5. Debit borrower, Credit lender
        borrowerAccount.setBalance(
                borrowerAccount.getBalance().subtract(emi.getEmiAmount()));
        lenderAccount.setBalance(
                lenderAccount.getBalance().add(emi.getEmiAmount()));

        bankAccountRepository.save(borrowerAccount);
        bankAccountRepository.save(lenderAccount);

        // 6. Mark EMI as PAID
        emi.setStatus(EmiStatus.PAID);
        emi.setPaidDate(java.time.LocalDate.now());
        emiScheduleRepository.save(emi);

        // 7. Update LoanSummary
        summary.setEmisPaid(summary.getEmisPaid() + 1);
        summary.setEmisRemaining(summary.getEmisRemaining() - 1);
        summary.setOutstandingPrincipal(emi.getOutstandingPrincipal());

        // 8. Check if all EMIs paid → COMPLETED
        if (summary.getEmisRemaining() == 0) {
            summary.setStatus(LoanStatus.COMPLETED);
            log.info("Loan COMPLETED: loanSummaryId={}", loanSummaryId);
        }

        loanSummaryRepository.save(summary);

        log.info("EMI paid: loanSummaryId={} emiNumber={} amount={}",
                loanSummaryId, emi.getEmiNumber(), emi.getEmiAmount());

        return loanDisbursementService.toEmiResponse(emi);
    }
}