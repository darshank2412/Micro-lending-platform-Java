CREATE TABLE loan_offer (
                            id BIGSERIAL PRIMARY KEY,

                            loan_request_id BIGINT NOT NULL,
                            lender_id       BIGINT NOT NULL,
                            borrower_id     BIGINT NOT NULL,

                            offered_amount  NUMERIC(15,2) NOT NULL,
                            interest_rate   NUMERIC(5,2)  NOT NULL,
                            tenure_months   INT           NOT NULL,

                            status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

                            created_at TIMESTAMP,
                            updated_at TIMESTAMP,

                            CONSTRAINT fk_loan_offer_request
                                FOREIGN KEY (loan_request_id)
                                    REFERENCES loan_request(id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_loan_offer_lender
                                FOREIGN KEY (lender_id)
                                    REFERENCES users(id),

                            CONSTRAINT fk_loan_offer_borrower
                                FOREIGN KEY (borrower_id)
                                    REFERENCES users(id)
);