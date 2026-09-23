-- Fitment is shared inside a group. A shop's stock, sales, and customers stay on that shop.
-- Phone and part names stay a shared reference. The rows that say what fits what belong to one group.

CREATE TABLE sharing_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    owner_user_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sharing_group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES sharing_groups (id) ON DELETE CASCADE,
    workspace_id UUID REFERENCES shops (id) ON DELETE CASCADE,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sharing_member_subject CHECK (
        (workspace_id IS NOT NULL AND user_id IS NULL)
        OR (workspace_id IS NULL AND user_id IS NOT NULL)
    ),
    CONSTRAINT ck_sharing_member_role CHECK (role IN ('MEMBER', 'ADMIN'))
);

CREATE UNIQUE INDEX uq_sharing_member_shop
    ON sharing_group_members (group_id, workspace_id)
    WHERE workspace_id IS NOT NULL;

CREATE UNIQUE INDEX uq_sharing_member_user
    ON sharing_group_members (group_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX ix_sharing_member_group ON sharing_group_members (group_id);

-- One home for the fitment rows that already exist, so today's catalog does not disappear.
DO $$
DECLARE
    owner_id UUID;
    default_group UUID := '11111111-1111-4111-8111-111111111111';
BEGIN
    SELECT id INTO owner_id FROM users ORDER BY created_at ASC NULLS LAST LIMIT 1;
    IF owner_id IS NULL THEN
        RETURN;
    END IF;
    INSERT INTO sharing_groups (id, name, owner_user_id)
    VALUES (default_group, 'Fitment catalog', owner_id);
    INSERT INTO sharing_group_members (group_id, workspace_id, role)
    SELECT default_group, id, 'MEMBER' FROM shops;
END $$;

ALTER TABLE catalog_fitments ADD COLUMN group_id UUID;

UPDATE catalog_fitments
SET group_id = '11111111-1111-4111-8111-111111111111'
WHERE group_id IS NULL
  AND EXISTS (
      SELECT 1 FROM sharing_groups WHERE id = '11111111-1111-4111-8111-111111111111'
  );

ALTER TABLE catalog_fitments
    ADD CONSTRAINT catalog_fitments_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES sharing_groups (id);

ALTER TABLE catalog_fitments DROP CONSTRAINT uq_catalog_fitment;

ALTER TABLE catalog_fitments
    ADD CONSTRAINT uq_catalog_fitment UNIQUE (group_id, component_id, device_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM catalog_fitments WHERE group_id IS NULL) THEN
        ALTER TABLE catalog_fitments ALTER COLUMN group_id SET NOT NULL;
    END IF;
END $$;

CREATE INDEX ix_catalog_fitments_group_device ON catalog_fitments (group_id, device_id);

ALTER TABLE catalog_contributions ADD COLUMN group_id UUID REFERENCES sharing_groups (id);
