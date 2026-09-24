-- First run against an empty mobistack database: the one account that can administer the platform.
--
-- Flyway's baseline creates the schema, the permissions, the six system roles, the billing plans,
-- the prices and the global feature flags. It does not create a user, so a freshly wiped database
-- has nobody who can sign in and no way to reach /api/v1/admin. This fixes that, and nothing else.
--
-- Deliberately no shop. Real shops sign themselves up — ShopProvisioningService creates the shops
-- row, the fourteen default categories, the owner membership and the billing state, and it does a
-- better job of it than SQL can. Seeding a placeholder shop here would only leave a fake tenant in
-- the workspace list that somebody has to remember to delete.
--
-- A system admin with no workspace signs in fine: principalFor falls through to
-- UserPrincipal.unscoped, and PlatformAdminService.requireAdmin reads users.system_admin from the
-- database rather than from the token. So the admin console works from the first login, and the
-- account picks up a workspace later only if it actually needs one.
--
-- Replaced deploy/promote-platform-admin.sql, now deleted, which required registering a shop through
-- the UI first and then promoting the account it created — two steps, a leftover shop nobody wanted,
-- and the one address it worked for hardcoded in the file.
--
--   psql "host=$RDS user=mobistack dbname=mobistack sslmode=require" \
--     -v ON_ERROR_STOP=1 \
--     -v admin_email=admin@prabhixtechnologies.com \
--     -v admin_name='Your Name' \
--     -f deploy/seed.sql
--
-- Locally:
--
--   docker compose exec -T postgres psql -U mobistack -d mobistack -v ON_ERROR_STOP=1 \
--     -f - < deploy/seed.sql
--
-- No password is stored here. Identity checks the sign-in secret; this row is the mirror the
-- shop tables' foreign keys point at, plus system_admin so the admin console has someone to
-- admit. Create the same address in Identity before expecting a login to succeed.
--
-- Safe to run twice: the insert is guarded on the address and the promotion is an idempotent
-- UPDATE, so a second run restores active and system_admin rather than inserting another row.

\set ON_ERROR_STOP on

\if :{?admin_email}
\else
  \set admin_email 'admin@prabhixtechnologies.com'
\endif

\if :{?admin_name}
\else
  \set admin_name 'Prabhix Admin'
\endif

\if :{?admin_phone}
\else
  \set admin_phone NULL
\endif

BEGIN;

-- email is citext, so the comparison is case-insensitive without lower().
INSERT INTO users (shop_id, full_name, email, phone, active,
                   email_verified, phone_verified, system_admin)
SELECT NULL, :'admin_name', :'admin_email', :admin_phone,
       true, true, false, true
WHERE NOT EXISTS (
  SELECT 1 FROM users WHERE email = :'admin_email'
);

-- Runs whether or not the insert above did. A second run re-grants the admin flag and clears a
-- disabled account. The password, if one is needed, is reset in Identity.
UPDATE users
SET full_name      = :'admin_name',
    active         = true,
    email_verified = true,
    system_admin   = true,
    updated_at     = now()
WHERE email = :'admin_email';

COMMIT;

\echo ''
\echo 'Seeded:'
SELECT full_name, email, system_admin, active,
       CASE WHEN shop_id IS NULL THEN 'none' ELSE shop_id::text END AS workspace
FROM users
WHERE email = :'admin_email';

\echo ''
\echo 'This account can be signed in only after the same address exists in Identity. The admin'
\echo 'console is at /admin. Shops create themselves through registration; if this account needs a'
\echo 'workspace of its own, make one from the workspace switcher.'
\echo ''
\echo 'The compatibility catalog is empty on a fresh database. deploy/seed-commons.sql puts a small'
\echo 'starter set in it so the lookup screens are not blank.'
