-- Align tables that predate BaseEntity / AuditableEntity with Hibernate validate.
ALTER TABLE price_list_items
    ADD COLUMN IF NOT EXISTS created_by UUID;

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS created_by UUID;

ALTER TABLE workspace_memberships
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
