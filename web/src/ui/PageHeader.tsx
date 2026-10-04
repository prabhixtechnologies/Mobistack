import type { ReactNode } from "react";
import { Icon, type NavIconName } from "./navIcons";

export function PageHeader({
  kicker,
  title,
  subtitle,
  actions,
  meta,
  icon,
  avatar,
}: {
  kicker?: ReactNode;
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  meta?: ReactNode;
  icon?: NavIconName;
  avatar?: ReactNode;
}) {
  return (
    <div className={`page-title${icon || avatar ? " page-title--icon" : ""}`}>
      {avatar ? (
        <div className="page-title__avatar">{avatar}</div>
      ) : icon ? (
        <span className="page-title__icon" aria-hidden>
          <Icon name={icon} />
        </span>
      ) : null}
      <div className="page-title__copy">
        {kicker ? <p className="page-kicker">{kicker}</p> : null}
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
        {meta ? <div className="page-title__meta">{meta}</div> : null}
      </div>
      {actions ? <div className="page-title__actions">{actions}</div> : null}
    </div>
  );
}
