-- Drop old unique constraint on lender_id alone
ALTER TABLE lender_preference
DROP CONSTRAINT IF EXISTS uq_lender_preference_user;

-- Add loan_product_id column
ALTER TABLE lender_preference
    ADD COLUMN IF NOT EXISTS loan_product_id BIGINT;

-- Delete old invalid data (had no loan product)
DELETE FROM lender_preference WHERE loan_product_id IS NULL;

-- Make loan_product_id NOT NULL
ALTER TABLE lender_preference
    ALTER COLUMN loan_product_id SET NOT NULL;

-- Add foreign key to loan_product
ALTER TABLE lender_preference
    ADD CONSTRAINT fk_lender_preference_loan_product
        FOREIGN KEY (loan_product_id) REFERENCES loan_product(id);

-- Add new unique constraint on (lender_id, loan_product_id)
ALTER TABLE lender_preference
    ADD CONSTRAINT uq_lender_preference_user_product
        UNIQUE (lender_id, loan_product_id);