-- Promote an existing Identity-mirrored MobiStack user to platform admin
-- (users.system_admin). Identity platform_admin is NOT mirrored automatically.
--
--   psql "host=$RDS user=mobistack dbname=mobistack sslmode=require" \
--     -v ON_ERROR_STOP=1 \
--     -v admin_email='you@prabhixtechnologies.com' \
--     -f deploy/promote-system-admin.sql
--
-- Safe to run twice.

\set ON_ERROR_STOP on

\if :{?admin_email}
\else
  DO $$ BEGIN
    RAISE EXCEPTION 'admin_email is required: psql -v admin_email=you@example.com -f deploy/promote-system-admin.sql';
  END $$;
\endif

BEGIN;

UPDATE users
SET system_admin = true,
    active = true,
    updated_at = now()
WHERE lower(email) = lower(:'admin_email');

COMMIT;

\echo 'Promoted (0 rows = email not found in mobistack.users):'
SELECT id, full_name, email, system_admin, active
FROM users
WHERE lower(email) = lower(:'admin_email');

\echo ''
\echo 'CAPTURED billing_orders sample:'
SELECT id, shop_id, amount, currency, status, paid_at
FROM billing_orders
WHERE status = 'CAPTURED'
ORDER BY coalesce(paid_at, created_at) DESC
LIMIT 10;
