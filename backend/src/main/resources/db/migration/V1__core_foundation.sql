-- =====================================================================
-- FixFlow V1 - Core foundation
-- Shops (tenants), users, RBAC, refresh tokens, audit trail.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Keeps updated_at honest even for SQL issued outside the JPA layer
-- (Flyway data fixes, bulk imports, admin scripts).
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- shops : the tenant boundary. Every business row carries a shop_id.
-- ---------------------------------------------------------------------
CREATE TABLE shops
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name                VARCHAR(160) NOT NULL,
    legal_name          VARCHAR(200),
    phone               VARCHAR(32),
    email               VARCHAR(255),
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(255),
    city                VARCHAR(120),
    state               VARCHAR(120),
    postal_code         VARCHAR(20),
    country             VARCHAR(80)          DEFAULT 'India',
    gst_number          VARCHAR(20),
    currency_code       VARCHAR(3)   NOT NULL DEFAULT 'INR',
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    logo_url            TEXT,
    invoice_prefix      VARCHAR(12)  NOT NULL DEFAULT 'INV',
    invoice_next_number BIGINT       NOT NULL DEFAULT 1,
    settings            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID
);

CREATE TRIGGER trg_shops_updated_at
    BEFORE UPDATE ON shops
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- permissions : fine-grained capability codes, e.g. INVENTORY_WRITE
-- ---------------------------------------------------------------------
CREATE TABLE permissions
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    code        VARCHAR(64)  NOT NULL UNIQUE,
    resource    VARCHAR(40)  NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_permissions_resource ON permissions (resource);

-- ---------------------------------------------------------------------
-- roles : system roles have shop_id NULL and are shared by all tenants;
--         a shop may later define custom roles scoped to itself.
-- ---------------------------------------------------------------------
CREATE TABLE roles
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id     UUID REFERENCES shops (id) ON DELETE CASCADE,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Lower number = more senior; used to stop a user granting a role above their own.
    seniority   INT          NOT NULL DEFAULT 100,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Two partial indexes because NULL shop_id would defeat a plain UNIQUE.
CREATE UNIQUE INDEX uq_roles_system_code ON roles (code) WHERE shop_id IS NULL;
CREATE UNIQUE INDEX uq_roles_shop_code ON roles (shop_id, code) WHERE shop_id IS NOT NULL;

CREATE TRIGGER trg_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE role_permissions
(
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id        UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    full_name      VARCHAR(160) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(32),
    password_hash  VARCHAR(255) NOT NULL,
    avatar_url     TEXT,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_pw BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMPTZ,
    failed_logins  INT          NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID
);

-- Login is by email, so it must be unique platform-wide, not per shop.
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE INDEX idx_users_shop ON users (shop_id);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_roles
(
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ---------------------------------------------------------------------
-- refresh_tokens : rotating refresh tokens, stored hashed.
-- device_id lets the offline Android client keep its own session.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(88)  NOT NULL UNIQUE,
    device_id   VARCHAR(120),
    user_agent  VARCHAR(400),
    ip_address  VARCHAR(64),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens (expires_at) WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- audit_logs : "Abhishek changed iPhone 11 Display price 4300 -> 4500"
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs
(
    id          UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    shop_id     UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_name  VARCHAR(160),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   UUID,
    summary     TEXT        NOT NULL,
    before_data JSONB,
    after_data  JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_shop_time ON audit_logs (shop_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- ---------------------------------------------------------------------
-- Reference data: permission catalogue + the five system roles.
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description)
VALUES ('INVENTORY_READ', 'INVENTORY', 'READ', 'View products, variants and stock levels'),
       ('INVENTORY_WRITE', 'INVENTORY', 'WRITE', 'Create and edit products, variants and stock'),
       ('INVENTORY_ADJUST', 'INVENTORY', 'ADJUST', 'Adjust, damage or write off stock'),
       ('CATALOG_READ', 'CATALOG', 'READ', 'View devices, aliases and compatibility groups'),
       ('CATALOG_WRITE', 'CATALOG', 'WRITE', 'Manage devices, aliases and compatibility groups'),
       ('SALES_READ', 'SALES', 'READ', 'View sales and invoices'),
       ('SALES_WRITE', 'SALES', 'WRITE', 'Create sales and take payments'),
       ('SALES_VOID', 'SALES', 'VOID', 'Cancel or refund a completed sale'),
       ('PURCHASE_READ', 'PURCHASE', 'READ', 'View purchases and supplier bills'),
       ('PURCHASE_WRITE', 'PURCHASE', 'WRITE', 'Create and receive purchases'),
       ('REPAIR_READ', 'REPAIR', 'READ', 'View repair jobs'),
       ('REPAIR_WRITE', 'REPAIR', 'WRITE', 'Create and update repair jobs'),
       ('CUSTOMER_READ', 'CUSTOMER', 'READ', 'View customers'),
       ('CUSTOMER_WRITE', 'CUSTOMER', 'WRITE', 'Create and edit customers'),
       ('SUPPLIER_READ', 'SUPPLIER', 'READ', 'View suppliers'),
       ('SUPPLIER_WRITE', 'SUPPLIER', 'WRITE', 'Create and edit suppliers'),
       ('PRICING_READ', 'PRICING', 'READ', 'View cost prices and pricing rules'),
       ('PRICING_WRITE', 'PRICING', 'WRITE', 'Change prices and pricing rules'),
       ('REPORT_READ', 'REPORT', 'READ', 'View reports and analytics'),
       ('USER_READ', 'USER', 'READ', 'View staff accounts'),
       ('USER_WRITE', 'USER', 'WRITE', 'Invite, edit and deactivate staff accounts'),
       ('SETTINGS_READ', 'SETTINGS', 'READ', 'View shop settings'),
       ('SETTINGS_WRITE', 'SETTINGS', 'WRITE', 'Change shop settings'),
       ('AUDIT_READ', 'AUDIT', 'READ', 'View the audit trail');

INSERT INTO roles (code, name, description, system_role, seniority)
VALUES ('OWNER', 'Owner', 'Full access to everything in the shop', TRUE, 10),
       ('MANAGER', 'Manager', 'Runs day-to-day operations; no user or settings control', TRUE, 20),
       ('TECHNICIAN', 'Technician', 'Handles repair jobs and consumes parts', TRUE, 30),
       ('STAFF', 'Staff', 'Counter sales and stock entry', TRUE, 40),
       ('VIEWER', 'Viewer', 'Read-only access, no cost prices', TRUE, 50);

-- OWNER: everything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.code = 'OWNER'
  AND r.shop_id IS NULL;

-- MANAGER: everything except user administration and settings changes.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.code = 'MANAGER'
  AND r.shop_id IS NULL
  AND p.code NOT IN ('USER_WRITE', 'SETTINGS_WRITE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN
                               ('INVENTORY_READ', 'CATALOG_READ', 'REPAIR_READ', 'REPAIR_WRITE',
                                'CUSTOMER_READ', 'CUSTOMER_WRITE', 'SALES_READ')
WHERE r.code = 'TECHNICIAN'
  AND r.shop_id IS NULL;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN
                               ('INVENTORY_READ', 'INVENTORY_WRITE', 'CATALOG_READ',
                                'SALES_READ', 'SALES_WRITE', 'CUSTOMER_READ', 'CUSTOMER_WRITE',
                                'REPAIR_READ', 'REPAIR_WRITE', 'SUPPLIER_READ', 'PURCHASE_READ')
WHERE r.code = 'STAFF'
  AND r.shop_id IS NULL;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN
                               ('INVENTORY_READ', 'CATALOG_READ', 'SALES_READ', 'REPAIR_READ',
                                'CUSTOMER_READ', 'SUPPLIER_READ', 'PURCHASE_READ', 'REPORT_READ')
WHERE r.code = 'VIEWER'
  AND r.shop_id IS NULL;
