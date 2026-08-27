-- ---------------------------------------------------------------------
-- Push delivery was fire-and-forget: the server posted to Expo, ignored the
-- reply, and left every PUSH outbox row QUEUED forever. A phone that had been
-- wiped, or whose token Expo had invalidated, was retried on every alert and
-- nobody could tell that delivery had stopped.
--
-- Tokens now carry their last delivery error and a retirement stamp, so a dead
-- registration is dropped after Expo reports it rather than retried for ever.
-- ---------------------------------------------------------------------

ALTER TABLE push_devices
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(200);

ALTER TABLE push_devices
    ADD COLUMN IF NOT EXISTS retired_at TIMESTAMPTZ;

-- Every alert looks up the live tokens for one person, so that lookup gets its
-- own partial index rather than scanning retired rows.
CREATE INDEX IF NOT EXISTS idx_push_devices_live
    ON push_devices (user_id)
    WHERE expo_push_token IS NOT NULL AND retired_at IS NULL;
