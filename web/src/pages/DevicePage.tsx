import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, money, qty } from "../lib/api";
import { phoneLabel } from "../lib/compatibility";
import { ErrorState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import type { DeviceCompatibilityView, PricingFlag } from "../lib/types";

export function DevicePage() {
  const { id } = useParams();
  const [flag, setFlag] = useState<PricingFlag>("NORMAL");
  const [view, setView] = useState<DeviceCompatibilityView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openCategory, setOpenCategory] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    if (!id) {
      return;
    }
    let live = true;
    setError(null);
    api<DeviceCompatibilityView>(`/api/v1/devices/${id}/compatibility?flag=${flag}`)
      .then((payload) => {
        if (live) {
          setView(payload);
        }
      })
      .catch((err: Error) => {
        if (live) {
          setError(err.message);
        }
      });
    return () => {
      live = false;
    };
  }, [id, flag, nonce]);

  if (error) {
    return (
      <div className="page">
        <ErrorState message={error} onRetry={() => setNonce((value) => value + 1)} />
      </div>
    );
  }

  if (!view) {
    return (
      <div className="page">
        <div className="skeleton skeleton--title" />
        <div className="card">
          <div className="skeleton" />
          <div className="skeleton" />
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Catalog"
        title={phoneLabel(view.device)}
        subtitle={`${view.totalPartsAvailable} parts on the shelf · ${view.categoriesInStock} categories in stock`}
        actions={
          <label className="form-field" style={{ minWidth: 0, width: "100%", maxWidth: 180 }}>
            <span className="form-field__label">Pricing</span>
            <select
              className="select"
              value={flag}
              aria-label="Pricing list"
              onChange={(e) => setFlag(e.target.value as PricingFlag)}
            >
              <option value="NORMAL">Retail</option>
              <option value="WHOLESALE">Wholesale</option>
              <option value="REPAIR">Repair</option>
              <option value="VIP">VIP</option>
              <option value="CLEARANCE">Clearance</option>
            </select>
          </label>
        }
      />

      <div className="card">
        <div className="metric-label">Compatible models</div>
        <div className="chips" style={{ marginTop: 12 }}>
          <span className="chip">
            {phoneLabel(view.device)}
          </span>
          {view.compatibleModels.map((model) => (
            <span className="chip" key={model.id}>
              {phoneLabel(model)}
            </span>
          ))}
        </div>
        {view.device.aliases.length > 0 && (
          <p className="faint" style={{ marginBottom: 0 }}>
            Also known as {view.device.aliases.join(", ")}
          </p>
        )}
      </div>

      <section className="card tight">
        {view.categories.map((category) => {
          const expanded = openCategory === category.categoryId;
          return (
            <div key={category.categoryId}>
              <button
                className="category-row"
                type="button"
                aria-expanded={expanded}
                onClick={() => setOpenCategory(expanded ? null : category.categoryId)}
                style={{ width: "100%", background: "transparent", textAlign: "left" }}
              >
                <div>
                  <div style={{ fontWeight: 650 }}>{category.categoryName}</div>
                  <div className="faint">{category.variantCount} options</div>
                </div>
                <div>
                  <div style={{ fontWeight: 650 }}>{qty.format(category.totalAvailable)} in stock</div>
                  <div className="faint">
                    {category.minPrice != null
                      ? category.minPrice === category.maxPrice
                        ? money.format(category.minPrice)
                        : `${money.format(category.minPrice)} – ${money.format(category.maxPrice ?? 0)}`
                      : "No price"}
                  </div>
                </div>
                <span className={`badge ${category.stockStatus}`}>{category.stockStatus}</span>
              </button>
              {expanded &&
                category.options.map((option) => (
                  <div className="category-row" key={option.variantId} style={{ background: "var(--bg-muted)" }}>
                    <div>
                      <div style={{ fontWeight: 600 }}>{option.variantName}</div>
                      <div className="faint">
                        {option.sku}
                        {option.grade ? ` · ${option.grade}` : ""}
                        {option.quality ? ` · ${option.quality}` : ""}
                      </div>
                    </div>
                    <div>
                      <div style={{ fontWeight: 650 }}>{money.format(option.price)}</div>
                      <div className="faint">{qty.format(option.availableQty)} available</div>
                    </div>
                    <span className={`badge ${option.stockStatus}`}>{option.stockStatus}</span>
                    <Link className="btn ghost" to={`/sales?q=${encodeURIComponent(option.sku)}`}>
                      Sell
                    </Link>
                  </div>
                ))}
            </div>
          );
        })}
        {view.categories.length === 0 && (
          <div className="empty">No parts are linked to this phone yet. Add a compatibility group on the product.</div>
        )}
      </section>
    </div>
  );
}
