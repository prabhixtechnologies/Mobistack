-- ---------------------------------------------------------------------
-- One-time codes had no attempt limit.
--
-- A code is six digits and lives for ten minutes. The only thing standing in
-- the way of guessing it was a per-IP rate limit, which does nothing against a
-- caller spreading attempts across many addresses at a single phone number.
--
-- Codes now carry their own failure counter, and a code is burned once it has
-- been guessed at too many times. Requesting a new one is also throttled, so
-- the counter cannot simply be reset in a loop.
-- ---------------------------------------------------------------------

ALTER TABLE user_tokens
    ADD COLUMN IF NOT EXISTS attempts SMALLINT NOT NULL DEFAULT 0;

-- Both the attempt counter and the resend cooldown need the newest live code
-- for one destination, which is the lookup these indexes serve.
CREATE INDEX IF NOT EXISTS idx_user_tokens_live_email
    ON user_tokens (token_type, lower(email), created_at DESC)
    WHERE used_at IS NULL AND email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_tokens_live_phone
    ON user_tokens (token_type, phone, created_at DESC)
    WHERE used_at IS NULL AND phone IS NOT NULL;
