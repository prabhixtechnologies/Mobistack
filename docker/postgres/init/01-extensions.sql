-- Extensions FixFlow relies on. Creating them here (as the bootstrap superuser)
-- means the Flyway migrations can run under a least-privilege application role.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";
