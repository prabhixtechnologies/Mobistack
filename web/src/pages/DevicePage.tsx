import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, money, qty } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { phoneLabel } from "../lib/compatibility";
import { hasFeature } from "../lib/plan";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import type { DeviceCompatibilityView, PricingFlag } from "../lib/types";

export function DevicePage() {
  const { user } = useAuth();
  const access = useAccess();
  const inventory = hasFeature(user, "INVENTORY") && access.has("INVENTORY_READ");
  const sales = hasFeature(user, "SALES") && access.has("SALES_READ");
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
    api<DeviceCompatibilityView>(`/api/v1/mobistack/devices/compatibility?id=${id}&flag=${flag}`)
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
    <div className="page device-detail">
      <PageHeader
        kicker="Catalog"
        title={phoneLabel(view.device)}
        subtitle={
          inventory
            ? `${view.totalPartsAvailable} parts on the shelf · ${view.categoriesInStock} categories in stock`
            : "Your shop’s phone record and compatible part groups."
        }
        actions={
          inventory ? <label className="form-field device-detail__pricing">
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
          </label> : undefined
        }
      />

      <div className="card">
        <div className="metric-label">Compatible models</div>
        <div className="chips device-detail__models">
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
          <p className="faint device-detail__aliases">
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
                data-category-trigger
              >
                <div>
                  <strong>{category.categoryName}</strong>
                  <div className="faint">{category.variantCount} options</div>
                </div>
                {inventory && (
                  <>
                    <div>
                      <strong>{qty.format(category.totalAvailable)} in stock</strong>
                      <div className="faint">
                        {category.minPrice != null
                          ? category.minPrice === category.maxPrice
                            ? money.format(category.minPrice)
                            : `${money.format(category.minPrice)} – ${money.format(category.maxPrice ?? 0)}`
                          : "No price"}
                      </div>
                    </div>
                    <span className={`badge ${category.stockStatus}`}>{category.stockStatus}</span>
                  </>
                )}
              </button>
              {expanded &&
                category.options.map((option) => (
                  <div className="category-row device-detail__option" key={option.variantId}>
                    <div>
                      <strong>{option.variantName}</strong>
                      <div className="faint">
                        {inventory ? option.sku : "Compatible option"}
                        {option.grade ? ` · ${option.grade}` : ""}
                        {option.quality ? ` · ${option.quality}` : ""}
                      </div>
                    </div>
                    {inventory && (
                      <>
                        <div>
                          <strong>{money.format(option.price)}</strong>
                          <div className="faint">{qty.format(option.availableQty)} available</div>
                        </div>
                        <span className={`badge ${option.stockStatus}`}>{option.stockStatus}</span>
                      </>
                    )}
                    {sales && (
                      <Link className="btn ghost" to={`/sales?q=${encodeURIComponent(option.sku)}`}>
                        Sell
                      </Link>
                    )}
                  </div>
                ))}
            </div>
          );
        })}
        {view.categories.length === 0 && (
          <EmptyState
            compact
            icon="box"
            title="No parts linked yet"
            hint={inventory
              ? "Add a compatibility group on the product so this phone shows stock and price."
              : "Add a private fitment note when you learn what fits this phone."}
          />
        )}
      </section>
    </div>
  );
}
