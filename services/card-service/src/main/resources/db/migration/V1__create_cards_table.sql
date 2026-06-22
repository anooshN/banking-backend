CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE cards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL,
    user_id UUID NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    card_number_masked VARCHAR(25),
    card_number_hash VARCHAR(255),
    cardholder_name VARCHAR(255),
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_limit NUMERIC(19,4),
    monthly_limit NUMERIC(19,4),
    international_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    contactless_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    online_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pin_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cards_user_id ON cards(user_id);
CREATE INDEX idx_cards_account_id ON cards(account_id);
CREATE INDEX idx_cards_status ON cards(status);
