-- Tier 1 BOND: COUPON_INCOME transaction_type 'COUPON_INCOME' string'i 13 karakter,
-- mevcut VARCHAR(10) ile sığmıyor → kolonu genişlet ve check kısıtını güncelle.
ALTER TABLE portfolio_transaction
    DROP CONSTRAINT IF EXISTS portfolio_transaction_transaction_type_check;

ALTER TABLE portfolio_transaction
    ALTER COLUMN transaction_type TYPE VARCHAR(20);

ALTER TABLE portfolio_transaction
    ADD CONSTRAINT portfolio_transaction_transaction_type_check
    CHECK (transaction_type IN ('BUY', 'SELL', 'COUPON_INCOME'));
