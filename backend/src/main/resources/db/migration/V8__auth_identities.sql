ALTER TABLE user_tokens
    DROP CONSTRAINT IF EXISTS ck_user_tokens_type;
ALTER TABLE user_tokens
    ADD CONSTRAINT ck_user_tokens_type CHECK (token_type IN (
        'PASSWORD_RESET', 'EMAIL_VERIFY', 'PHONE_OTP',
        'EMAIL_OTP', 'WHATSAPP_OTP', 'MAGIC_LINK'
    ));

CREATE TABLE user_identities
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   VARCHAR(40)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_identity UNIQUE (provider, subject)
);

CREATE INDEX idx_user_identities_user ON user_identities (user_id);
