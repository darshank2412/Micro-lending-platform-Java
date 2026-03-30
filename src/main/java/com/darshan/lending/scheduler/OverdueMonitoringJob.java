package com.darshan.lending.scheduler;

import com.darshan.lending.entity.LoanSummary;
import com.darshan.lending.entity.enums.EmiStatus;
import com.darshan.lending.entity.enums.LoanStatus;
import com.darshan.lending.repository.EmiScheduleRepository;
import com.darshan.lending.repository.LoanSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueMonitoringJob {

    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanSummaryRepository loanSummaryRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueEmis() {

        LocalDate today = LocalDate.now();
        log.info("OverdueMonitoringJob started for date: {}", today);

        // 1. Bulk mark all overdue EMIs in one query
        int overdueCount = emiScheduleRepository.markOverdueEmis(
                today, EmiStatus.PENDING, EmiStatus.OVERDUE);
        log.info("Marked {} EMIs as OVERDUE", overdueCount);

        // 2. Find all ACTIVE loans and check if any have overdue EMIs
        if (overdueCount > 0) {
            List<LoanSummary> activeLoans =
                    loanSummaryRepository.findByStatus(LoanStatus.ACTIVE);

            for (LoanSummary loan : activeLoans) {
                boolean hasOverdue = emiScheduleRepository
                        .findOverdueEmis(today, EmiStatus.OVERDUE)
                        .stream()
                        .anyMatch(e -> e.getLoanSummary().getId().equals(loan.getId()));

                if (hasOverdue) {
                    loan.setStatus(LoanStatus.DEFAULTED);
                    loanSummaryRepository.save(loan);
                    log.warn("Loan DEFAULTED: loanSummaryId={} borrower={}",
                            loan.getId(), loan.getBorrower().getId());
                }
            }
        }

        log.info("OverdueMonitoringJob completed");
    }
}