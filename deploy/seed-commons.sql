-- A starting point for the shared compatibility catalog.
--
-- The catalog_* tables are global — no shop_id — and Flyway leaves them empty. That is a problem on
-- a fresh database, because the COMPATIBILITY plan sells exactly one thing: type a phone, see which
-- parts fit it. On an empty catalog it sells a blank screen.
--
-- What this seeds, and what it deliberately does not:
--
--   It seeds the taxonomy. Brands, the models a repair counter in India actually sees, and for each
--   model the six parts that are replaceable on every smartphone. Each part is named for its own
--   model and fits only that model, so every fitment row here is true by construction rather than by
--   my say-so. A shop can search a phone and get a real parts list, and contributors have something
--   to attach to instead of an empty database.
--
--   It does not seed compatibility claims. "This folder also fits that model" is the valuable half
--   of the catalog and the half that costs a shop money when it is wrong — they order forty of a
--   part that does not fit. That belongs to people with the phones on the bench, through
--   ContributionService, not to a file written by someone without them. The handful of cross-model
--   rows at the end are the well-known ones, marked COMPATIBLE rather than EXACT.
--
--   Nothing here is marked verified. verified_by and verified_at stay null and confirmations stays
--   zero on every row, so the UI shows this as unconfirmed data, which is what it is. The first
--   shop to confirm a fitment on the bench is the first real signal the catalog gets.
--
--   psql "host=$RDS user=mobistack dbname=mobistack sslmode=require" \
--     -v ON_ERROR_STOP=1 -f deploy/seed-commons.sql
--
-- Safe to run twice, and safe to run on a catalog that shops have already added to: every insert is
-- guarded on the table's own natural key, so it fills gaps and touches nothing else.
--
-- normalized_name is a generated column on brands, devices and components. It is not inserted here
-- and cannot be — Postgres rejects a value for a GENERATED ALWAYS column.

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------------------------------
-- Brands
-- ---------------------------------------------------------------------------------------------

-- Sub-brands are listed separately because that is how they are sold and how a shop asks for them.
-- Redmi, Poco and iQOO are Xiaomi and Vivo companies; a counter still says "Redmi Note 12 folder".
INSERT INTO catalog_brands (name)
SELECT b.name
FROM (VALUES
    ('Apple'), ('Samsung'), ('Xiaomi'), ('Redmi'), ('Poco'), ('Realme'), ('Oppo'), ('Vivo'),
    ('iQOO'), ('OnePlus'), ('Motorola'), ('Nothing'), ('Infinix'), ('Tecno'), ('Lava'), ('Nokia')
  ) AS b(name)
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_brands x WHERE lower(x.name) = lower(b.name)
);

-- ---------------------------------------------------------------------------------------------
-- Models
-- ---------------------------------------------------------------------------------------------

-- Named the way a counter says them, which is not always brand plus model: "Galaxy A54", not
-- "Samsung Galaxy A54"; "Redmi Note 12", not "Redmi Redmi Note 12". Where the bare name is a letter
-- and a number and would be ambiguous across brands, the brand goes in front — "Oppo A17" and
-- "Vivo Y21" rather than "A17" and "Y21".
--
-- These names have to stay unambiguous, because the component names below are built from them and
-- the component index is global. Two brands with a model called "Note 12" would collide.
--
-- variant is left null throughout. Where a year distinguishes a model it is in the name, so
-- "iPhone SE 2020" rather than name "iPhone SE" with variant "2020" — otherwise both variants
-- generate the same component name.
CREATE TEMP TABLE seed_models (brand text, name text, model_code text, release_year int)
  ON COMMIT DROP;

INSERT INTO seed_models VALUES
  -- Apple. Model codes are the A-numbers printed on the back.
  ('Apple',    'iPhone XR',           'A1984',      2018),
  ('Apple',    'iPhone 11',           'A2221',      2019),
  ('Apple',    'iPhone SE 2020',      'A2275',      2020),
  ('Apple',    'iPhone 12',           'A2403',      2020),
  ('Apple',    'iPhone 12 Pro',       'A2407',      2020),
  ('Apple',    'iPhone 13',           'A2633',      2021),
  ('Apple',    'iPhone 14',           'A2882',      2022),
  ('Apple',    'iPhone 15',           'A3090',      2023),
  -- Samsung. SM- codes are the Indian variants where I am sure of them, null where I am not.
  ('Samsung',  'Galaxy A50',          'SM-A505F',   2019),
  ('Samsung',  'Galaxy M31',          'SM-M315F',   2020),
  ('Samsung',  'Galaxy S21',          'SM-G991B',   2021),
  ('Samsung',  'Galaxy A14',          'SM-A145F',   2023),
  ('Samsung',  'Galaxy A34',          'SM-A346B',   2023),
  ('Samsung',  'Galaxy A54',          'SM-A546E',   2023),
  ('Samsung',  'Galaxy M14',          NULL,         2023),
  ('Samsung',  'Galaxy S23',          'SM-S911B',   2023),
  -- Redmi
  ('Redmi',    'Redmi 9A',            NULL,         2020),
  ('Redmi',    'Redmi Note 10',       'M2101K7AI',  2021),
  ('Redmi',    'Redmi Note 10S',      NULL,         2021),
  ('Redmi',    'Redmi Note 11',       NULL,         2022),
  ('Redmi',    'Redmi Note 12',       NULL,         2023),
  ('Redmi',    'Redmi 12',            NULL,         2023),
  ('Redmi',    'Redmi Note 13',       NULL,         2024),
  -- Xiaomi and Poco
  ('Xiaomi',   'Xiaomi 13',           NULL,         2023),
  ('Poco',     'Poco M4 Pro',         NULL,         2021),
  ('Poco',     'Poco X5',             NULL,         2023),
  -- Realme. The 6 / 7 / Narzo 20 family is the one the compatibility feature was built around.
  ('Realme',   'Realme 6',            'RMX2001',    2020),
  ('Realme',   'Realme 7',            'RMX2151',    2020),
  ('Realme',   'Realme Narzo 20',     'RMX2193',    2020),
  ('Realme',   'Realme C11',          NULL,         2020),
  ('Realme',   'Realme C53',          NULL,         2023),
  ('Realme',   'Realme 11',           NULL,         2023),
  -- Oppo
  ('Oppo',     'Oppo A17',            NULL,         2022),
  ('Oppo',     'Oppo F21 Pro',        NULL,         2022),
  ('Oppo',     'Oppo A78',            NULL,         2023),
  -- Vivo and iQOO
  ('Vivo',     'Vivo Y20',            'V2027',      2020),
  ('Vivo',     'Vivo Y21',            NULL,         2021),
  ('Vivo',     'Vivo T2',             NULL,         2023),
  ('Vivo',     'Vivo V29',            NULL,         2023),
  ('iQOO',     'iQOO Z7',             NULL,         2023),
  -- OnePlus
  ('OnePlus',  'Nord CE 3',           NULL,         2023),
  ('OnePlus',  'Nord 3',              NULL,         2023),
  ('OnePlus',  'OnePlus 11R',         NULL,         2023),
  -- The rest
  ('Motorola', 'Moto G32',            NULL,         2022),
  ('Motorola', 'Moto G84',            NULL,         2023),
  ('Nothing',  'Nothing Phone (1)',   NULL,         2022),
  ('Nothing',  'Nothing Phone (2)',   NULL,         2023),
  ('Infinix',  'Infinix Hot 30',      NULL,         2023),
  ('Tecno',    'Tecno Spark 10',      NULL,         2023);

INSERT INTO catalog_devices (brand_id, name, model_code, release_year)
SELECT b.id, m.name, m.model_code, m.release_year
FROM seed_models m
JOIN catalog_brands b ON lower(b.name) = lower(m.brand)
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_devices x
  WHERE x.brand_id = b.id
    AND lower(x.name) = lower(m.name)
    AND lower(COALESCE(x.variant, '')) = ''
);

-- ---------------------------------------------------------------------------------------------
-- Parts
-- ---------------------------------------------------------------------------------------------

-- Six categories, applied to every model. Not an arbitrary six: these are the parts that exist on
-- every smartphone and are ordered as a single item, so "<model> Battery" is a real thing a shop can
-- buy for any model in the list above.
--
-- The categories left out are left out on purpose. A microphone is often part of the charging board
-- rather than a part of its own; a phone has three or four cameras and "Camera" would not say which;
-- touch/OCA and display connector only apply to panels that are separated rather than replaced whole;
-- middle frame is sometimes sold with the display and sometimes not. Guessing on any of those
-- produces a catalog entry nobody can order, which is worse than a gap.
--
-- category_code matches categories.code — the same codes as DefaultCategories, which is what every
-- shop's own category list starts from. There is no check constraint enforcing that, only this note.
CREATE TEMP TABLE seed_part_kinds (category_code text, suffix text, description text)
  ON COMMIT DROP;

INSERT INTO seed_part_kinds VALUES
  ('DISPLAY_FOLDER',    'Display Folder',
   'Complete display assembly: panel, touch and frame, replaced as one unit.'),
  ('TEMPERED_GLASS',    'Tempered Glass',
   'Screen protector cut for this model.'),
  ('BATTERY',           'Battery',
   'Replacement cell.'),
  ('BACK_COVER',        'Back Cover',
   'Rear panel or housing.'),
  ('CHARGING_BOARD',    'Charging Board',
   'Charging port sub-board or flex, usually with the USB connector on it.'),
  ('POWER_VOLUME_FLEX', 'Power Volume Flex',
   'Side button flex cable.');

INSERT INTO catalog_components (category_code, name, description)
SELECT p.category_code, d.name || ' ' || p.suffix, p.description
FROM catalog_devices d
CROSS JOIN seed_part_kinds p
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_components x
  WHERE x.category_code = p.category_code
    AND lower(x.name) = lower(d.name || ' ' || p.suffix)
);

-- Each part to the one model it is named for. EXACT because the part is defined by the model, not
-- claimed to fit it.
INSERT INTO catalog_fitments (component_id, device_id, fit_quality)
SELECT c.id, d.id, 'EXACT'
FROM catalog_devices d
CROSS JOIN seed_part_kinds p
JOIN catalog_components c
  ON c.category_code = p.category_code
 AND lower(c.name) = lower(d.name || ' ' || p.suffix)
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_fitments f WHERE f.component_id = c.id AND f.device_id = d.id
);

-- ---------------------------------------------------------------------------------------------
-- The cross-model rows
-- ---------------------------------------------------------------------------------------------

-- Where one part covers several models. This is the whole point of a shared catalog, and it is also
-- where being wrong is expensive, so what follows is short: the families that are sold as one part
-- across the counter, and nothing that needed a judgement call.
--
-- Every row is COMPATIBLE rather than EXACT, and unverified. A shop should see "reported to fit"
-- and check the connector, not "confirmed" and order fifty.
CREATE TEMP TABLE seed_cross (component_name text, category_code text, device_name text)
  ON COMMIT DROP;

INSERT INTO seed_cross VALUES
  -- Sold as one folder across the family: same 6.5" panel and connector. This is the family the
  -- product's own demo data is built on, so the claim is the repo's, not new here.
  ('Realme 6 Display Folder',  'DISPLAY_FOLDER',    'Realme 7'),
  ('Realme 6 Display Folder',  'DISPLAY_FOLDER',    'Realme Narzo 20'),
  ('Realme 6 Tempered Glass',  'TEMPERED_GLASS',    'Realme 7'),
  ('Realme 6 Tempered Glass',  'TEMPERED_GLASS',    'Realme Narzo 20'),
  -- iPhone 12 and 12 Pro are the same 6.1" assembly.
  ('iPhone 12 Display Folder', 'DISPLAY_FOLDER',    'iPhone 12 Pro'),
  ('iPhone 12 Tempered Glass', 'TEMPERED_GLASS',    'iPhone 12 Pro'),
  -- Redmi Note 10 and 10S share the folder.
  ('Redmi Note 10 Display Folder', 'DISPLAY_FOLDER', 'Redmi Note 10S'),
  ('Redmi Note 10 Tempered Glass', 'TEMPERED_GLASS', 'Redmi Note 10S');

INSERT INTO catalog_fitments (component_id, device_id, fit_quality)
SELECT c.id, d.id, 'COMPATIBLE'
FROM seed_cross s
JOIN catalog_components c
  ON c.category_code = s.category_code AND lower(c.name) = lower(s.component_name)
JOIN catalog_devices d ON lower(d.name) = lower(s.device_name)
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_fitments f WHERE f.component_id = c.id AND f.device_id = d.id
);

COMMIT;

-- ---------------------------------------------------------------------------------------------
-- What it built
-- ---------------------------------------------------------------------------------------------

\echo ''
\echo 'Catalog:'
SELECT
  (SELECT count(*) FROM catalog_brands)                                     AS brands,
  (SELECT count(*) FROM catalog_devices)                                    AS models,
  (SELECT count(*) FROM catalog_components)                                 AS parts,
  (SELECT count(*) FROM catalog_fitments)                                   AS fitments,
  (SELECT count(*) FROM catalog_fitments WHERE fit_quality = 'COMPATIBLE')   AS cross_model,
  (SELECT count(*) FROM catalog_fitments WHERE verified_at IS NOT NULL)      AS verified;
