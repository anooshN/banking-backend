CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    loan_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    principal_amount NUMERIC(19,4) NOT NULL,
    outstanding_balance NUMERIC(19,4) NOT NULL,
    interest_rate NUMERIC(5,4) NOT NULL,
    tenure_months INTEGER NOT NULL,
    emi_amount NUMERIC(19,4),
    disbursement_date DATE,
    maturity_date DATE,
    next_emi_date DATE,
    purpose VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_loans_user_id ON loans(user_id);
CREATE INDEX idx_loans_status ON loans(status);
CREATE INDEX idx_loans_next_emi ON loans(next_emi_date) WHERE status = 'ACTIVE';
