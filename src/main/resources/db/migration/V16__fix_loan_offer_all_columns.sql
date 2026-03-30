-- Add missing columns
ALTER TABLE loan_offer ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loan_offer ADD COLUMN IF NOT EXISTS match_rank INT NOT NULL DEFAULT 0;
ALTER TABLE loan_offer ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

-- Rename offered_amount to loan_amount (already done in V15)
-- Rename interest_rate to offered_interest_rate
ALTER TABLE loan_offer RENAME COLUMN interest_rate TO offered_interest_rate;

-- Drop columns that don't exist in entity
ALTER TABLE loan_offer DROP COLUMN IF EXISTS offered_amount;
ALTER TABLE loan_offer DROP COLUMN IF EXISTS tenure_months;
ALTER TABLE loan_offer DROP COLUMN IF EXISTS borrower_id;

-- Add unique constraint
ALTER TABLE loan_offer DROP CONSTRAINT IF EXISTS uq_loan_offer_request_lender;
ALTER TABLE loan_offer ADD CONSTRAINT uq_loan_offer_request_lender
    UNIQUE (loan_request_id, lender_id);