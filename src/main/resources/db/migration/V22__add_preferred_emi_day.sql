ALTER TABLE loan_request ADD COLUMN IF NOT EXISTS preferred_emi_day INTEGER
    CHECK (preferred_emi_day BETWEEN 1 AND 28);

ALTER TABLE lender_preference ADD COLUMN IF NOT EXISTS preferred_payment_day INTEGER
    CHECK (preferred_payment_day BETWEEN 1 AND 28);

ALTER TABLE emi_schedule ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(15,2);

ALTER TABLE emi_schedule DROP CONSTRAINT IF EXISTS emi_schedule_status_check;
ALTER TABLE emi_schedule ADD CONSTRAINT emi_schedule_status_check
    CHECK (status IN ('PENDING','PAID','OVERDUE','PARTIAL'));