CREATE TABLE emi_schedule (
                              id              BIGSERIAL PRIMARY KEY,

                              loan_summary_id BIGINT NOT NULL,

                              emi_number           INT           NOT NULL,
                              due_date             DATE          NOT NULL,
                              paid_date            DATE,

                              emi_amount           NUMERIC(15,2) NOT NULL,
                              principal_component  NUMERIC(15,2) NOT NULL,
                              interest_component   NUMERIC(15,2) NOT NULL,
                              outstanding_principal NUMERIC(15,2) NOT NULL,

                              status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              updated_at TIMESTAMP,

                              CONSTRAINT fk_emi_loan_summary
                                  FOREIGN KEY (loan_summary_id)
                                      REFERENCES loan_summary(id)
                                      ON DELETE CASCADE
);
