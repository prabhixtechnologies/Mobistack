-- The join payment is the selected product plan, not a separate admission fee.
-- Compatibility is the entry product; Full shop is the complete counter.
UPDATE billing_plans
SET name = 'Compatibility',
    description = 'Check which parts fit each phone. Inventory, sales and repairs stay locked.',
    amount = 50.00,
    "interval" = 'MONTHLY'
WHERE code = 'COMPATIBILITY';

UPDATE billing_prices
SET amount = 50.00,
    "interval" = 'MONTHLY'
WHERE code = 'WORKSPACE_ACTIVATION';

UPDATE billing_plans
SET name = 'Full Inventory Management',
    description = 'Inventory, sales, repairs, customers, suppliers, reports and compatibility.',
    amount = 499.00,
    "interval" = 'MONTHLY'
WHERE code = 'FULL_SHOP';

UPDATE billing_prices
SET amount = 499.00,
    "interval" = 'MONTHLY'
WHERE code = 'WORKSPACE_MONTHLY';
