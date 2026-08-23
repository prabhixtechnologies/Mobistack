-- =====================================================================
-- FixFlow V2 - Parties, catalog and the universal compatibility system
--
-- The compatibility model is the heart of the product. A shopkeeper
-- thinks "Realme 6 = Realme 6i = Realme 7" for displays, but those same
-- phones may need *different* back covers. So compatibility groups are
-- scoped to a part category, never to the device alone.
-- =====================================================================

-- Text normaliser used by generated search columns. Declared IMMUTABLE so
-- it can back generated columns and expression indexes.
CREATE OR REPLACE FUNCTION fixflow_normalize(input TEXT)
    RETURNS TEXT AS
$$
SELECT btrim(regexp_replace(lower(unaccent(coalesce(input, ''))), '[^a-z0-9]+', ' ', 'g'));
$$ LANGUAGE sql IMMUTABLE;

-- ---------------------------------------------------------------------
-- Parties
-- ---------------------------------------------------------------------
CREATE TABLE suppliers
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id           UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name              VARCHAR(160)  NOT NULL,
    normalized_name   TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    contact_person    VARCHAR(160),
    phone             VARCHAR(32),
    email             VARCHAR(255),
    address_line1     VARCHAR(255),
    city              VARCHAR(120),
    state             VARCHAR(120),
    postal_code       VARCHAR(20),
    gst_number        VARCHAR(20),
    payment_terms_days INT          NOT NULL DEFAULT 0,
    opening_balance   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes             TEXT,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID
);

CREATE UNIQUE INDEX uq_suppliers_shop_name ON suppliers (shop_id, lower(name));
CREATE INDEX idx_suppliers_name_trgm ON suppliers USING gin (normalized_name gin_trgm_ops);
CREATE TRIGGER trg_suppliers_updated_at BEFORE UPDATE ON suppliers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE customers
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name               VARCHAR(160)  NOT NULL,
    normalized_name    TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    phone              VARCHAR(32),
    email              VARCHAR(255),
    address_line1      VARCHAR(255),
    city               VARCHAR(120),
    customer_type      VARCHAR(20)   NOT NULL DEFAULT 'RETAIL',
    gst_number         VARCHAR(20),
    credit_limit       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_purchases    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMPTZ,
    notes              TEXT,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT ck_customers_type CHECK (customer_type IN ('RETAIL', 'WHOLESALE', 'VIP', 'TECHNICIAN', 'INTERNAL'))
);

CREATE UNIQUE INDEX uq_customers_shop_phone ON customers (shop_id, phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_customers_name_trgm ON customers USING gin (normalized_name gin_trgm_ops);
CREATE INDEX idx_customers_shop ON customers (shop_id) WHERE active;
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- categories : part categories, seeded per shop from a default template
-- so each shop can rename, reorder, disable or add its own.
-- ---------------------------------------------------------------------
CREATE TABLE categories
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id       UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    parent_id     UUID REFERENCES categories (id) ON DELETE SET NULL,
    code          VARCHAR(48) NOT NULL,
    name          VARCHAR(80) NOT NULL,
    icon          VARCHAR(48),
    color         VARCHAR(9),
    sort_order    INT         NOT NULL DEFAULT 100,
    -- Parts in these categories are looked up via compatibility groups
    -- (a display fits many phones). Accessories like cables are not.
    compatibility_relevant BOOLEAN NOT NULL DEFAULT TRUE,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID
);

CREATE UNIQUE INDEX uq_categories_shop_code ON categories (shop_id, code);
CREATE INDEX idx_categories_shop_sort ON categories (shop_id, sort_order) WHERE active;
CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- brands / device_models / device_aliases
-- ---------------------------------------------------------------------
CREATE TABLE brands
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id         UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name            VARCHAR(80) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    logo_url        TEXT,
    color           VARCHAR(9),
    sort_order      INT         NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE UNIQUE INDEX uq_brands_shop_name ON brands (shop_id, lower(name));
CREATE INDEX idx_brands_name_trgm ON brands USING gin (normalized_name gin_trgm_ops);
CREATE TRIGGER trg_brands_updated_at BEFORE UPDATE ON brands
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE device_models
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id         UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    brand_id        UUID         NOT NULL REFERENCES brands (id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    model_code      VARCHAR(60),
    release_year    INT,
    popularity      INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE UNIQUE INDEX uq_device_models_brand_name ON device_models (brand_id, lower(name));
CREATE INDEX idx_device_models_shop ON device_models (shop_id) WHERE active;
CREATE INDEX idx_device_models_name_trgm ON device_models USING gin (normalized_name gin_trgm_ops);
CREATE TRIGGER trg_device_models_updated_at BEFORE UPDATE ON device_models
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Alternate spellings and market names: "Realme 6i", "RMX2002", "Narzo 20".
CREATE TABLE device_aliases
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    device_model_id    UUID         NOT NULL REFERENCES device_models (id) ON DELETE CASCADE,
    alias              VARCHAR(120) NOT NULL,
    normalized_alias   TEXT GENERATED ALWAYS AS (fixflow_normalize(alias)) STORED,
    source             VARCHAR(24)  NOT NULL DEFAULT 'MANUAL',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT ck_device_aliases_source CHECK (source IN ('MANUAL', 'IMPORT', 'SYSTEM'))
);

CREATE UNIQUE INDEX uq_device_aliases_shop_alias ON device_aliases (shop_id, lower(alias));
CREATE INDEX idx_device_aliases_model ON device_aliases (device_model_id);
CREATE INDEX idx_device_aliases_trgm ON device_aliases USING gin (normalized_alias gin_trgm_ops);

-- ---------------------------------------------------------------------
-- compatibility_groups : "these models take the same part"
-- ---------------------------------------------------------------------
CREATE TABLE compatibility_groups
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id     UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories (id) ON DELETE SET NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(160) NOT NULL,
    notes       TEXT,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE UNIQUE INDEX uq_compat_groups_shop_code ON compatibility_groups (shop_id, code);
CREATE INDEX idx_compat_groups_category ON compatibility_groups (category_id);
CREATE TRIGGER trg_compat_groups_updated_at BEFORE UPDATE ON compatibility_groups
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE compatibility_group_devices
(
    id                     UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    compatibility_group_id UUID        NOT NULL REFERENCES compatibility_groups (id) ON DELETE CASCADE,
    device_model_id        UUID        NOT NULL REFERENCES device_models (id) ON DELETE CASCADE,
    -- The model the group is usually named after; shown first in search.
    primary_device         BOOLEAN     NOT NULL DEFAULT FALSE,
    note                   VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    CONSTRAINT uq_compat_group_device UNIQUE (compatibility_group_id, device_model_id)
);

CREATE INDEX idx_compat_group_devices_device ON compatibility_group_devices (device_model_id);

-- ---------------------------------------------------------------------
-- products / product_variants
-- A Product is "iPhone 11 Display". A Variant is "iPhone 11 Display /
-- GX / A+ / Black" and owns SKU, stock and every price.
-- ---------------------------------------------------------------------
CREATE TABLE products
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id         UUID         NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    category_id     UUID         NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    brand_id        UUID REFERENCES brands (id) ON DELETE SET NULL,
    name            VARCHAR(200) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    description     TEXT,
    hsn_code        VARCHAR(16),
    unit            VARCHAR(16)  NOT NULL DEFAULT 'PCS',
    tax_rate        NUMERIC(5, 2) NOT NULL DEFAULT 0,
    image_url       TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX idx_products_shop_category ON products (shop_id, category_id) WHERE active;
CREATE INDEX idx_products_name_trgm ON products USING gin (normalized_name gin_trgm_ops);
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_variants
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id           UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    product_id        UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    supplier_id       UUID REFERENCES suppliers (id) ON DELETE SET NULL,
    sku               VARCHAR(64)   NOT NULL,
    barcode           VARCHAR(64),
    variant_name      VARCHAR(160)  NOT NULL,
    normalized_name   TEXT GENERATED ALWAYS AS (fixflow_normalize(variant_name)) STORED,
    grade             VARCHAR(24),
    quality           VARCHAR(40),
    color             VARCHAR(40),

    -- Pricing (see price_rules for how one of these gets selected)
    cost_price        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    retail_price      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    wholesale_price   NUMERIC(14, 2),
    repair_price      NUMERIC(14, 2),
    min_price         NUMERIC(14, 2),
    clearance_price   NUMERIC(14, 2),

    -- Stock. on_hand_qty is a cache of the inventory_transactions ledger
    -- and is rebuildable; available_qty is always derived.
    on_hand_qty       INT           NOT NULL DEFAULT 0,
    reserved_qty      INT           NOT NULL DEFAULT 0,
    available_qty     INT GENERATED ALWAYS AS (on_hand_qty - reserved_qty) STORED,
    reorder_level     INT           NOT NULL DEFAULT 0,
    max_stock_level   INT,

    warranty_days     INT           NOT NULL DEFAULT 0,
    batch_no          VARCHAR(64),
    serial_tracked    BOOLEAN       NOT NULL DEFAULT FALSE,
    location          VARCHAR(64),

    first_stocked_at  TIMESTAMPTZ,
    last_purchased_at TIMESTAMPTZ,
    last_sold_at      TIMESTAMPTZ,

    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,

    CONSTRAINT ck_variant_qty_non_negative CHECK (on_hand_qty >= 0 AND reserved_qty >= 0),
    CONSTRAINT ck_variant_prices_non_negative CHECK (cost_price >= 0 AND retail_price >= 0)
);

CREATE UNIQUE INDEX uq_variants_shop_sku ON product_variants (shop_id, upper(sku));
CREATE UNIQUE INDEX uq_variants_shop_barcode ON product_variants (shop_id, barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_variants_product ON product_variants (product_id);
CREATE INDEX idx_variants_supplier ON product_variants (supplier_id);
CREATE INDEX idx_variants_name_trgm ON product_variants USING gin (normalized_name gin_trgm_ops);
CREATE INDEX idx_variants_sku_trgm ON product_variants USING gin (upper(sku) gin_trgm_ops);
-- Drives the low-stock dashboard card without a full table scan.
CREATE INDEX idx_variants_low_stock ON product_variants (shop_id)
    WHERE active AND on_hand_qty <= reorder_level;
CREATE INDEX idx_variants_dead_stock ON product_variants (shop_id, last_sold_at)
    WHERE active AND on_hand_qty > 0;
CREATE TRIGGER trg_variants_updated_at BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- product_compatibilities : a product fits either a whole compatibility
-- group (the normal case) or one specific model (the exception).
-- ---------------------------------------------------------------------
CREATE TABLE product_compatibilities
(
    id                     UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id                UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    product_id             UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    compatibility_group_id UUID REFERENCES compatibility_groups (id) ON DELETE CASCADE,
    device_model_id        UUID REFERENCES device_models (id) ON DELETE CASCADE,
    fit_quality            VARCHAR(16) NOT NULL DEFAULT 'EXACT',
    note                   VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID,
    CONSTRAINT ck_product_compat_target CHECK (
        (compatibility_group_id IS NOT NULL AND device_model_id IS NULL) OR
        (compatibility_group_id IS NULL AND device_model_id IS NOT NULL)
        ),
    CONSTRAINT ck_product_compat_fit CHECK (fit_quality IN ('EXACT', 'COMPATIBLE', 'REQUIRES_MODIFICATION'))
);

CREATE UNIQUE INDEX uq_product_compat_group ON product_compatibilities (product_id, compatibility_group_id)
    WHERE compatibility_group_id IS NOT NULL;
CREATE UNIQUE INDEX uq_product_compat_device ON product_compatibilities (product_id, device_model_id)
    WHERE device_model_id IS NOT NULL;
CREATE INDEX idx_product_compat_group ON product_compatibilities (compatibility_group_id);
CREATE INDEX idx_product_compat_device ON product_compatibilities (device_model_id);
