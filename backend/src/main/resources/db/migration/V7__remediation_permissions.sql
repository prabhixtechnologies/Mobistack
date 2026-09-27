-- Capability gates for notifications, support, feature flags and bounded commerce overrides.

INSERT INTO permissions (id, code, resource, action, description, created_at)
VALUES ('a1b2c3d4-e5f6-4789-a012-000000000001', 'NOTIFICATION_READ', 'NOTIFICATION', 'READ',
        'View notification history', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000002', 'NOTIFICATION_WRITE', 'NOTIFICATION', 'WRITE',
        'Manage notification preferences', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000003', 'SUPPORT_READ', 'SUPPORT', 'READ',
        'View support conversations', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000004', 'SUPPORT_WRITE', 'SUPPORT', 'WRITE',
        'Open and reply to support tickets', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000005', 'FEATURE_FLAG_READ', 'FEATURE_FLAG', 'READ',
        'View workspace feature flags', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000006', 'SALES_PRICE_OVERRIDE', 'SALES', 'OVERRIDE',
        'Override server sale prices within bounds', now()),
       ('a1b2c3d4-e5f6-4789-a012-000000000007', 'PURCHASE_COST_OVERRIDE', 'PURCHASE', 'OVERRIDE',
        'Override server purchase costs within bounds', now())
ON CONFLICT DO NOTHING;

-- OWNER and ADMIN: all new capabilities
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'ADMIN')
  AND p.code IN ('NOTIFICATION_READ', 'NOTIFICATION_WRITE', 'SUPPORT_READ', 'SUPPORT_WRITE',
                 'FEATURE_FLAG_READ', 'SALES_PRICE_OVERRIDE', 'PURCHASE_COST_OVERRIDE')
ON CONFLICT DO NOTHING;

-- MANAGER: operations overrides and support; not notification preference writes for others
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('NOTIFICATION_READ', 'NOTIFICATION_WRITE', 'SUPPORT_READ',
                                        'SUPPORT_WRITE', 'FEATURE_FLAG_READ', 'SALES_PRICE_OVERRIDE',
                                        'PURCHASE_COST_OVERRIDE')
WHERE r.code = 'MANAGER'
ON CONFLICT DO NOTHING;

-- STAFF / TECHNICIAN: notify + support
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('NOTIFICATION_READ', 'NOTIFICATION_WRITE', 'SUPPORT_READ', 'SUPPORT_WRITE')
WHERE r.code IN ('STAFF', 'TECHNICIAN')
ON CONFLICT DO NOTHING;

-- VIEWER: read-only notify + support
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('NOTIFICATION_READ', 'SUPPORT_READ')
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;
