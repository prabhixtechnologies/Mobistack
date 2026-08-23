-- Handset radio/region variants (4G vs 5G) are first-class so two otherwise
-- identical model names can sit in different compatibility groups.
ALTER TABLE device_models
    ADD COLUMN variant VARCHAR(40);

DROP INDEX IF EXISTS uq_device_models_brand_name;

CREATE UNIQUE INDEX uq_device_models_brand_name_variant
    ON device_models (brand_id, lower(name), lower(coalesce(variant, '')));

CREATE INDEX idx_device_models_variant
    ON device_models (shop_id, lower(variant))
    WHERE variant IS NOT NULL;
