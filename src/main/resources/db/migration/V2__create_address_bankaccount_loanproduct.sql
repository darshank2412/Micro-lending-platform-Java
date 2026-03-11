-- V2__create_address_bankaccount_loanproduct.sql

CREATE TABLE address (
                         id      BIGSERIAL    PRIMARY KEY,
                         line1   VARCHAR(255) NOT NULL,
                         city    VARCHAR(100) NOT NULL,
                         state   VARCHAR(100) NOT NULL,
                         pincode VARCHAR(10)  NOT NULL
);

ALTER TABLE users
    ADD CONSTRAINT fk_users_address FOREIGN KEY (address_id) REFERENCES address(id);

CREATE TABLE bank_account (
                              id             BIGSERIAL      PRIMARY KEY,
                              account_number VARCHAR(20)    NOT NULL,
                              balance        NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
                              status         VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
                              user_id        BIGINT         NOT NULL,
                              created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT uq_bank_account_number  UNIQUE (account_number),
                              CONSTRAINT fk_bank_account_user    FOREIGN KEY (user_id) REFERENCES users(id),
                              CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_bank_account_user ON bank_account(user_id);

CREATE TABLE loan_product (
                              id           BIGSERIAL      PRIMARY KEY,
                              name         VARCHAR(200)   NOT NULL,
                              min_amount   NUMERIC(15,2)  NOT NULL,
                              max_amount   NUMERIC(15,2)  NOT NULL,
                              min_interest NUMERIC(5,2)   NOT NULL,
                              max_interest NUMERIC(5,2)   NOT NULL,
                              min_tenure   INTEGER        NOT NULL,
                              max_tenure   INTEGER        NOT NULL,
                              active       BOOLEAN        NOT NULL DEFAULT TRUE,
                              created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT chk_amount_range    CHECK (min_amount   < max_amount),
                              CONSTRAINT chk_interest_range  CHECK (min_interest < max_interest),
                              CONSTRAINT chk_tenure_range    CHECK (min_tenure   < max_tenure),
                              CONSTRAINT chk_min_amount_pos  CHECK (min_amount   > 0),
                              CONSTRAINT chk_min_interest_nn CHECK (min_interest >= 0),
                              CONSTRAINT chk_min_tenure_pos  CHECK (min_tenure   >= 1),
                              CONSTRAINT chk_max_tenure_lim  CHECK (max_tenure   <= 360)
);

CREATE INDEX idx_loan_product_active ON loan_product(active);
