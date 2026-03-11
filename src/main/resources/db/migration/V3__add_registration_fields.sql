-- V3__add_registration_fields.sql
-- Add missing fields for complete registration flow

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS phone_number  VARCHAR(15),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- OTP table for phone/email verification
CREATE TABLE IF NOT EXISTS otp_verification (
                                                id          BIGSERIAL     PRIMARY KEY,
                                                identifier  VARCHAR(255)  NOT NULL,  -- phone number or email
    otp_code    VARCHAR(6)    NOT NULL,
    otp_type    VARCHAR(10)   NOT NULL CHECK (otp_type IN ('PHONE','EMAIL')),
    purpose     VARCHAR(20)   NOT NULL CHECK (purpose IN ('REGISTRATION','LOGIN','RESET')),
    expires_at  TIMESTAMP     NOT NULL,
    verified    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_otp_identifier ON otp_verification(identifier, otp_type);

-- KYC documents table
CREATE TABLE IF NOT EXISTS kyc_document (
                                            id              BIGSERIAL     PRIMARY KEY,
                                            user_id         BIGINT        NOT NULL,
                                            document_type   VARCHAR(30)   NOT NULL CHECK (document_type IN ('AADHAAR','PAN','PASSPORT','DRIVING_LICENSE','VOTER_ID')),
    document_number VARCHAR(50)   NOT NULL,
    document_url    VARCHAR(500),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','VERIFIED','REJECTED')),
    rejection_note  TEXT,
    submitted_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP,

    CONSTRAINT fk_kyc_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_kyc_user_doc UNIQUE (user_id, document_type)
    );

CREATE INDEX idx_kyc_user ON kyc_document(user_id);
CREATE INDEX idx_kyc_status ON kyc_document(status);
