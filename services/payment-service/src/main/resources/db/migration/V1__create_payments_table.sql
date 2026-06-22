CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_reference VARCHAR(50) UNIQUE NOT NULL,
    sender_account_id UUID NOT NULL,
    receiver_account_number VARCHAR(50) NOT NULL,
    receiver_bank_code VARCHAR(20),
    receiver_name VARCHAR(255),
    amount NUMERIC(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'USD',
    payment_rail VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    description TEXT,
    swift_message TEXT,
    correlation_id VARCHAR(100),
    outbox_processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payments_sender ON payments(sender_account_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_outbox ON payments(outbox_processed) WHERE outbox_processed = FALSE;
