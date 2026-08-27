-- ---------------------------------------------------------------------
-- Sessions are per device again.
--
-- V17 forced max_devices_per_user to 1, which collapsed two different
-- limits into one: a paid screen seat (a person) and a device (a screen
-- belonging to that person). The effect was that one owner could not use
-- the counter phone and the web console at the same time -- each sign-in,
-- and even each silent token refresh, ended the other session.
--
-- Screen seats still bill per person. This restores the documented
-- three-devices-per-person allowance, which owners may change in Settings.
-- ---------------------------------------------------------------------

ALTER TABLE shops
    ALTER COLUMN max_devices_per_user SET DEFAULT 3;

UPDATE shops
SET max_devices_per_user = 3
WHERE max_devices_per_user < 3;

-- Every authenticated request checks whether the calling device still holds
-- a live refresh token, so that lookup needs its own index.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_device_live
    ON refresh_tokens (user_id, device_id)
    WHERE revoked_at IS NULL;
