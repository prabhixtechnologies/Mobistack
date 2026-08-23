-- Seat fee so each workspace join is a separate ₹50 payment.
INSERT INTO billing_prices (plan_id, code, amount, currency, interval, entitlement)
SELECT id, 'WORKSPACE_JOIN', 50, 'INR', 'ONE_TIME', 'MEMBER_ADD'
FROM billing_plans
WHERE code = 'PILOT'
  AND NOT EXISTS (SELECT 1 FROM billing_prices WHERE code = 'WORKSPACE_JOIN');
