# MobiStack web experience baseline

This is the implementation baseline for the shop-floor redesign. It is intentionally
short and testable: every migrated screen is reviewed against the same routes, states,
viewports, and interaction contract.

## Route families

- Entry: login, callback, password change, shop journey, workspace picker.
- Home: dashboard.
- Counter: sales, customers.
- Bench: repairs.
- Stock: inventory, catalog links, purchases, suppliers, movements.
- Catalog: shared browse/device/component, private fitment notes, contribution standing,
  and review.
- Insights: reports, audit, system health.
- Manage: team, access, workspaces, billing, settings, profile, notifications, support,
  and platform administration.
- Public: privacy, terms, refunds, and app downloads.

Each family must cover loading, first-run empty, filtered empty, populated, error, stale
or offline, unpaid, and read-only states where those states can occur.

## Viewport matrix

- 320 × 568: minimum-width phone.
- 390 × 844: current phone baseline.
- 768 × 1024: portrait tablet.
- 1024 × 768: landscape tablet.
- 1440 × 900: standard desktop.
- 1920 × 1080: wide desktop.
- Short landscape: any pointer or touch viewport no taller than 540px.

Run the matrix in light and dark themes. Operational surfaces use compact density; auth,
onboarding, legal, and billing must also remain valid at comfortable density and 200%
text zoom.

## Production observation, 2026-10-02

The production public pages were scrolled at desktop and 390 × 844. Legal copy has a
sound reading measure and reflows without page-level horizontal scrolling. App-download
cards stack correctly, but long raw package URLs dominate the cards and wrap poorly.

`/login?sso=0` initially remained on “Restoring your session”. The deployed bundle
contains both the restore and explicit sign-in-choice states, so this is an auth
initialisation/state-timing observation rather than proof of a source/deploy mismatch.
Regression captures must wait for auth readiness before asserting the final gateway.

Production asset observed: `assets/index-6K1uvd6_.js`.
Local source revision at audit: `0e95bbf`.

## Acceptance gates

- No unintended page-level horizontal scroll at 320px.
- Primary counter action is reachable within two taps from Home.
- Every page has one `h1`; asynchronous changes that matter are announced.
- Every list has visible row actions, keyboard access, and a touch route to the same
  verbs. Search and filter state survives reload through the URL.
- Motion is optional and collapses under `prefers-reduced-motion`.
- Loading geometry resembles loaded geometry; filtered empty states clear their filters.
- No serious axe violations in shared-shell and representative-page tests.
