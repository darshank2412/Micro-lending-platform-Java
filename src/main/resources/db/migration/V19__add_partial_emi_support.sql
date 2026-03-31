ALTER TABLE emi_schedule ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(15,2);
ALTER TABLE emi_schedule DROP CONSTRAINT IF EXISTS emi_schedule_status_check;
ALTER TABLE emi_schedule ADD CONSTRAINT emi_schedule_status_check
    CHECK (status IN ('PENDING','PAID','OVERDUE','PARTIAL'));