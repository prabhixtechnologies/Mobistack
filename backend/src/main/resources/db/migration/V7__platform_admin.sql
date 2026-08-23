-- Platform operators (Phase 2H). Demo owner is promoted on existing DBs.
ALTER TABLE users
    ADD COLUMN system_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET system_admin = TRUE
WHERE lower(email) = 'owner@fixflow.app';
