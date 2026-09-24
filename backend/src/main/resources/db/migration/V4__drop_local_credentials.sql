-- Identity owns passwords, one-time codes and sessions. This database keeps a mirror of the
-- person, keyed by Identity's id, and nothing that could sign somebody in.
--
-- The mirror insert omits password_hash, and the baseline declares that column NOT NULL with
-- no default, so a first sign-in cannot write the row it needs. Dropping the column is what
-- makes that insert succeed. must_change_pw, failed_logins and locked_until go with it: no
-- Java field reads them.
--
-- refresh_tokens, user_identities and user_tokens are the local session and OTP tables from
-- before Identity. They are dropped only when empty, so a database that still holds those
-- rows fails here instead of losing them.
--
-- users.email becomes citext, matching Identity and oneOps. The old unique index was
-- lower(email) because the column was varchar and would have allowed Admin@ and admin@ as
-- two people.

DO $$
DECLARE
    leftover text;
BEGIN
    SELECT string_agg(format('%s (%s rows)', rel, n), ', ' ORDER BY rel)
    INTO leftover
    FROM (
        SELECT 'refresh_tokens'::text AS rel, count(*) AS n FROM public.refresh_tokens
        UNION ALL SELECT 'user_identities', count(*) FROM public.user_identities
        UNION ALL SELECT 'user_tokens', count(*) FROM public.user_tokens
    ) counts
    WHERE n > 0;

    IF leftover IS NOT NULL THEN
        RAISE EXCEPTION 'Refusing to drop credential leftovers that still hold rows: %', leftover;
    END IF;
END $$;

DROP TABLE public.refresh_tokens;
DROP TABLE public.user_identities;
DROP TABLE public.user_tokens;

ALTER TABLE public.users
    DROP COLUMN password_hash,
    DROP COLUMN must_change_pw,
    DROP COLUMN failed_logins,
    DROP COLUMN locked_until;

CREATE EXTENSION IF NOT EXISTS citext;

ALTER TABLE public.users
    ALTER COLUMN email TYPE public.citext USING email::public.citext;

DROP INDEX public.uq_users_email;
CREATE UNIQUE INDEX uq_users_email ON public.users (email);
