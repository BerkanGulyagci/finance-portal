-- Kullanıcı bazında tek VİOP teminat-uyarı eşiği (0–100, 0 = kapalı, varsayılan 50).
-- Eski per-alarm MARGIN_RATIO yaklaşımı yerini kullanıcı profil ayarına bıraktı: tüm açık FUTURE
-- pozisyonlar bu eşik altına düşerse MARGIN_CALL bildirimi + e-posta üretilir.
-- Eski MARGIN_RATIO alarm satırları silinmez (enum korunur), ancak yeni UI'da gizlidir ve
-- evaluator'da değerlendirilmez (AlarmEvaluationService zaten metric=MARGIN_RATIO erken döner).

CREATE TABLE user_margin_alert_setting (
    user_id                    VARCHAR(100) PRIMARY KEY,
    margin_alert_threshold_pct INT          NOT NULL DEFAULT 50,
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_margin_alert_threshold_range
        CHECK (margin_alert_threshold_pct BETWEEN 0 AND 100)
);
