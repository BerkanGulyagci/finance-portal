-- Kullanıcı arayüz tercihleri (dashboard düzeni, grafik kartları, dil, ticker, izleme/portföy grafik düzenleri…)
-- Cihazlar arası senkron için: anahtar-değer (value = JSON metni), kullanıcı+anahtar benzersiz.
CREATE TABLE IF NOT EXISTS user_preference (
    id         UUID         NOT NULL,
    user_id    VARCHAR(100) NOT NULL,
    pref_key   VARCHAR(255) NOT NULL,
    value      TEXT,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_user_preference PRIMARY KEY (id),
    CONSTRAINT uq_user_preference UNIQUE (user_id, pref_key)
);

CREATE INDEX IF NOT EXISTS idx_user_preference_user ON user_preference (user_id);
