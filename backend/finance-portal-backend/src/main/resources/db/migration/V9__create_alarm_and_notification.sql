-- V9: Alarm + bildirim (notification) tabloları.
-- Alarm: kullanıcının bir enstrüman için kurduğu fiyat/değişim/hacim koşulu.
--        Periyodik değerlendirilir; koşul sağlanınca bildirim üretir + e-posta gönderir.
-- Notification: kullanıcıya gösterilecek uygulama-içi bildirim + gönderilen e-posta kaydı.

CREATE TABLE alarm (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             VARCHAR(100) NOT NULL,
    recipient_email     VARCHAR(255),
    asset_type          VARCHAR(20)  NOT NULL,
    symbol              VARCHAR(120) NOT NULL,
    instrument_name     VARCHAR(255),
    metric              VARCHAR(20)  NOT NULL,           -- PRICE | CHANGE_PERCENT | VOLUME
    direction           VARCHAR(10)  NOT NULL,           -- ABOVE | BELOW
    threshold           NUMERIC(24,8) NOT NULL,
    frequency           VARCHAR(20)  NOT NULL DEFAULT 'ONCE',  -- ONCE | RECURRING
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | TRIGGERED | DISABLED
    last_observed_value NUMERIC(24,8),
    note                VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    triggered_at        TIMESTAMP,
    last_triggered_at   TIMESTAMP
);

CREATE INDEX idx_alarm_user_id ON alarm(user_id);
-- Değerlendirme döngüsü aktif alarmları statüye göre çeker
CREATE INDEX idx_alarm_status ON alarm(status);

CREATE TABLE notification (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(100) NOT NULL,
    type             VARCHAR(30)  NOT NULL DEFAULT 'ALARM',  -- ALARM (ileride genişleyebilir)
    title            VARCHAR(255) NOT NULL,
    body             TEXT         NOT NULL,
    related_alarm_id UUID         REFERENCES alarm(id) ON DELETE SET NULL,
    is_read          BOOLEAN      NOT NULL DEFAULT FALSE,
    recipient_email  VARCHAR(255),
    email_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | FAILED | SKIPPED
    email_error      TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at          TIMESTAMP
);

CREATE INDEX idx_notification_user_id ON notification(user_id);
-- Okunmamış bildirim sayısı/listesi için
CREATE INDEX idx_notification_user_unread ON notification(user_id, is_read);
