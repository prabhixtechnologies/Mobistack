-- Extra screens are a monthly add-on. The seat stays only while this month is paid.

ALTER TABLE shops
    ADD COLUMN extra_screens_period_end TIMESTAMPTZ;

UPDATE billing_plans
SET interval = 'MONTHLY',
    description = '₹50 each month for one more person signed in at the same time. Miss a month and that screen turns off.'
WHERE code = 'EXTRA_SCREEN';

UPDATE billing_prices
SET interval = 'MONTHLY'
WHERE code = 'EXTRA_SCREEN';

-- Shops that already bought extras under the old one-time rule keep them for this month.
UPDATE shops
SET extra_screens_period_end = now() + interval '31 days'
WHERE extra_screens > 0
  AND extra_screens_period_end IS NULL;
