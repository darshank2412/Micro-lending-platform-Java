package com.darshan.lending.repository;

import com.darshan.lending.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndRequestPath(
            String key, String path);

    void deleteByExpiresAtBefore(LocalDateTime now);
}