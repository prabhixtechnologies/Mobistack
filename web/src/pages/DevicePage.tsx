import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, money, qty } from "../lib/api";
import type { DeviceCompatibilityView, PricingFlag } from "../lib/types";

export function DevicePage() {
  const { id } = useParams();
  const [flag, setFlag] = useState<PricingFlag>("NORMAL");
  const [view, setView] = useState<DeviceCompatibilityView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openCategory, setOpenCategory] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    api<DeviceCompatibilityView>(`/api/v1/devices/${id}/compatibility?flag=${flag}`)
      .then(setView)
      .catch((err: Error) => setError(err.message));
  }, [id, flag]);

  if (error) return <div className="page error">{error}</div>;
  if (!view) return <div className="page"><div className="skeleton" style={{ height: 48 }} /></div>;

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>
            {view.device.brandName} {view.device.name}
          </h1>
          <p>
            {view.totalPartsAvailable} parts on the shelf · {view.categoriesInStock} categories in stock
          </p>
        </div>
        <select className="select" value={flag} onChange={(e) => setFlag(e.target.value as PricingFlag)} style={{ width: 180 }}>
          <option value="NORMAL">Retail</option>
          <option value="WHOLESALE">Wholesale</option>
          <option value="REPAIR">Repair</option>
          <option value="VIP">VIP</option>
          <option value="CLEARANCE">Clearance</option>
        </select>
      </div>

      <div className="card">
        <div className="metric-label">Compatible models</div>
        <div className="chips" style={{ marginTop: 12 }}>
          <span className="chip">
            {view.device.brandName} {view.device.name}
          </span>
          {view.compatibleModels.map((model) => (
            <span className="chip" key={model.id}>
              {model.brandName} {model.name}
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
        {view.categories.map((category) => (
          <div key={category.categoryId}>
            <button
              className="category-row"
              type="button"
              onClick={() => setOpenCategory(openCategory === category.categoryId ? null : category.categoryId)}
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
            {openCategory === category.categoryId &&
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
                  <Link className="btn ghost" to="/sales">Sell</Link>
                </div>
              ))}
          </div>
        ))}
        {view.categories.length === 0 && (
          <div className="empty">No parts are linked to this phone yet. Add a compatibility group on the product.</div>
        )}
      </section>
    </div>
  );
}
