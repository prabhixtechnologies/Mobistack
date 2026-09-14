# MobiStack API

Base path: `/api/v1`  
Auth: `Authorization: Bearer <accessToken>`  
Production: [https://mobistack.prabhixtechnologies.com](https://mobistack.prabhixtechnologies.com)  
Interactive docs (dev): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Every business request is scoped to the workspace encoded in the access token. A mismatched `X-MobiStack-Workspace` header is rejected. The previous `X-FixFlow-Workspace` header is still accepted.

## Authentication

Sign-in is **Prabhix Identity** (OIDC + PKCE). This backend verifies RS256 access tokens via
`identity-spring-boot-starter`. It does not mint user tokens and has no password, magic-link, OTP
or Google endpoints.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/auth/me` | Current user + workspace permissions |
| POST | `/auth/logout` | Clears the local session cookie if one was issued |
| GET | `/public/brand` | Product name, organisation, tagline, copyright, public HTTPS origin |
| GET | `/public/platform` | Canonical URLs, support email, HTTPS flag |
| GET | `/public/app-release` | OTA channel + min native build (`platform`, `build`) |

Account changes (password, passkeys, email, sessions) go to Identity `/account`, not here.

## Workspaces

| Method | Path |
| --- | --- |
| GET | `/workspaces` |
| POST | `/workspaces` |
| POST | `/workspaces/join` |
| POST | `/workspaces/{id}/select` |
| GET | `/workspaces/{id}/members` |
| POST | `/workspaces/{id}/members/{membershipId}/approve` |
| POST | `/workspaces/{id}/members/{membershipId}/reject` |
| POST | `/workspaces/{id}/invitations` |
| POST | `/invitations/accept` |

## Flagship workflow

| Method | Path | What it answers |
| --- | --- | --- |
| GET | `/commons/devices?q=realme+6` | Shared Fitment Catalog — what phones exist |
| GET | `/commons/devices/{id}/fits` | What parts fit that phone |
| GET | `/search?q=realme+6` | Shared catalog hits, plus this shop's aliases, SKUs, barcodes, parts |
| GET | `/inventory/catalog-links/devices/{catalogDeviceId}/stock` | This shop's stock that fits the catalog phone |
| GET | `/devices/{id}/compatibility?flag=NORMAL` | This shop's private device graph + stock + price |
| GET | `/pricing/quote/{variantId}?flag=REPAIR` | Resolved unit price and why |

## Fitment Catalog (`/commons`)

The catalog is **global and free**. There is no shop id on these rows, and they are not gated on the
`COMPATIBILITY` plan. Anyone signed in may read them, including an unpaid workspace. Contributing
is also `isAuthenticated()`. Reviewing is not a shop permission: Prabhix grants it per user.

`GET /auth/me` includes `commonsReviewer` when the caller is in `commons_reviewers`.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/commons/stats` | Shop count + catalog sizes (for “shared with N shops”) |
| GET | `/commons/brands` | |
| GET | `/commons/devices` | `q`, optional `brandId`, page, size |
| GET | `/commons/devices/{id}` | |
| GET | `/commons/devices/{id}/fits` | Confirm/dispute counts, verified flag |
| GET | `/commons/components` | `q` optional |
| GET | `/commons/components/{id}` | |
| GET | `/commons/components/{id}/devices` | The other direction |
| POST | `/commons/contributions` | `kind`: `ADD_DEVICE`, `ADD_COMPONENT`, `ADD_FITMENT`, `CONFIRM_FITMENT`, `DISPUTE_FITMENT` |
| GET | `/commons/contributions/mine` | |
| GET | `/commons/standing` | Accepted / rejected / trusted / banned |
| GET | `/commons/review/queue` | Requires catalog review (table, not a shop role) |
| POST | `/commons/review/{id}/accept` | |
| POST | `/commons/review/{id}/reject` | |
| POST | `/commons/review/fitments/{id}/verify` | Settles a dispute |
| POST | `/commons/review/contributors/{userId}/trust` | |
| POST | `/commons/review/contributors/{userId}/ban` | |

Grant and revoke review (platform staff / BFF). The oneOps BFF is the intended writer; MobiStack
exposes both surfaces so a grant still works before that BFF client exists.

| Method | Path | Auth |
| --- | --- | --- |
| GET, POST | `/api/v1/admin/commons-reviewers` | Platform admin BFF (`X-Prabhix-Acting-User`) |
| POST | `/api/v1/admin/commons-reviewers/{userId}/grant` | same |
| POST | `/api/v1/admin/commons-reviewers/{userId}/revoke` | same |
| GET, POST | `/internal/admin/commons-reviewers` | Service token + acting user |
| POST | `/internal/admin/commons-reviewers/{userId}/grant` | same |
| POST | `/internal/admin/commons-reviewers/{userId}/revoke` | same |

`POST` body: `{ "userId", "reason" }`. Table PK is `user_id`.

Shop inventory points at catalog parts (these **are** tenant data and need inventory permission):

| Method | Path | Notes |
| --- | --- | --- |
| PUT | `/inventory/catalog-links/{variantId}` | `{ "componentId" }` |
| DELETE | `/inventory/catalog-links/{variantId}` | |
| GET | `/inventory/catalog-links/devices/{catalogDeviceId}/stock` | `INVENTORY_READ` |

Private per-shop groups remain at `/compatibility-groups`. `COMPATIBILITY_APPROVE` still approves
those notes. `GET /sync/snapshot` includes a `commons` slice: brands plus up to 200 popular devices.

## Commerce

`POST /sales` completes a sale, posts `OUT` movements, and captures payments.  
`POST /sales/{id}/void` restores stock.  
`GET /sales/{id}/invoice` returns printable HTML.  
`POST /purchases` receives supplier stock.  
`POST /repairs` opens a job; `POST /repairs/{id}/parts` consumes inventory.

## Parties, reports, import

`/customers` `/suppliers`  
`GET /reports?range=today`  
`GET /reports/export` (CSV)  
`POST /imports/compatibility` accepts `A = B = C`, CSV or JSON  
`GET /sync/snapshot` hydrates the native app cache.  
`POST /sync` accepts offline `SALE` / `RECEIVE` / `REPAIR` / `CUSTOMER` / `REPAIR_STATUS` operations

## Billing and admin

`GET /billing` lists prices, entitlements, and the public Razorpay key when configured.  
`POST /billing/orders` creates a Razorpay order and returns `{ order_id, amount (paise), currency, keyId }`.  
`POST /billing/verify` (also `/billing/verify-payment`) checks HMAC-SHA256(`order_id|payment_id`) against `KEY_SECRET` and marks paid only on a match.  
`POST /billing/orders/{id}/confirm` is a local DEV fallback and refuses Razorpay orders.  
`POST /billing/webhooks/dev` is idempotent and disabled in production.  
`/admin/workspaces` and `/admin/feature-flags` require `users.system_admin`.  
Also: `/admin/live`, `/admin/sessions/{userId}`, `/admin/support`, `/admin/app-releases`, `/admin/workspaces/{id}/device-limit`.

## Inbox, support, presence

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/inbox` | Unread count + last 50 |
| POST | `/inbox/{id}/read` | |
| POST | `/inbox/read-all` | |
| POST | `/inbox/push-token` | Expo push token for this device |
| POST | `/support/chat` | Chatbot; say “talk to a person” to escalate |
| POST | `/support/conversations` | Open a ticket to Prabhix |
| GET | `/support/conversations` | |
| POST | `/presence/heartbeat` | Live user (Redis, 90s TTL) |
| GET | `/presence` | Live users in this workspace (`USER_READ`) |
