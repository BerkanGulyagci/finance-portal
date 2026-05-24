-- V10: Bülten (dashboard özeti e-postası) aboneliği.
-- Kullanıcı seçtiği sıklıkta (DAILY/WEEKLY/MONTHLY) portföy + piyasa özetini e-posta ile alır.
-- Her kullanıcı için en fazla bir kayıt (user_id benzersiz).

CREATE TABLE newsletter_subscription (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            VARCHAR(100) NOT NULL UNIQUE,
    email              VARCHAR(255),
    frequency          VARCHAR(20)  NOT NULL DEFAULT 'WEEKLY',  -- DAILY | WEEKLY | MONTHLY
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    unsubscribe_token  VARCHAR(64)  NOT NULL UNIQUE,
    last_sent_at       TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Digest döngüsü aktif abonelikleri çeker
CREATE INDEX idx_newsletter_enabled ON newsletter_subscription(enabled);
