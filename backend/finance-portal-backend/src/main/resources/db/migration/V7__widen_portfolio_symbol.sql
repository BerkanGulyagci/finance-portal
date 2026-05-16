-- VIOP ve uzun enstrüman adları (örn. "SASA (25 May 26) Vadeli FIZ.") VARCHAR(20)'ye sığmıyordu;
-- INSERT 500 hatasına ve işlem eklenememesine yol açıyordu.

ALTER TABLE portfolio_transaction
    ALTER COLUMN symbol TYPE VARCHAR(255);

ALTER TABLE watchlist_item
    ALTER COLUMN symbol TYPE VARCHAR(255);
