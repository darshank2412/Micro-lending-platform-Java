CREATE TABLE loan_summary (
                              id BIGSERIAL PRIMARY KEY,

                              loan_offer_id BIGINT NOT NULL UNIQUE,
                              borrower_id   BIGINT NOT NULL,
                              lender_id     BIGINT NOT NULL,

                              principal_amount       NUMERIC(15,2) NOT NULL,
                              interest_rate          NUMERIC(5,2)  NOT NULL,
                              tenure_months          INT           NOT NULL,

                              emi_amount             NUMERIC(15,2) NOT NULL,
                              total_repayment_amount NUMERIC(15,2) NOT NULL,
                              total_interest_amount  NUMERIC(15,2) NOT NULL,
                              outstanding_principal  NUMERIC(15,2) NOT NULL,

                              disbursement_date DATE NOT NULL,
                              first_emi_date    DATE NOT NULL,
                              last_emi_date     DATE NOT NULL,

                              emis_paid      INT NOT NULL DEFAULT 0,
                              emis_remaining INT NOT NULL,

                              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                              created_at TIMESTAMP,
                              updated_at TIMESTAMP,

                              CONSTRAINT fk_loan_summary_offer
                                  FOREIGN KEY (loan_offer_id)
                                      REFERENCES loan_offer(id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_loan_summary_borrower
                                  FOREIGN KEY (borrower_id)
                                      REFERENCES users(id),

                              CONSTRAINT fk_loan_summary_lender
                                  FOREIGN KEY (lender_id)
                                      REFERENCES users(id)
);