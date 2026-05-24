-- Admin'in ban verirken yazdığı sebep (neden banlandığını sonradan görebilmek için).
-- Mevcut kayıtlarda NULL kalır; opsiyonel alan.
ALTER TABLE user_ban_state ADD COLUMN ban_reason VARCHAR(512);
