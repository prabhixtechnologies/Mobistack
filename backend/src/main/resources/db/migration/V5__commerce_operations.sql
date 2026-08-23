-- =====================================================================
-- FixFlow V5 - Sales, payments, purchases, repairs
-- Completes the operational desks that Phases 4–6 specified.
-- =====================================================================

CREATE TABLE sales
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id         UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    customer_id     UUID REFERENCES customers (id) ON DELETE SET NULL,
    invoice_number  VARCHAR(32)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    pricing_flag    VARCHAR(20)   NOT NULL DEFAULT 'NORMAL',
    subtotal        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    paid            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    profit          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes           TEXT,
    idempotency_key VARCHAR(80),
    device_id       VARCHAR(80),
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    voided_at       TIMESTAMPTZ,
    void_reason     VARCHAR(255),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_sales_status CHECK (status IN ('COMPLETED', 'VOID')),
    CONSTRAINT uq_sales_shop_invoice UNIQUE (shop_id, invoice_number),
    CONSTRAINT uq_sales_idempotency UNIQUE (shop_id, idempotency_key)
);

CREATE INDEX idx_sales_shop_occurred ON sales (shop_id, occurred_at DESC);
CREATE INDEX idx_sales_shop_customer ON sales (shop_id, customer_id);
CREATE TRIGGER trg_sales_updated_at
    BEFORE UPDATE ON sales
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE sale_items
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    sale_id            UUID          NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_variant_id UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity           INT           NOT NULL,
    unit_price         NUMERIC(14, 2) NOT NULL,
    unit_cost          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    discount           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax_rate           NUMERIC(6, 2)  NOT NULL DEFAULT 0,
    line_total         NUMERIC(14, 2) NOT NULL,
    profit             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT ck_sale_items_qty CHECK (quantity > 0)
);

CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);

CREATE TABLE payments
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id         UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    reference_type  VARCHAR(20)   NOT NULL,
    reference_id    UUID          NOT NULL,
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL,
    status          VARCHAR(24)   NOT NULL,
    gateway         VARCHAR(40),
    gateway_ref     VARCHAR(80),
    notes           VARCHAR(255),
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    CONSTRAINT ck_payments_ref CHECK (reference_type IN ('SALE', 'PURCHASE', 'REPAIR', 'BILLING')),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'UPI', 'CARD', 'CREDIT', 'NETBANKING', 'MIXED', 'RAZORPAY', 'DEV')),
    CONSTRAINT ck_payments_status CHECK (status IN
                                         ('CREATED', 'PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED',
                                          'REFUNDED', 'PARTIALLY_REFUNDED', 'EXPIRED'))
);

CREATE INDEX idx_payments_ref ON payments (shop_id, reference_type, reference_id);
CREATE INDEX idx_payments_gateway ON payments (gateway, gateway_ref);

CREATE TABLE purchases
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id         UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    supplier_id     UUID          NOT NULL REFERENCES suppliers (id) ON DELETE RESTRICT,
    status          VARCHAR(20)   NOT NULL,
    subtotal        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    tax             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    paid            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    notes           TEXT,
    idempotency_key VARCHAR(80),
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT ck_purchases_status CHECK (status IN ('RECEIVED', 'CANCELLED')),
    CONSTRAINT uq_purchases_idempotency UNIQUE (shop_id, idempotency_key)
);

CREATE INDEX idx_purchases_shop ON purchases (shop_id, received_at DESC);
CREATE TRIGGER trg_purchases_updated_at
    BEFORE UPDATE ON purchases
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE purchase_items
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    purchase_id        UUID          NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    product_variant_id UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity           INT           NOT NULL,
    unit_cost          NUMERIC(14, 2) NOT NULL,
    line_total         NUMERIC(14, 2) NOT NULL,
    batch_no           VARCHAR(40),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT ck_purchase_items_qty CHECK (quantity > 0)
);

CREATE INDEX idx_purchase_items_purchase ON purchase_items (purchase_id);

CREATE TABLE repairs
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    customer_id        UUID REFERENCES customers (id) ON DELETE SET NULL,
    device_model_id    UUID REFERENCES device_models (id) ON DELETE SET NULL,
    technician_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    job_number         VARCHAR(32)   NOT NULL,
    imei               VARCHAR(32),
    problem            TEXT          NOT NULL,
    status             VARCHAR(24)   NOT NULL,
    estimated_cost     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    labor_charge       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    labor_cost         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    parts_total        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    parts_cost         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total              NUMERIC(14, 2) NOT NULL DEFAULT 0,
    paid               NUMERIC(14, 2) NOT NULL DEFAULT 0,
    outstanding        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    profit             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    customer_notes     TEXT,
    internal_notes     TEXT,
    expected_at        TIMESTAMPTZ,
    delivered_at       TIMESTAMPTZ,
    idempotency_key    VARCHAR(80),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    CONSTRAINT ck_repairs_status CHECK (status IN
                                        ('RECEIVED', 'DIAGNOSING', 'WAITING_FOR_PART', 'IN_REPAIR',
                                         'READY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT uq_repairs_shop_job UNIQUE (shop_id, job_number),
    CONSTRAINT uq_repairs_idempotency UNIQUE (shop_id, idempotency_key)
);

CREATE INDEX idx_repairs_shop_status ON repairs (shop_id, status);
CREATE TRIGGER trg_repairs_updated_at
    BEFORE UPDATE ON repairs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE repair_parts
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    repair_id          UUID          NOT NULL REFERENCES repairs (id) ON DELETE CASCADE,
    product_variant_id UUID          NOT NULL REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity           INT           NOT NULL,
    unit_price         NUMERIC(14, 2) NOT NULL,
    unit_cost          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    line_total         NUMERIC(14, 2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT ck_repair_parts_qty CHECK (quantity > 0)
);

CREATE INDEX idx_repair_parts_repair ON repair_parts (repair_id);

ALTER TABLE shops
    ADD COLUMN repair_prefix VARCHAR(12) NOT NULL DEFAULT 'JOB';
ALTER TABLE shops
    ADD COLUMN repair_next_number BIGINT NOT NULL DEFAULT 1;
