-- =====================================================================
-- FixFlow V6 - Invites, auth extras, notifications, billing, flags,
-- compatibility history, import jobs.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users
    ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shops
    ADD COLUMN require_compatibility_approval BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- Time-limited tokens: password reset, email verify, phone OTP
-- ---------------------------------------------------------------------
CREATE TABLE user_tokens
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users (id) ON DELETE CASCADE,
    phone      VARCHAR(32),
    email      VARCHAR(255),
    token_type VARCHAR(20)  NOT NULL,
    token_hash VARCHAR(88)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_tokens_type CHECK (token_type IN ('PASSWORD_RESET', 'EMAIL_VERIFY', 'PHONE_OTP'))
);

CREATE INDEX idx_user_tokens_hash ON user_tokens (token_type, token_hash);
CREATE INDEX idx_user_tokens_user ON user_tokens (user_id, token_type);

-- ---------------------------------------------------------------------
-- Invitations (secure token, not the row id)
-- ---------------------------------------------------------------------
CREATE TABLE workspace_invitations
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    email        VARCHAR(255),
    phone        VARCHAR(32),
    role_id      UUID         NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    token_hash   VARCHAR(88)  NOT NULL UNIQUE,
    raw_hint     VARCHAR(12)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    invited_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    accepted_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_invitations_workspace ON workspace_invitations (workspace_id, status);
CREATE TRIGGER trg_invitations_updated_at
    BEFORE UPDATE ON workspace_invitations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------
CREATE TABLE notification_preferences
(
    id         UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    email      BOOLEAN     NOT NULL DEFAULT TRUE,
    whatsapp   BOOLEAN     NOT NULL DEFAULT FALSE,
    push       BOOLEAN     NOT NULL DEFAULT TRUE,
    sms        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notif_pref UNIQUE (user_id, event_type)
);

CREATE TABLE notification_outbox
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id      UUID REFERENCES shops (id) ON DELETE CASCADE,
    user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    event_type   VARCHAR(40)  NOT NULL,
    channel      VARCHAR(20)  NOT NULL,
    recipient    VARCHAR(255),
    subject      VARCHAR(255),
    body         TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    provider     VARCHAR(40),
    provider_ref VARCHAR(80),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    sent_at      TIMESTAMPTZ,
    CONSTRAINT ck_outbox_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'PUSH', 'LOG')),
    CONSTRAINT ck_outbox_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_outbox_shop ON notification_outbox (shop_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Billing (prices live in the database, never hard-coded)
-- ---------------------------------------------------------------------
CREATE TABLE billing_plans
(
    id          UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    code        VARCHAR(40) NOT NULL UNIQUE,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE billing_prices
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    plan_id    UUID          NOT NULL REFERENCES billing_plans (id) ON DELETE CASCADE,
    code       VARCHAR(40)   NOT NULL UNIQUE,
    amount     NUMERIC(14, 2) NOT NULL,
    currency   VARCHAR(3)    NOT NULL DEFAULT 'INR',
    interval   VARCHAR(20)   NOT NULL,
    entitlement VARCHAR(40)  NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_price_interval CHECK (interval IN ('ONE_TIME', 'MONTHLY', 'ANNUAL'))
);

CREATE TABLE billing_orders
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    workspace_id     UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    price_code       VARCHAR(40)   NOT NULL,
    purpose          VARCHAR(80)   NOT NULL,
    amount           NUMERIC(14, 2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status           VARCHAR(24)   NOT NULL,
    gateway          VARCHAR(40)   NOT NULL,
    gateway_order_id VARCHAR(80),
    gateway_payment_id VARCHAR(80),
    entitlement_code VARCHAR(40)   NOT NULL,
    idempotency_key  VARCHAR(80),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_billing_status CHECK (status IN
                                        ('CREATED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED',
                                         'REFUNDED', 'PARTIALLY_REFUNDED', 'EXPIRED')),
    CONSTRAINT uq_billing_idempotency UNIQUE (workspace_id, idempotency_key)
);

CREATE INDEX idx_billing_orders_ws ON billing_orders (workspace_id, created_at DESC);
CREATE TRIGGER trg_billing_orders_updated_at
    BEFORE UPDATE ON billing_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE workspace_entitlements
(
    id           UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    code         VARCHAR(40) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    source_order_id UUID REFERENCES billing_orders (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_entitlement UNIQUE (workspace_id, code)
);

CREATE TABLE billing_webhook_events
(
    id            UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    provider      VARCHAR(40) NOT NULL,
    event_id      VARCHAR(80) NOT NULL,
    payload       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    processed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_event UNIQUE (provider, event_id)
);

INSERT INTO billing_plans (code, name, description)
VALUES ('PILOT', 'Pilot', 'Initial one-time activation and member add-ons');

INSERT INTO billing_prices (plan_id, code, amount, currency, interval, entitlement)
SELECT id, 'WORKSPACE_ACTIVATION', 50, 'INR', 'ONE_TIME', 'WORKSPACE_CREATE'
FROM billing_plans
WHERE code = 'PILOT';

INSERT INTO billing_prices (plan_id, code, amount, currency, interval, entitlement)
SELECT id, 'MEMBER_ADD', 50, 'INR', 'ONE_TIME', 'MEMBER_ADD'
FROM billing_plans
WHERE code = 'PILOT';

-- Existing shops keep working: grant the operational entitlements.
INSERT INTO workspace_entitlements (workspace_id, code)
SELECT s.id, e.code
FROM shops s
         CROSS JOIN (VALUES ('WORKSPACE_CREATE'),
                            ('MEMBER_ADD'),
                            ('INVENTORY'),
                            ('SALES'),
                            ('REPAIRS'),
                            ('MULTI_USER')) AS e(code);

-- ---------------------------------------------------------------------
-- Feature flags
-- ---------------------------------------------------------------------
CREATE TABLE feature_flags
(
    id      UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    shop_id UUID REFERENCES shops (id) ON DELETE CASCADE,
    code    VARCHAR(40) NOT NULL,
    enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_feature_flag UNIQUE (shop_id, code)
);

CREATE UNIQUE INDEX uq_feature_flag_global ON feature_flags (code) WHERE shop_id IS NULL;

INSERT INTO feature_flags (shop_id, code, enabled)
VALUES (NULL, 'WHATSAPP_ENABLED', FALSE),
       (NULL, 'ADVANCED_ANALYTICS', TRUE),
       (NULL, 'COMPATIBILITY_APPROVAL', FALSE),
       (NULL, 'MULTI_LOCATION', FALSE),
       (NULL, 'PREMIUM_REPORTS', TRUE);

-- ---------------------------------------------------------------------
-- Compatibility collaboration
-- ---------------------------------------------------------------------
CREATE TABLE compatibility_history
(
    id         UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    shop_id    UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    group_id   UUID        NOT NULL REFERENCES compatibility_groups (id) ON DELETE CASCADE,
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_name VARCHAR(160),
    summary    TEXT        NOT NULL,
    reason     VARCHAR(255),
    before_data JSONB,
    after_data  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_compat_history_group ON compatibility_history (shop_id, group_id, created_at DESC);

CREATE TABLE compatibility_change_requests
(
    id           UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    shop_id      UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    group_id     UUID        NOT NULL REFERENCES compatibility_groups (id) ON DELETE CASCADE,
    action       VARCHAR(20) NOT NULL,
    device_id    UUID REFERENCES device_models (id) ON DELETE CASCADE,
    reason       VARCHAR(255),
    status       VARCHAR(20) NOT NULL,
    requested_by UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at  TIMESTAMPTZ,
    CONSTRAINT ck_ccr_action CHECK (action IN ('ADD_DEVICE', 'REMOVE_DEVICE')),
    CONSTRAINT ck_ccr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_ccr_shop_status ON compatibility_change_requests (shop_id, status);

-- ---------------------------------------------------------------------
-- Import jobs + extra permissions
-- ---------------------------------------------------------------------
CREATE TABLE import_jobs
(
    id           UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    shop_id      UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    kind         VARCHAR(40) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    source_name  VARCHAR(160),
    result_json  JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    CONSTRAINT ck_import_status CHECK (status IN ('REVIEW', 'IMPORTED', 'FAILED'))
);

INSERT INTO permissions (code, resource, action, description)
VALUES ('USER_INVITE', 'user', 'invite', 'Invite people into a workspace'),
       ('WORKSPACE_BILLING', 'workspace', 'billing', 'View and pay workspace bills'),
       ('REPORT_EXPORT', 'report', 'export', 'Export reports'),
       ('COMPATIBILITY_APPROVE', 'compatibility', 'approve', 'Approve compatibility changes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.shop_id IS NULL
  AND r.code IN ('OWNER', 'ADMIN')
  AND p.code IN ('USER_INVITE', 'WORKSPACE_BILLING', 'REPORT_EXPORT', 'COMPATIBILITY_APPROVE')
  AND NOT EXISTS (SELECT 1
                  FROM role_permissions rp
                  WHERE rp.role_id = r.id
                    AND rp.permission_id = p.id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.shop_id IS NULL
  AND r.code = 'MANAGER'
  AND p.code IN ('USER_INVITE', 'REPORT_EXPORT', 'COMPATIBILITY_APPROVE')
  AND NOT EXISTS (SELECT 1
                  FROM role_permissions rp
                  WHERE rp.role_id = r.id
                    AND rp.permission_id = p.id);
