ALTER TABLE watchlist_item
    ADD COLUMN start_price NUMERIC(19,6),
    ADD COLUMN start_currency VARCHAR(10);

