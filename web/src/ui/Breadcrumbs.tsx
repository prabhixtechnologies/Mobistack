import { Link, useLocation } from "react-router-dom";
import { breadcrumbsFor } from "../lib/navigation";
import { Icon } from "./navIcons";

/**
 * Trail for the current route, derived from the nav catalog.
 *
 * `detailLabel` names the final segment on record pages, where the URL holds an
 * id the user has no way to recognise.
 */
export function Breadcrumbs({ detailLabel }: { detailLabel?: string }) {
  const { pathname } = useLocation();
  const crumbs = breadcrumbsFor(pathname, detailLabel);

  if (crumbs.length < 2) {
    return null;
  }

  return (
    <nav className="crumbs" aria-label="Breadcrumb">
      <ol>
        {crumbs.map((crumb, index) => (
          <li key={`${crumb.label}-${index}`}>
            {crumb.to ? (
              <Link to={crumb.to}>{crumb.label}</Link>
            ) : (
              <span aria-current="page">{crumb.label}</span>
            )}
            {index < crumbs.length - 1 && (
              <span className="crumbs__sep" aria-hidden>
                <Icon name="chevronRight" />
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
