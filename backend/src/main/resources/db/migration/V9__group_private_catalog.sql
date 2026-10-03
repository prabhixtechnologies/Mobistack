-- Phone and part rows belong to one fitment group. The rows that already exist stay with
-- the original Fitment catalog group. A newly created group does not receive a copy.

ALTER TABLE catalog_brands ADD COLUMN group_id UUID;
ALTER TABLE catalog_devices ADD COLUMN group_id UUID;
ALTER TABLE catalog_components ADD COLUMN group_id UUID;

UPDATE catalog_brands
SET group_id = '11111111-1111-4111-8111-111111111111'
WHERE group_id IS NULL
  AND EXISTS (
      SELECT 1 FROM sharing_groups WHERE id = '11111111-1111-4111-8111-111111111111'
  );

UPDATE catalog_devices AS device
SET group_id = brand.group_id
FROM catalog_brands AS brand
WHERE device.brand_id = brand.id
  AND device.group_id IS NULL;

UPDATE catalog_components
SET group_id = '11111111-1111-4111-8111-111111111111'
WHERE group_id IS NULL
  AND EXISTS (
      SELECT 1 FROM sharing_groups WHERE id = '11111111-1111-4111-8111-111111111111'
  );

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM catalog_brands WHERE group_id IS NULL) THEN
        ALTER TABLE catalog_brands ALTER COLUMN group_id SET NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalog_devices WHERE group_id IS NULL) THEN
        ALTER TABLE catalog_devices ALTER COLUMN group_id SET NOT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalog_components WHERE group_id IS NULL) THEN
        ALTER TABLE catalog_components ALTER COLUMN group_id SET NOT NULL;
    END IF;
END $$;

ALTER TABLE catalog_brands
    ADD CONSTRAINT catalog_brands_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES sharing_groups (id);
ALTER TABLE catalog_devices
    ADD CONSTRAINT catalog_devices_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES sharing_groups (id);
ALTER TABLE catalog_components
    ADD CONSTRAINT catalog_components_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES sharing_groups (id);

DROP INDEX IF EXISTS uq_catalog_brands_name;
CREATE UNIQUE INDEX uq_catalog_brands_name
    ON catalog_brands (group_id, lower(name));

DROP INDEX IF EXISTS uq_catalog_components_identity;
CREATE UNIQUE INDEX uq_catalog_components_identity
    ON catalog_components (group_id, category_code, lower(name));

CREATE INDEX ix_catalog_brands_group ON catalog_brands (group_id);
CREATE INDEX ix_catalog_devices_group ON catalog_devices (group_id, brand_id);
CREATE INDEX ix_catalog_components_group ON catalog_components (group_id, category_code);

CREATE TABLE catalog_equivalence_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES sharing_groups (id),
    category_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_catalog_equivalence_group
    ON catalog_equivalence_groups (group_id, category_code, lower(name));

CREATE TABLE catalog_equivalence_group_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equivalence_group_id UUID NOT NULL REFERENCES catalog_equivalence_groups (id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES catalog_devices (id),
    primary_device BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID
);

CREATE UNIQUE INDEX uq_catalog_equivalence_member
    ON catalog_equivalence_group_devices (equivalence_group_id, device_id);

CREATE INDEX ix_catalog_equivalence_device
    ON catalog_equivalence_group_devices (device_id);
