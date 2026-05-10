-- V5: watchlist_item tablosu.
-- Fiyat bilgisi tutulmaz; sadece kullanıcının takip ettiği semboller saklanır.
-- Fiyat verileri (son fiyat, açılış, yüksek, düşük, fark, hacim vb.)
-- backend response hazırlanırken mevcut market servislerinden anlık olarak çekilir.

CREATE TABLE watchlist_item (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID        NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    symbol       VARCHAR(20) NOT NULL,
    asset_type   VARCHAR(20) NOT NULL,
    notes        TEXT,
    added_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_watchlist_portfolio_symbol_asset
        UNIQUE (portfolio_id, symbol, asset_type)
);

-- portfolio_id üzerinde index (portföye ait tüm item'ları çekmek için)
CREATE INDEX idx_watchlist_portfolio_id
    ON watchlist_item(portfolio_id);

-- portfolio_id + asset_type + symbol bileşik index
-- (tipe göre filtreleme ve tekrar kontrolü için)
CREATE INDEX idx_watchlist_portfolio_asset_symbol
    ON watchlist_item(portfolio_id, asset_type, symbol);
