-- =====================================================================
-- FixFlow V4 - Workspaces and memberships (Phase 2A)
--
-- shops remains the workspace row. Same IDs, same FKs, no data loss.
-- Users become platform accounts; access goes through workspace_memberships.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Workspace identity on the existing shop row
-- ---------------------------------------------------------------------
ALTER TABLE shops
    ADD COLUMN join_code VARCHAR(16);

UPDATE shops
SET join_code = upper(left(regexp_replace(name, '[^A-Za-z0-9]', '', 'g'), 6))
                    || '-'
                    || upper(substr(replace(id::text, '-', ''), 1, 4))
WHERE join_code IS NULL;

ALTER TABLE shops
    ALTER COLUMN join_code SET NOT NULL;

CREATE UNIQUE INDEX uq_shops_join_code ON shops (join_code);

-- ---------------------------------------------------------------------
-- ADMIN sits between OWNER and MANAGER. Needed so a workspace can have
-- operators who are not the billing owner (Phase 2B will use this more).
-- ---------------------------------------------------------------------
INSERT INTO roles (code, name, description, system_role, seniority)
SELECT 'ADMIN',
       'Admin',
       'Runs the workspace; cannot delete it or change billing (Phase 2D)',
       TRUE,
       15
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE shop_id IS NULL AND code = 'ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
  AND r.shop_id IS NULL
  AND p.code NOT IN ('SETTINGS_WRITE')
  AND NOT EXISTS (SELECT 1
                  FROM role_permissions rp
                  WHERE rp.role_id = r.id
                    AND rp.permission_id = p.id);

-- ---------------------------------------------------------------------
-- Memberships
-- ---------------------------------------------------------------------
CREATE TABLE workspace_memberships
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id       UUID         NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    status        VARCHAR(20)  NOT NULL,
    invited_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    joined_at     TIMESTAMPTZ,
    last_selected_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ck_membership_status CHECK (status IN
                                           ('INVITED', 'PENDING', 'ACTIVE', 'SUSPENDED', 'REMOVED', 'REJECTED')),
    CONSTRAINT uq_membership_workspace_user UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_memberships_user_status ON workspace_memberships (user_id, status);
CREATE INDEX idx_memberships_workspace_status ON workspace_memberships (workspace_id, status);

CREATE TRIGGER trg_workspace_memberships_updated_at
    BEFORE UPDATE ON workspace_memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- One ACTIVE membership per existing user, using their most senior role.
INSERT INTO workspace_memberships (workspace_id, user_id, role_id, status, joined_at, last_selected_at)
SELECT DISTINCT ON (u.id) u.shop_id,
                          u.id,
                          r.id,
                          'ACTIVE',
                          u.created_at,
                          u.last_login_at
FROM users u
         JOIN user_roles ur ON ur.user_id = u.id
         JOIN roles r ON r.id = ur.role_id
WHERE u.shop_id IS NOT NULL
ORDER BY u.id, r.seniority ASC;

-- Platform account: shop_id is now "last selected workspace", not the tenant key.
ALTER TABLE users
    ALTER COLUMN shop_id DROP NOT NULL;
