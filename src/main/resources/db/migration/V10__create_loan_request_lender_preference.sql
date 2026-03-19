-- Loan Request table
CREATE TABLE loan_request (
                              id                  BIGSERIAL       PRIMARY KEY,
                              borrower_id         BIGINT          NOT NULL,
                              loan_product_id     BIGINT          NOT NULL,
                              amount              NUMERIC(15,2)   NOT NULL,
                              tenure_months       INTEGER         NOT NULL,
                              purpose             VARCHAR(30)     NOT NULL
                                  CHECK (purpose IN (
                                                     'EDUCATION','SMALL_BUSINESS','EMERGENCY',
                                                     'ASSET_PURCHASE','MEDICAL','TRAVEL','OTHER')),
                              purpose_description VARCHAR(500),
                              status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN (
                                                    'PENDING','MATCHED','ACCEPTED',
                                                    'REJECTED','CANCELLED','DISBURSED')),
                              rejection_reason    VARCHAR(500),
                              created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at          TIMESTAMP,

                              CONSTRAINT fk_loan_request_borrower FOREIGN KEY (borrower_id)     REFERENCES users(id),
                              CONSTRAINT fk_loan_request_product  FOREIGN KEY (loan_product_id) REFERENCES loan_product(id),
                              CONSTRAINT chk_loan_amount_positive CHECK (amount > 0),
                              CONSTRAINT chk_tenure_positive      CHECK (tenure_months >= 1)
);

CREATE INDEX idx_loan_request_borrower ON loan_request(borrower_id);
CREATE INDEX idx_loan_request_status   ON loan_request(status);

-- Lender Preference table
CREATE TABLE lender_preference (
                                   id                  BIGSERIAL       PRIMARY KEY,
                                   lender_id           BIGINT          NOT NULL UNIQUE,
                                   min_interest_rate   NUMERIC(5,2)    NOT NULL,
                                   max_interest_rate   NUMERIC(5,2)    NOT NULL,
                                   min_tenure_months   INTEGER         NOT NULL,
                                   max_tenure_months   INTEGER         NOT NULL,
                                   min_loan_amount     NUMERIC(15,2)   NOT NULL,
                                   max_loan_amount     NUMERIC(15,2)   NOT NULL,
                                   risk_appetite       VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM'
                                       CHECK (risk_appetite IN ('LOW','MEDIUM','HIGH')),
                                   is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
                                   created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at          TIMESTAMP,

                                   CONSTRAINT fk_lender_preference_user    FOREIGN KEY (lender_id) REFERENCES users(id),
                                   CONSTRAINT uq_lender_preference_user    UNIQUE (lender_id),
                                   CONSTRAINT chk_interest_range           CHECK (min_interest_rate < max_interest_rate),
                                   CONSTRAINT chk_tenure_range_pref        CHECK (min_tenure_months < max_tenure_months),
                                   CONSTRAINT chk_amount_range_pref        CHECK (min_loan_amount   < max_loan_amount)
);

CREATE INDEX idx_lender_preference_lender    ON lender_preference(lender_id);
CREATE INDEX idx_lender_preference_active    ON lender_preference(is_active);