ALTER TABLE lender_preference ADD COLUMN IF NOT EXISTS preferred_payment_day INTEGER
    CHECK (preferred_payment_day BETWEEN 1 AND 28);