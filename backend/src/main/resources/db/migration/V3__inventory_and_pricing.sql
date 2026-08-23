-- =====================================================================
-- FixFlow V3 - Inventory ledger, stock alerts and the pricing engine
--
-- Every stock change is an append-only row in inventory_transactions.
-- product_variants.on_hand_qty is a cache of that ledger, kept in the
-- same DB transaction and rebuildable at any time.
-- =====================================================================

CREATE TABLE inventory_transactions
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id            UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    product_variant_id UUID          NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,

    type               VARCHAR(20)   NOT NULL,
    -- Always a positive magnitude; the deltas below carry the direction.
    quantity           INT           NOT NULL,
    on_hand_delta      INT           NOT NULL,
    reserved_delta     INT           NOT NULL DEFAULT 0,
    -- Running on-hand balance immediately after this row was applied.
    balance_after      INT           NOT NULL,

    unit_cost          NUMERIC(14, 2),
    total_cost         NUMERIC(14, 2),

    reference_type     VARCHAR(24)   NOT NULL DEFAULT 'MANUAL',
    reference_id       UUID,
    reference_label    VARCHAR(80),

    -- Offline clients generate this; it makes replayed syncs a no-op.
    idempotency_key    VARCHAR(120),
    device_id          VARCHAR(120),

    batch_no           VARCHAR(64),
    serial_no          VARCHAR(120),
    reason             VARCHAR(160),
    notes              TEXT,

    occurred_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    created_by_name    VARCHAR(160),

    CONSTRAINT ck_inv_txn_type CHECK (type IN
                                      ('IN', 'OUT', 'RETURN', 'DAMAGE', 'ADJUSTMENT',
                                       'RESERVATION', 'RELEASE', 'OPENING', 'TRANSFER')),
    CONSTRAINT ck_inv_txn_reference CHECK (reference_type IN
                                           ('MANUAL', 'SALE', 'SALE_RETURN', 'PURCHASE', 'PURCHASE_RETURN',
                                            'REPAIR', 'STOCK_TAKE', 'IMPORT', 'SYNC')),
    CONSTRAINT ck_inv_txn_quantity CHECK (quantity > 0)
);

CREATE UNIQUE INDEX uq_inv_txn_idempotency ON inventory_transactions (shop_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_inv_txn_variant_time ON inventory_transactions (product_variant_id, occurred_at DESC);
CREATE INDEX idx_inv_txn_shop_time ON inventory_transactions (shop_id, occurred_at DESC);
CREATE INDEX idx_inv_txn_reference ON inventory_transactions (reference_type, reference_id);

-- ---------------------------------------------------------------------
-- stock_alerts : one open alert per variant per alert type.
-- ---------------------------------------------------------------------
CREATE TABLE stock_alerts
(
    id                 UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id            UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    product_variant_id UUID        NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    alert_type         VARCHAR(24) NOT NULL,
    severity           VARCHAR(12) NOT NULL DEFAULT 'ORANGE',
    status             VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    threshold_value    INT,
    observed_value     INT,
    message            VARCHAR(400) NOT NULL,
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_alert_type CHECK (alert_type IN ('LOW_STOCK', 'OUT_OF_STOCK', 'DEAD_STOCK', 'OVERSTOCK')),
    CONSTRAINT ck_stock_alert_severity CHECK (severity IN ('GREEN', 'ORANGE', 'RED')),
    CONSTRAINT ck_stock_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE UNIQUE INDEX uq_stock_alert_open ON stock_alerts (product_variant_id, alert_type)
    WHERE status <> 'RESOLVED';
CREATE INDEX idx_stock_alerts_shop_status ON stock_alerts (shop_id, status, severity);
CREATE TRIGGER trg_stock_alerts_updated_at BEFORE UPDATE ON stock_alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- price_lists : explicit per-variant overrides (a supplier deal, a
-- seasonal wholesale sheet). Highest precedence when one matches.
-- ---------------------------------------------------------------------
CREATE TABLE price_lists
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    shop_id       UUID        NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    code          VARCHAR(48) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    pricing_flag  VARCHAR(20),
    customer_type VARCHAR(20),
    priority      INT         NOT NULL DEFAULT 100,
    valid_from    TIMESTAMPTZ,
    valid_to      TIMESTAMPTZ,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID
);

CREATE UNIQUE INDEX uq_price_lists_shop_code ON price_lists (shop_id, code);
CREATE TRIGGER trg_price_lists_updated_at BEFORE UPDATE ON price_lists
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE price_list_items
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    price_list_id      UUID          NOT NULL REFERENCES price_lists (id) ON DELETE CASCADE,
    product_variant_id UUID          NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    price              NUMERIC(14, 2) NOT NULL,
    min_quantity       INT           NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_list_item UNIQUE (price_list_id, product_variant_id, min_quantity),
    CONSTRAINT ck_price_list_item_price CHECK (price >= 0)
);

CREATE INDEX idx_price_list_items_variant ON price_list_items (product_variant_id);

-- ---------------------------------------------------------------------
-- price_rules : declarative pricing. Conditions are all optional and
-- ANDed together; NULL means "don't care". The engine picks the
-- highest-priority matching rule, so nothing is hard-coded in Java.
-- ---------------------------------------------------------------------
CREATE TABLE price_rules
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    shop_id          UUID          NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    description      VARCHAR(255),
    priority         INT           NOT NULL DEFAULT 100,

    -- Scope: what the rule applies to
    scope_type       VARCHAR(20)   NOT NULL DEFAULT 'ALL',
    scope_id         UUID,

    -- Conditions
    pricing_flag     VARCHAR(20),
    customer_type    VARCHAR(20),
    transaction_type VARCHAR(20),
    min_quantity     INT,
    min_stock_age_days INT,
    supplier_id      UUID REFERENCES suppliers (id) ON DELETE CASCADE,

    -- Effect
    strategy         VARCHAR(24)   NOT NULL,
    base_field       VARCHAR(20)   NOT NULL DEFAULT 'RETAIL_PRICE',
    amount           NUMERIC(14, 2),
    percentage       NUMERIC(6, 3),
    -- Never let a rule (or a manual discount) go below the floor price.
    respect_min_price BOOLEAN      NOT NULL DEFAULT TRUE,

    valid_from       TIMESTAMPTZ,
    valid_to         TIMESTAMPTZ,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,

    CONSTRAINT ck_price_rule_scope CHECK (scope_type IN ('ALL', 'CATEGORY', 'BRAND', 'PRODUCT', 'VARIANT')),
    CONSTRAINT ck_price_rule_flag CHECK (pricing_flag IS NULL OR pricing_flag IN
                                         ('NORMAL', 'WHOLESALE', 'REPAIR', 'VIP', 'CLEARANCE', 'OLD_STOCK', 'CUSTOM')),
    CONSTRAINT ck_price_rule_txn CHECK (transaction_type IS NULL OR transaction_type IN ('SALE', 'REPAIR', 'ESTIMATE')),
    CONSTRAINT ck_price_rule_strategy CHECK (strategy IN
                                             ('USE_FIELD', 'PERCENT_OFF', 'AMOUNT_OFF', 'MARKUP_ON_COST', 'FIXED_PRICE')),
    CONSTRAINT ck_price_rule_base CHECK (base_field IN
                                         ('COST_PRICE', 'RETAIL_PRICE', 'WHOLESALE_PRICE', 'REPAIR_PRICE',
                                          'MIN_PRICE', 'CLEARANCE_PRICE'))
);

CREATE INDEX idx_price_rules_shop_priority ON price_rules (shop_id, priority) WHERE active;
CREATE TRIGGER trg_price_rules_updated_at BEFORE UPDATE ON price_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
