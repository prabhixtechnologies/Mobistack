import type { ReactNode } from "react";
import { Icon, type NavIconName } from "./navIcons";

/**
 * A titled surface: a heading row with optional tools on the right, then the body.
 * Lists, forms and report sections all sit in one of these so every page reads the same way.
 */
export function Panel({
  title,
  hint,
  icon,
  count,
  tools,
  flush = false,
  className,
  children,
  labelledBy,
}: {
  title: ReactNode;
  hint?: ReactNode;
  icon?: NavIconName;
  count?: ReactNode;
  tools?: ReactNode;
  /** Body without padding, for tables and row lists that draw their own edges. */
  flush?: boolean;
  className?: string;
  children?: ReactNode;
  labelledBy?: string;
}) {
  return (
    <section className={`panel${className ? ` ${className}` : ""}`} aria-labelledby={labelledBy}>
      <header className="panel__head">
        <div className="panel__title">
          {icon ? (
            <span className="panel__icon" aria-hidden>
              <Icon name={icon} />
            </span>
          ) : null}
          <div>
            <h2 id={labelledBy}>
              {title}
              {count != null ? <span className="panel__count">{count}</span> : null}
            </h2>
            {hint ? <p>{hint}</p> : null}
          </div>
        </div>
        {tools ? <div className="panel__tools">{tools}</div> : null}
      </header>
      <div className={flush ? "panel__body panel__body--flush" : "panel__body"}>{children}</div>
    </section>
  );
}

/** A labelled figure: icon, label, the number, and an optional line under it. */
export function Kpi({
  icon,
  label,
  value,
  foot,
  tone = "ochre",
}: {
  icon: NavIconName;
  label: string;
  value: ReactNode;
  foot?: ReactNode;
  tone?: "ochre" | "teal" | "indigo" | "green" | "rose" | "slate";
}) {
  return (
    <div className={`kpi kpi--${tone}`}>
      <div className="kpi__top">
        <span className="kpi__icon" aria-hidden>
          <Icon name={icon} />
        </span>
        <span className="kpi__label">{label}</span>
      </div>
      <div className="kpi__value">{value}</div>
      {foot ? <div className="kpi__foot">{foot}</div> : null}
    </div>
  );
}
