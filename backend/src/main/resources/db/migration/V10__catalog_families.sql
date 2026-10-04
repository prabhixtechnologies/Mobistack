-- Shop phones can point at a catalog phone. Equivalence groups become real
-- components with fitment rows, then those tables go away.

ALTER TABLE device_models
    ADD COLUMN IF NOT EXISTS catalog_device_id UUID REFERENCES catalog_devices (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_device_models_catalog_device
    ON device_models (catalog_device_id);

UPDATE device_models AS dm
SET catalog_device_id = matched.catalog_id
FROM (
    SELECT DISTINCT ON (dm2.id)
        dm2.id AS device_model_id,
        cd.id AS catalog_id
    FROM device_models dm2
    JOIN brands b ON b.id = dm2.brand_id
    JOIN sharing_group_members sgm ON sgm.shop_id = dm2.shop_id
    JOIN catalog_devices cd ON cd.group_id = sgm.group_id
    JOIN catalog_brands cb ON cb.id = cd.brand_id
    WHERE dm2.catalog_device_id IS NULL
      AND (
            (dm2.model_code IS NOT NULL AND cd.model_code IS NOT NULL
                AND lower(dm2.model_code) = lower(cd.model_code))
            OR (
                lower(b.name) = lower(cb.name)
                AND lower(dm2.name) = lower(cd.name)
                AND lower(COALESCE(dm2.variant, '')) = lower(COALESCE(cd.variant, ''))
            )
      )
    ORDER BY dm2.id,
             CASE
                 WHEN dm2.model_code IS NOT NULL AND cd.model_code IS NOT NULL
                      AND lower(dm2.model_code) = lower(cd.model_code) THEN 0
                 ELSE 1
             END,
             cd.id
) AS matched
WHERE dm.id = matched.device_model_id
  AND dm.catalog_device_id IS NULL;

INSERT INTO catalog_components (group_id, category_code, name, attributes, created_at, updated_at, version)
SELECT g.group_id, g.category_code, g.name, '{}'::jsonb, now(), now(), 0
FROM catalog_equivalence_groups g
WHERE NOT EXISTS (
    SELECT 1 FROM catalog_components c
    WHERE c.group_id = g.group_id
      AND c.category_code = g.category_code
      AND lower(c.name) = lower(g.name)
);

INSERT INTO catalog_fitments (group_id, component_id, device_id, fit_quality, confirmations, disputes, disputed, version)
SELECT g.group_id, c.id, m.device_id, 'COMPATIBLE', 0, 0, false, 0
FROM catalog_equivalence_groups g
JOIN catalog_components c
  ON c.group_id = g.group_id
 AND c.category_code = g.category_code
 AND lower(c.name) = lower(g.name)
JOIN catalog_equivalence_group_devices m ON m.equivalence_group_id = g.id
WHERE NOT EXISTS (
    SELECT 1 FROM catalog_fitments f
    WHERE f.group_id = g.group_id
      AND f.component_id = c.id
      AND f.device_id = m.device_id
);

DROP TABLE IF EXISTS catalog_equivalence_group_devices;
DROP TABLE IF EXISTS catalog_equivalence_groups;
