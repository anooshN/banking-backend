CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(50) UNIQUE NOT NULL,
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'USD',
    balance_before NUMERIC(19,4),
    balance_after NUMERIC(19,4),
    counterparty_account VARCHAR(50),
    counterparty_name VARCHAR(255),
    description TEXT,
    payment_rail VARCHAR(20),
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX idx_transactions_reference ON transactions(reference_number);
CREATE INDEX idx_transactions_status ON transactions(status);
