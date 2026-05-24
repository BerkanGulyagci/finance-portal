-- Ban yüzünden pasifleştirilen alarm/aboneliği unban'da SADECE bunlara dokunarak geri açabilmek için işaret.
-- Kullanıcının ban'dan ÖNCE kendi elleriyle kapattıkları (false kalır) unban'da açılmaz.
ALTER TABLE alarm ADD COLUMN disabled_by_ban BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE newsletter_subscription ADD COLUMN disabled_by_ban BOOLEAN NOT NULL DEFAULT FALSE;
