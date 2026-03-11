-- V1__create_users_table.sql

CREATE TABLE users (
                       id              BIGSERIAL    PRIMARY KEY,
                       country_code    VARCHAR(5)   NOT NULL,
                       mobile_number   VARCHAR(15)  NOT NULL,
                       full_name       VARCHAR(200),
                       email           VARCHAR(255),
                       gender          VARCHAR(10)  CHECK (gender IN ('MALE','FEMALE','OTHER')),
                       role            VARCHAR(20)  NOT NULL DEFAULT 'BORROWER'
                           CHECK (role IN ('BORROWER','LENDER','ADMIN')),
                       status          VARCHAR(40)  NOT NULL DEFAULT 'MOBILE_VERIFIED'
                           CHECK (status IN ('MOBILE_VERIFIED','REGISTRATION_COMPLETE','PLATFORM_ACCOUNT_CREATED')),
                       kyc_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                           CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
                       pan             VARCHAR(10),
                       income_bracket  VARCHAR(50),
                       p2p_experience  VARCHAR(20)  CHECK (p2p_experience IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
                       address_id      BIGINT,
                       created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT uq_users_mobile UNIQUE (mobile_number, country_code),
                       CONSTRAINT uq_users_pan    UNIQUE (pan)
);

CREATE INDEX idx_users_mobile ON users(mobile_number);
CREATE INDEX idx_users_role   ON users(role);
CREATE INDEX idx_users_status ON users(status);
