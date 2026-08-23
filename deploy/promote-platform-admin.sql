-- Promote the operator account created via register-shop.
-- Safe to re-run.

UPDATE users
SET system_admin = TRUE,
    email_verified = TRUE,
    active = TRUE,
    failed_logins = 0,
    locked_until = NULL,
    must_change_pw = FALSE
WHERE lower(email) = 'admin@prabhixtechnologies.com';

INSERT INTO workspace_entitlements (workspace_id, code, active, expires_at)
SELECT u.shop_id, e.code, TRUE, NULL
FROM users u
         CROSS JOIN (VALUES ('WORKSPACE_CREATE'),
                            ('MEMBER_ADD'),
                            ('INVENTORY'),
                            ('SALES'),
                            ('REPAIRS'),
                            ('MULTI_USER')) AS e(code)
WHERE lower(u.email) = 'admin@prabhixtechnologies.com'
  AND u.shop_id IS NOT NULL
ON CONFLICT (workspace_id, code)
    DO UPDATE SET active = TRUE,
                  expires_at = NULL;
