-- VİOP pozisyon yönü: LONG (varsayılan) veya SHORT.
-- Yalnızca FUTURE assetType için doldurulur; diğer asset tiplerinde NULL kalır.
-- Eski transaction'lar geriye uyumluluk için NULL → LONG sayılır (uygulama katmanı).

ALTER TABLE portfolio_transaction
    ADD COLUMN direction VARCHAR(10);

-- Mevcut FUTURE transaction'larını LONG olarak işaretle (eski davranış).
UPDATE portfolio_transaction
SET direction = 'LONG'
WHERE asset_type = 'FUTURE' AND direction IS NULL;
