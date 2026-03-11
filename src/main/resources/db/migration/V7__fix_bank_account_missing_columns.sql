ALTER TABLE bank_account
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(10) NOT NULL DEFAULT 'SAVINGS'
    CHECK (account_type IN ('SAVINGS', 'LOAN'));

ALTER TABLE bank_account
    ADD COLUMN IF NOT EXISTS savings_product_id BIGINT,
    ADD COLUMN IF NOT EXISTS loan_product_id    BIGINT;

ALTER TABLE bank_account
DROP CONSTRAINT IF EXISTS fk_bank_account_savings_product;

ALTER TABLE bank_account
DROP CONSTRAINT IF EXISTS fk_bank_account_loan_product;

ALTER TABLE bank_account
    ADD CONSTRAINT fk_bank_account_savings_product
        FOREIGN KEY (savings_product_id) REFERENCES savings_product(id);

ALTER TABLE bank_account
    ADD CONSTRAINT fk_bank_account_loan_product
        FOREIGN KEY (loan_product_id) REFERENCES loan_product(id);