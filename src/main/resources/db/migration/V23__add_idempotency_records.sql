CREATE TABLE IF NOT EXISTS idempotency_records (
                                                   id                BIGSERIAL    PRIMARY KEY,
                                                   idempotency_key   VARCHAR(64)  NOT NULL UNIQUE,
    request_path      VARCHAR(255) NOT NULL,
    response_body     TEXT,
    response_status   INTEGER,
    created_at        TIMESTAMP,
    expires_at        TIMESTAMP
    );

CREATE INDEX idx_idempotency_expires_at
    ON idempotency_records (expires_at);