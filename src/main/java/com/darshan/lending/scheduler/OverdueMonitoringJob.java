package com.darshan.lending.scheduler;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueMonitoringJob {

    private final EmiScheduleRepository emiScheduleRepository;
    private final LoanSummaryRepository loanSummaryRepository;

    /**
     * Runs every day at midnight.
     *
     * Step 1 — Bulk mark all past-due PENDING EMIs as OVERDUE in one UPDATE query.
     * Step 2 — Bulk mark any ACTIVE loan that now has at least one OVERDUE EMI as DEFAULTED.
     *          Uses a single JOIN query — no N+1 loop.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueEmis() {

        LocalDate today = LocalDate.now();
        log.info("OverdueMonitoringJob started for date: {}", today);

        // ── Step 1: bulk flip PENDING → OVERDUE ───────────────────────────
        int overdueCount = emiScheduleRepository.markOverdueEmis(
                today, EmiStatus.PENDING, EmiStatus.OVERDUE);
        log.info("Marked {} EMIs as OVERDUE", overdueCount);

        // ── Step 2: bulk flip ACTIVE loans → DEFAULTED ────────────────────
        if (overdueCount > 0) {
            int defaultedCount = loanSummaryRepository
                    .markLoansWithOverdueEmisAsDefaulted(LoanStatus.ACTIVE,
                            LoanStatus.DEFAULTED, EmiStatus.OVERDUE);
            log.warn("Marked {} loans as DEFAULTED", defaultedCount);
        }

        log.info("OverdueMonitoringJob completed");
    }
}