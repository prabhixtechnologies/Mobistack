"""Load MobiStack/catalog/phones.json into catalog_brands and catalog_devices.

Uses MOBISTACK_DATABASE_URL, for example:
  postgresql://mobistack:secret@host:5432/mobistack?sslmode=require

Idempotent: a phone that already exists for that brand in the original Fitment
catalog group is skipped. The file is that group's private list. This script
does not copy it into any other group, and it does not invent spare-part links.
"""
import json
import os
import sys
from pathlib import Path

PHONES = Path(__file__).with_name("phones.json")
DEFAULT_GROUP = "11111111-1111-4111-8111-111111111111"


def main() -> None:
    url = os.environ.get("MOBISTACK_DATABASE_URL", "").strip()
    if not url:
        sys.exit("Set MOBISTACK_DATABASE_URL to the mobistack database.")
    try:
        import psycopg
    except ImportError:
        sys.exit("pip install psycopg[binary]")

    payload = json.loads(PHONES.read_text(encoding="utf-8"))
    rows = payload.get("phones") or []
    inserted = 0
    skipped = 0
    with psycopg.connect(url) as conn:
        with conn.cursor() as cur:
            brands: dict[str, str] = {}
            seen_brands: set[str] = set()
            for row in rows:
                name = (row.get("b") or "").strip()
                if not name or name.casefold() in seen_brands:
                    continue
                seen_brands.add(name.casefold())
                cur.execute(
                    """
                    INSERT INTO catalog_brands (group_id, name)
                    SELECT %s, %s
                    WHERE NOT EXISTS (
                      SELECT 1 FROM catalog_brands
                      WHERE group_id = %s AND lower(name) = lower(%s)
                    )
                    """,
                    (DEFAULT_GROUP, name, DEFAULT_GROUP, name),
                )
            cur.execute(
                "SELECT id::text, name FROM catalog_brands WHERE group_id = %s",
                (DEFAULT_GROUP,),
            )
            for brand_id, name in cur.fetchall():
                brands.setdefault(name.casefold(), brand_id)
            already = 0
            for row in rows:
                brand = (row.get("b") or "").strip()
                phone = (row.get("n") or "").strip()
                brand_id = brands.get(brand.casefold())
                if not brand_id or not phone:
                    skipped += 1
                    continue
                code = ((row.get("c") or "").strip() or None)
                if code:
                    code = code[:60]
                year = row.get("y") if isinstance(row.get("y"), int) else None
                cur.execute(
                    """
                    INSERT INTO catalog_devices (group_id, brand_id, name, model_code, release_year)
                    SELECT %s, %s, %s, %s, %s
                    WHERE NOT EXISTS (
                      SELECT 1 FROM catalog_devices
                      WHERE group_id = %s AND brand_id = %s AND lower(name) = lower(%s)
                    )
                    """,
                    (DEFAULT_GROUP, brand_id, phone[:120], code, year, DEFAULT_GROUP, brand_id, phone),
                )
                if cur.rowcount:
                    inserted += 1
                else:
                    already += 1
        conn.commit()
    print(f"phones in file {len(rows)}; inserted {inserted}; already present {already}; skipped {skipped}")


if __name__ == "__main__":
    main()
