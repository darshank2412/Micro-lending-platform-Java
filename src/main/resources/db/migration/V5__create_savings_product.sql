CREATE TABLE savings_product (
                                 id            BIGSERIAL      PRIMARY KEY,
                                 name          VARCHAR(200)   NOT NULL,
                                 min_balance   NUMERIC(15,2)  NOT NULL,
                                 max_balance   NUMERIC(15,2)  NOT NULL,
                                 interest_rate NUMERIC(5,2)   NOT NULL,
                                 status        VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE'
                                     CHECK (status IN ('ACTIVE','INACTIVE')),
                  r               created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT chk_savings_balance_range CHECK (min_balance < max_balance),
                                 CONSTRAINT chk_savings_interest      CHECK (interest_rate >= 0 AND interest_rate <= 20)
);

CREATE INDEX idx_savings_product_status ON savings_product(status);