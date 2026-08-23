-- Paid activation: entitlements can expire. Monthly plan is a first-class price.

ALTER TABLE workspace_entitlements
    ADD COLUMN expires_at TIMESTAMPTZ;

INSERT INTO billing_prices (plan_id, code, amount, currency, interval, entitlement)
SELECT id, 'WORKSPACE_MONTHLY', 199, 'INR', 'MONTHLY', 'SALES'
FROM billing_plans
WHERE code = 'PILOT'
  AND NOT EXISTS (SELECT 1 FROM billing_prices WHERE code = 'WORKSPACE_MONTHLY');
