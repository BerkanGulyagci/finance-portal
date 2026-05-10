-- V4: portfolio tablosuna portfolio_type kolonu ekle.
-- Mevcut satırlar DEFAULT 'HOLDINGS' alır — veri kaybı olmaz.

ALTER TABLE portfolio
    ADD COLUMN portfolio_type VARCHAR(20) NOT NULL DEFAULT 'HOLDINGS';

ALTER TABLE portfolio
    ADD CONSTRAINT chk_portfolio_type
        CHECK (portfolio_type IN ('HOLDINGS', 'WATCHLIST'));

-- Kullanıcının portföylerini tipe göre filtrelemek için bileşik index
CREATE INDEX idx_portfolio_user_type ON portfolio(user_id, portfolio_type);
