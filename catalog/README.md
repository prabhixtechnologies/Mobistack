# Fitment catalog

Public phone specs for the shared MobiStack catalog. Built from GSMArena listing and spec pages on 22 Sep 2026. Not copied from any third-party parts app.

`phones.json` holds 3,164 phones from 2018 onward (brand, name, year, battery mAh, display size, resolution, one model code when a spec page was fetched).

Same inches or the same mAh is not a confirmed spare. Nacho and 4G/5G variants stay separate rows.

The shop app bundles this file and browses it by part type, then brand, with search. From a phone, a technician can record which other models take the same spare and share that group with the catalog. Shop stock stays in the shop database and is not in this file.

To copy the phones into the `mobistack` database, follow `Infra/docs/AWS-ACCESS.md`. From a machine that can already reach RDS:

```
pip install "psycopg[binary]"
$env:MOBISTACK_DATABASE_URL = "postgresql://mobistack:PASSWORD@HOST:5432/mobistack?sslmode=require"
py -3 MobiStack/catalog/load_phones.py
```
