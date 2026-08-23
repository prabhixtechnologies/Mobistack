-- ₹50 activation is a catalog plan: look up which parts fit a phone.
-- Sales, repairs, and stock stay behind the monthly full-shop plan.

INSERT INTO workspace_entitlements (workspace_id, code, active)
SELECT s.id, 'CATALOG', TRUE
FROM shops s
WHERE NOT EXISTS (
    SELECT 1
    FROM workspace_entitlements e
    WHERE e.workspace_id = s.id
      AND e.code = 'CATALOG'
);

UPDATE billing_prices
SET entitlement = 'CATALOG'
WHERE code = 'WORKSPACE_ACTIVATION';

-- Shops that never paid the monthly plan drop back to catalog-only.
UPDATE workspace_entitlements
SET active = FALSE
WHERE code IN ('SALES', 'INVENTORY', 'REPAIRS', 'MULTI_USER')
  AND active = TRUE
  AND workspace_id NOT IN (
      SELECT workspace_id
      FROM billing_orders
      WHERE price_code = 'WORKSPACE_MONTHLY'
        AND status = 'CAPTURED'
        AND workspace_id IS NOT NULL
  );
