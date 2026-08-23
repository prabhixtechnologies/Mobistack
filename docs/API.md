# MobiStack API

Base path: `/api/v1`  
Auth: `Authorization: Bearer <accessToken>`  
Production: [https://mobistack.prabhixtechnologies.com](https://mobistack.prabhixtechnologies.com)  
Interactive docs (dev): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Every business request is scoped to the workspace encoded in the access token. A mismatched `X-MobiStack-Workspace` header is rejected. The previous `X-FixFlow-Workspace` header is still accepted.

## Authentication

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/auth/login` | Email + password. Access + rotating refresh token |
| POST | `/auth/register` | Create a user without a shop |
| POST | `/auth/register-shop` | Creates a shop and its first OWNER |
| POST | `/auth/magic-link` | Passwordless email link. The code is never returned in the API response |
| POST | `/auth/magic-link/consume` | Exchange the token for a session |
| POST | `/auth/email-otp` / `/auth/email-otp/verify` | Passwordless email code |
| POST | `/auth/phone/start` / `/auth/phone/verify` | SMS OTP via Twilio (creates the user if needed) |
| POST | `/auth/whatsapp/start` / `/auth/whatsapp/verify` | WhatsApp OTP via Twilio |
| POST | `/auth/sso/dev` | Local SSO stand-in (`fixflow.auth.dev-sso-enabled`) |
| GET | `/auth/sso/google/start` | Google authorization URL when a client id is configured |
| POST | `/auth/sso/google` | Exchange the Google code for a MobiStack session |
| GET | `/auth/methods` | Available methods + brand card |
| GET | `/public/brand` | Product name, organisation, tagline, copyright, public HTTPS origin |
| GET | `/public/platform` | Canonical URLs, support email, HTTPS flag |
| GET | `/public/app-release` | OTA channel + min native build (`platform`, `build`) |
| GET | `/auth/sessions` | Active devices for this account |
| DELETE | `/auth/sessions/{id}` | Revoke one device |
| POST | `/auth/refresh` | Replay of a used refresh token kills every session |
| POST | `/auth/forgot-password` | Sends reset instructions if the account exists |
| POST | `/auth/reset-password` | Consumes the reset token |
| POST | `/auth/request-otp` | SMS OTP via Twilio. The code is never returned in the API response |
| POST | `/auth/verify-otp` | Marks the phone verified (does not issue a session) |
| POST | `/auth/logout` | Revokes the refresh token |
| GET | `/auth/me` | Current user + permissions |
| POST | `/auth/change-password` | Ends every other session |

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
| GET | `/search?q=realme+6` | Devices, aliases, SKUs, barcodes, parts |
| GET | `/devices/{id}/compatibility?flag=NORMAL` | Compatible models + stock + price by category |
| GET | `/pricing/quote/{variantId}?flag=REPAIR` | Resolved unit price and why |

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
`POST /sync` accepts offline `SALE` / `RECEIVE` / `REPAIR` operations

## Billing and admin

`GET /billing` lists prices and entitlements.  
`POST /billing/orders` then `POST /billing/orders/{id}/confirm` captures in the DEV gateway.  
`POST /billing/webhooks/dev` is idempotent.  
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
