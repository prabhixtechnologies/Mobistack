-- One live session per user. Extra shop screens are a paid add-on (₹50 each).
-- The first screen is included with the shop.

ALTER TABLE shops
    ADD COLUMN extra_screens INTEGER NOT NULL DEFAULT 0;

ALTER TABLE shops
    ADD CONSTRAINT ck_shops_extra_screens CHECK (extra_screens >= 0 AND extra_screens <= 49);

UPDATE shops
SET max_devices_per_user = 1;

INSERT INTO billing_plans (code, name, description, amount, currency, interval, sort_order, active)
SELECT 'EXTRA_SCREEN',
       'Extra screen',
       'One more person can sign in at the same time. The same login still cannot be open in two places.',
       50, 'INR', 'ONE_TIME', 90, TRUE
WHERE NOT EXISTS (SELECT 1 FROM billing_plans WHERE code = 'EXTRA_SCREEN');

INSERT INTO billing_prices (plan_id, code, amount, currency, interval, entitlement)
SELECT id, 'EXTRA_SCREEN', 50, 'INR', 'ONE_TIME', 'EXTRA_SCREEN'
FROM billing_plans
WHERE code = 'EXTRA_SCREEN'
  AND NOT EXISTS (SELECT 1 FROM billing_prices WHERE code = 'EXTRA_SCREEN');
