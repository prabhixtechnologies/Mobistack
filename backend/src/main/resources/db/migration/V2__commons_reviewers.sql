-- Reviewing the shared catalog is not a shop role. COMMONS_REVIEW was seeded as a
-- permission and granted to nobody, so the queue was unreachable. Authority is now
-- a table of user ids, granted by Prabhix (admin console or the oneOps BFF).
-- COMPATIBILITY_APPROVE is left alone: that is still how a shop approves its own
-- private fitment notes.

CREATE TABLE commons_reviewers (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason VARCHAR(500)
);

DELETE FROM role_permissions
WHERE permission_id = '334321e2-1d7f-4bb7-90fa-e200df7985f5';

DELETE FROM permissions
WHERE code = 'COMMONS_REVIEW';
