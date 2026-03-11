-- Add product FK columns to bank_account
ALTER TABLE bank_account
    ADD COLUMN IF NOT EXISTS savings_product_id BIGINT,
    ADD COLUMN IF NOT EXISTS loan_product_id    BIGINT;

ALTER TABLE bank_account
    ADD CONSTRAINT fk_bank_account_savings_product
        FOREIGN KEY (savings_product_id) REFERENCES savings_product(id);

ALTER TABLE bank_account
    ADD CONSTRAINT fk_bank_account_loan_product
        FOREIGN KEY (loan_product_id) REFERENCES loan_product(id);

-- Replace boolean active column with status enum on loan_product
ALTER TABLE loan_product
    ADD COLUMN IF NOT EXISTS status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE','INACTIVE'));

ALTER TABLE loan_product DROP COLUMN IF EXISTS active;