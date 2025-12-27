CREATE TABLE households (
                            id UUID PRIMARY KEY,
                            name VARCHAR(80) NOT NULL,
                            created_at TIMESTAMP NOT NULL
);

CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       household_id UUID NOT NULL,
                       email VARCHAR(190) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       display_name VARCHAR(80) NOT NULL,
                       role VARCHAR(20) NOT NULL,
                       created_at TIMESTAMP NOT NULL,
                       CONSTRAINT fk_user_household
                           FOREIGN KEY (household_id)
                               REFERENCES households(id),
                       CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE TABLE categories (
                            id UUID PRIMARY KEY,
                            household_id UUID NOT NULL,
                            name VARCHAR(80) NOT NULL,
                            color VARCHAR(20),
                            icon VARCHAR(40),
                            created_at TIMESTAMP NOT NULL,
                            CONSTRAINT fk_category_household
                                FOREIGN KEY (household_id)
                                    REFERENCES households(id),
                            CONSTRAINT uq_category_household_name
                                UNIQUE (household_id, name)
);

CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          household_id UUID NOT NULL,
                          name VARCHAR(80) NOT NULL,
                          type VARCHAR(20) NOT NULL,
                          created_at TIMESTAMP NOT NULL,
                          CONSTRAINT fk_account_household
                              FOREIGN KEY (household_id)
                                  REFERENCES households(id),
                          CONSTRAINT uq_account_household_name
                              UNIQUE (household_id, name)
);

CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              household_id UUID NOT NULL,
                              created_by UUID,
                              tx_date DATE NOT NULL,
                              type VARCHAR(10) NOT NULL,
                              amount_cents BIGINT NOT NULL,
                              category_id UUID NOT NULL,
                              account_id UUID NOT NULL,
                              merchant VARCHAR(120),
                              note TEXT,
                              created_at TIMESTAMP NOT NULL,
                              updated_at TIMESTAMP,

                              CONSTRAINT fk_tx_household
                                  FOREIGN KEY (household_id)
                                      REFERENCES households(id),

                              CONSTRAINT fk_tx_user
                                  FOREIGN KEY (created_by)
                                      REFERENCES users(id),

                              CONSTRAINT fk_tx_category
                                  FOREIGN KEY (category_id)
                                      REFERENCES categories(id),

                              CONSTRAINT fk_tx_account
                                  FOREIGN KEY (account_id)
                                      REFERENCES accounts(id)
);

CREATE INDEX idx_tx_household_date
    ON transactions(household_id, tx_date);

CREATE INDEX idx_tx_category
    ON transactions(category_id);

CREATE INDEX idx_tx_account
    ON transactions(account_id);
