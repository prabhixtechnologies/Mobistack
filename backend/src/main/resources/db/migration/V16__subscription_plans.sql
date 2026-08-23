-- Sellable plans with a feature checklist. A shop keeps access only while
-- the subscription period is live; a missed month turns the plan off.

ALTER TABLE billing_plans
    ADD COLUMN amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN interval VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE billing_plans
    ADD CONSTRAINT ck_plan_interval CHECK (interval IN ('ONE_TIME', 'MONTHLY', 'ANNUAL'));

CREATE TABLE plan_features
(
    plan_id      UUID        NOT NULL REFERENCES billing_plans (id) ON DELETE CASCADE,
    feature_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (plan_id, feature_code)
);

CREATE TABLE workspace_subscriptions
(
    workspace_id    UUID PRIMARY KEY REFERENCES shops (id) ON DELETE CASCADE,
    plan_id         UUID REFERENCES billing_plans (id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NONE',
    period_end      TIMESTAMPTZ,
    source_order_id UUID REFERENCES billing_orders (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_status CHECK (status IN ('NONE', 'ACTIVE', 'PAST_DUE'))
);

INSERT INTO billing_plans (code, name, description, amount, currency, interval, sort_order, active)
VALUES ('COMPATIBILITY', 'Compatibility',
        'Look up which parts fit a phone. Sales, repairs, and stock stay locked.',
        50, 'INR', 'MONTHLY', 10, TRUE),
       ('FULL_SHOP', 'Full shop',
        'The counter: sales, repairs, inventory, people, and reports.',
        199, 'INR', 'MONTHLY', 20, TRUE);

UPDATE billing_plans p
SET amount = x.amount,
    currency = x.currency,
    interval = 'MONTHLY'
FROM billing_prices x
WHERE (p.code = 'COMPATIBILITY' AND x.code = 'WORKSPACE_ACTIVATION')
   OR (p.code = 'FULL_SHOP' AND x.code = 'WORKSPACE_MONTHLY');

UPDATE billing_prices
SET plan_id = (SELECT id FROM billing_plans WHERE code = 'COMPATIBILITY')
WHERE code = 'WORKSPACE_ACTIVATION';

UPDATE billing_prices
SET plan_id = (SELECT id FROM billing_plans WHERE code = 'FULL_SHOP')
WHERE code = 'WORKSPACE_MONTHLY';

INSERT INTO plan_features (plan_id, feature_code)
SELECT id, 'COMPATIBILITY'
FROM billing_plans
WHERE code = 'COMPATIBILITY';

INSERT INTO plan_features (plan_id, feature_code)
SELECT p.id, f.feature_code
FROM billing_plans p
         CROSS JOIN (VALUES ('COMPATIBILITY'),
                            ('DASHBOARD'),
                            ('SALES'),
                            ('REPAIRS'),
                            ('INVENTORY'),
                            ('PURCHASES'),
                            ('CUSTOMERS'),
                            ('SUPPLIERS'),
                            ('MEMBERS'),
                            ('IMPORT'),
                            ('REPORTS'),
                            ('MOVEMENTS'),
                            ('AUDIT')) AS f(feature_code)
WHERE p.code = 'FULL_SHOP';

INSERT INTO workspace_subscriptions (workspace_id, plan_id, status, period_end)
SELECT s.id,
       p.id,
       'ACTIVE',
       COALESCE((SELECT MAX(e.expires_at)
                 FROM workspace_entitlements e
                 WHERE e.workspace_id = s.id
                   AND e.active),
                now() + INTERVAL '31 days')
FROM shops s
         JOIN billing_plans p ON p.code = CASE
                                              WHEN EXISTS (SELECT 1
                                                           FROM workspace_entitlements e
                                                           WHERE e.workspace_id = s.id
                                                             AND e.code = 'SALES'
                                                             AND e.active
                                                             AND (e.expires_at IS NULL OR e.expires_at > now()))
                                                  THEN 'FULL_SHOP'
                                              WHEN EXISTS (SELECT 1
                                                           FROM workspace_entitlements e
                                                           WHERE e.workspace_id = s.id
                                                             AND e.code = 'CATALOG'
                                                             AND e.active
                                                             AND (e.expires_at IS NULL OR e.expires_at > now()))
                                                  THEN 'COMPATIBILITY'
    END
WHERE p.id IS NOT NULL;
