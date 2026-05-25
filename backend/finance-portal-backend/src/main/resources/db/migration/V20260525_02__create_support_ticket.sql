-- Kullanıcı destek talepleri (profil → "Bir problem mi yaşıyorsunuz?"). Durum: OPEN/IN_PROGRESS/RESOLVED.
CREATE TABLE IF NOT EXISTS support_ticket (
    id         UUID         NOT NULL,
    user_id    VARCHAR(100) NOT NULL,
    user_email VARCHAR(255),
    subject    VARCHAR(200) NOT NULL,
    message    TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    admin_note TEXT,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_support_ticket PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_user ON support_ticket (user_id);
CREATE INDEX IF NOT EXISTS idx_support_ticket_status ON support_ticket (status);
