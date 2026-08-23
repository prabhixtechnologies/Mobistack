-- In-app inbox, push devices, support chat, app-release policy, device caps.

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS max_devices_per_user INTEGER NOT NULL DEFAULT 3;

CREATE TABLE inbox_notifications
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id     UUID REFERENCES shops (id) ON DELETE SET NULL,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type  VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    link        VARCHAR(400),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID
);

CREATE INDEX idx_inbox_user_created ON inbox_notifications (user_id, created_at DESC);
CREATE INDEX idx_inbox_user_unread ON inbox_notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE push_devices
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shop_id         UUID REFERENCES shops (id) ON DELETE SET NULL,
    device_id       VARCHAR(80)  NOT NULL,
    platform        VARCHAR(20)  NOT NULL,
    expo_push_token VARCHAR(240),
    app_version     VARCHAR(40),
    native_build    INTEGER,
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    UNIQUE (user_id, device_id)
);

CREATE INDEX idx_push_devices_token ON push_devices (expo_push_token) WHERE expo_push_token IS NOT NULL;

CREATE TABLE support_conversations
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id         UUID REFERENCES shops (id) ON DELETE SET NULL,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject         VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    channel         VARCHAR(20)  NOT NULL DEFAULT 'WEB',
    assigned_to     UUID REFERENCES users (id) ON DELETE SET NULL,
    last_message_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX idx_support_user ON support_conversations (user_id, last_message_at DESC);
CREATE INDEX idx_support_status ON support_conversations (status, last_message_at DESC);

CREATE TABLE support_messages
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    conversation_id UUID         NOT NULL REFERENCES support_conversations (id) ON DELETE CASCADE,
    author_type     VARCHAR(12)  NOT NULL,
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    body            TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_support_messages_convo ON support_messages (conversation_id, created_at);

CREATE TABLE app_releases
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    platform            VARCHAR(20)  NOT NULL UNIQUE,
    min_native_build    INTEGER      NOT NULL DEFAULT 1,
    latest_native_build INTEGER      NOT NULL DEFAULT 1,
    ota_channel         VARCHAR(40)  NOT NULL DEFAULT 'production',
    ota_runtime_version VARCHAR(40),
    force_native_update BOOLEAN      NOT NULL DEFAULT FALSE,
    store_url           VARCHAR(400),
    notes               TEXT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_releases (platform, min_native_build, latest_native_build, ota_channel, store_url, notes)
VALUES ('ANDROID', 1, 1, 'production', 'https://mobistack.prabhixtechnologies.com/app/android',
        'Install the latest Android build from MobiStack.'),
       ('IOS', 1, 1, 'production', 'https://mobistack.prabhixtechnologies.com/app/ios',
        'Install the latest iOS build from MobiStack.'),
       ('WEB', 1, 1, 'production', 'https://mobistack.prabhixtechnologies.com',
        'Refresh the browser for the latest console.');
