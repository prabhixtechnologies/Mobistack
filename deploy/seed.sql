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
--     -v admin_password='<something long>' \
--     -v admin_name='Your Name' \
--     -f deploy/seed.sql
--
-- Locally:
--
--   docker compose exec -T postgres psql -U mobistack -d mobistack -v ON_ERROR_STOP=1 \
--     -v admin_password='dev-password' -f - < deploy/seed.sql
--
-- The password is hashed here by pgcrypto at bcrypt cost 12, matching PasswordConfig's
-- BCryptPasswordEncoder(12), so the application accepts it unchanged.
--
-- Safe to run twice: the insert is guarded on the address and the promotion is an idempotent
-- UPDATE, so a second run with a different password rotates it rather than failing.

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

-- RAISE rather than \quit, which takes no status and would exit 0 — a seed that silently did
-- nothing is worse than one that failed.
\if :{?admin_password}
\else
  DO $$ BEGIN
    RAISE EXCEPTION 'admin_password is required: psql -v admin_password=<value> -f deploy/seed.sql';
  END $$;
\endif

BEGIN;

-- users.email is varchar, not citext, and the unique index is on lower(email). So every comparison
-- has to lower() both sides itself; there is no case-insensitive operator doing it here.
INSERT INTO users (shop_id, full_name, email, phone, password_hash, active, must_change_pw,
                   email_verified, phone_verified, system_admin)
SELECT NULL, :'admin_name', :'admin_email', :admin_phone,
       crypt(:'admin_password', gen_salt('bf', 12)),
       true, false, true, false, true
WHERE NOT EXISTS (
  SELECT 1 FROM users WHERE lower(email) = lower(:'admin_email')
);

-- Runs whether or not the insert above did, which is what makes a second run useful: it rotates the
-- password, clears a lockout from too many failed sign-ins, and re-grants the admin flag if someone
-- removed it. Everything a locked-out operator needs, without a second script.
UPDATE users
SET full_name      = :'admin_name',
    password_hash  = crypt(:'admin_password', gen_salt('bf', 12)),
    active         = true,
    must_change_pw = false,
    email_verified = true,
    system_admin   = true,
    failed_logins  = 0,
    locked_until   = NULL,
    updated_at     = now()
WHERE lower(email) = lower(:'admin_email');

COMMIT;

\echo ''
\echo 'Seeded:'
SELECT full_name, email, system_admin, active,
       CASE WHEN shop_id IS NULL THEN 'none' ELSE shop_id::text END AS workspace
FROM users
WHERE lower(email) = lower(:'admin_email');

\echo ''
\echo 'Sign in at /login with the admin_email and admin_password given above. The admin console is'
\echo 'at /admin. Shops create themselves through registration; if this account needs a workspace of'
\echo 'its own, make one from the workspace switcher.'
\echo ''
\echo 'The compatibility catalog is empty on a fresh database. deploy/seed-commons.sql puts a small'
\echo 'starter set in it so the lookup screens are not blank.'
