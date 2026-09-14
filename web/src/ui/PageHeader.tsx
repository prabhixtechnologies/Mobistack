import type { ReactNode } from "react";

export function PageHeader({
  kicker,
  title,
  subtitle,
  actions,
  meta,
}: {
  kicker?: ReactNode;
  title: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  meta?: ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        {kicker ? <p className="page-kicker">{kicker}</p> : null}
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
        {meta ? <div className="page-title__meta">{meta}</div> : null}
      </div>
      {actions ? <div className="page-title__actions">{actions}</div> : null}
    </div>
  );
}
