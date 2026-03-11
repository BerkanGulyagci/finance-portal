CREATE TABLE portfolio_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    
    symbol VARCHAR(20) NOT NULL,
    asset_type VARCHAR(20) NOT NULL,  -- STOCK/FUND/FX/FUTURE/CRYPTO...
    
    transaction_type VARCHAR(10) NOT NULL
        CHECK (transaction_type IN ('BUY', 'SELL')),
    
    quantity NUMERIC(19,8) NOT NULL CHECK (quantity > 0),
    price NUMERIC(19,4) NOT NULL CHECK (price > 0),
    commission NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (commission >= 0),
    
    transaction_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tx_portfolio_id ON portfolio_transaction(portfolio_id);
CREATE INDEX idx_tx_portfolio_asset_symbol ON portfolio_transaction(portfolio_id, asset_type, symbol);
CREATE INDEX idx_tx_portfolio_date ON portfolio_transaction(portfolio_id, transaction_date DESC);
