package com.darshan.lending.scheduler;

import com.darshan.lending.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupScheduler {

    private final IdempotencyRecordRepository repo;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanExpiredKeys() {
        repo.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Idempotency cleanup ran at {}", LocalDateTime.now());
    }
}