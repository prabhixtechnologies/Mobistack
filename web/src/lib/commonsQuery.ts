/** Catalog filters live on `/commons` so a brand and a part type can combine. */
export function commonsBrowsePath(filters?: {
  brands?: string | null;
  category?: string | null;
}): string {
  const params = new URLSearchParams();
  const brands = clean(filters?.brands);
  const category = clean(filters?.category);
  if (brands) params.set("brands", brands);
  if (category) params.set("category", category);
  const query = params.toString();
  return query ? `/commons?${query}` : "/commons";
}

/** Old `/commons/brands|categories/...` addresses, mapped onto the query-param catalog. */
export function legacyCommonsTarget(pathname: string, search = ""): string | null {
  const category = /^\/commons\/categories\/([^/]+)\/?$/.exec(pathname);
  if (category) {
    return commonsBrowsePath({ category: decodeURIComponent(category[1]) });
  }
  const brand = /^\/commons\/brands\/([^/]+)\/?$/.exec(pathname);
  if (brand) {
    const params = new URLSearchParams(search.startsWith("?") ? search.slice(1) : search);
    return commonsBrowsePath({
      brands: decodeURIComponent(brand[1]),
      category: params.get("category"),
    });
  }
  return null;
}

function clean(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}
