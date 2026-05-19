CREATE TABLE user_ban_state (
    keycloak_user_id VARCHAR(36) PRIMARY KEY,
    permanent        BOOLEAN NOT NULL,
    ban_until        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_ban_state_temporary_expiry
    ON user_ban_state (ban_until)
    WHERE permanent = FALSE AND ban_until IS NOT NULL;
