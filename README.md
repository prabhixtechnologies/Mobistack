# FixFlow

A product of **Prabhix Technologies Pvt Ltd**.  
*Building software that simplifies business.*

A premium mobile-repair shop platform. The product is built around one counter motion:

**search a phone → see what fits → see stock → see price → sell or use it**

This repository is a monorepo:

| Path | Stack | Role |
| --- | --- | --- |
| `backend/` | Java 21, Spring Boot 3.5, PostgreSQL, Flyway | API, domain, pricing, inventory ledger |
| `web/` | React 19, TypeScript, Vite | Owner / manager console |
| `mobile/` | React Native, Expo, TypeScript | Native Android + iOS counter app with SQLite offline cache |
| `docs/` | Markdown | API notes |

## What you can do today

1. Sign in as the demo shop owner.
2. Type **Realme 6** in search.
3. Open the device, see compatible models and live stock/prices.
4. Complete a sale or open a repair job. Both consume the inventory ledger.
5. Invite or approve people, import a universal list, and read today’s profit.

That is the product. Everything else exists to make that path faster.

## Demo shop

Seeded only under the `dev` profile, and only on an empty database.

| Role | Email | Password |
| --- | --- | --- |
| Owner | `owner@fixflow.app` | `Owner@123` |
| Manager | `manager@fixflow.app` | `Manager@123` |
| Technician | `tech@fixflow.app` | `Tech@123` |
| Staff | `staff@fixflow.app` | `Staff@123` |

Shop: **Mobile Care Hub**, Pune. The demo owner also belongs to **ABC Mobile Repair** (Mumbai). Dev OTP / email / WhatsApp codes are `123456`. Magic-link and password-reset tokens are written to the notification log.

Sign-in methods: email + password, magic link, email OTP, phone OTP, WhatsApp OTP, Dev SSO, user registration, and new-shop registration. Google SSO exchanges an authorization code at `POST /auth/sso/google` when `FIXFLOW_GOOGLE_CLIENT_ID` / `FIXFLOW_GOOGLE_CLIENT_SECRET` are set.

SMS and WhatsApp OTPs go through **Twilio** when `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and the matching From numbers are set. Without those, the API still issues the local dev code `123456`. See `.env.example`. For WhatsApp, the destination number must be in the Twilio sandbox (or a purchased WhatsApp sender).

A workspace is still the `shops` row — same UUID. Access is `User → Membership → Workspace`. Switching calls `POST /api/v1/workspaces/{id}/select` and issues a new JWT. Join-by-code creates a **PENDING** membership; an owner approves it from People.

## Prerequisites

- JDK 21 (the `pom.xml` targets 21; it also compiles on 17 if you pass `-Djava.version=17`)
- Maven 3.8+
- Node 20+
- PostgreSQL 16
- Docker optional — `docker compose up postgres` if you have it

### PostgreSQL without Docker

```sql
CREATE USER fixflow WITH PASSWORD 'fixflow';
CREATE DATABASE fixflow OWNER fixflow;
```

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
```

## Run the API

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The API listens on [http://localhost:8080](http://localhost:8080).  
Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

A JWT signing key is supplied by `application-dev.yml`. Every other profile refuses to start without `FIXFLOW_SECURITY_JWT_SECRET` (≥ 64 characters).

## Production site (HTTPS)

Canonical origin: **https://mobistack.prabhixtechnologies.com**

Magic links, Google redirects, invoices, CORS, and release-app URLs use that host. Local `dev` still uses `http://localhost:5173`.

```powershell
docker compose up postgres redis
# then, on a server with DNS pointed at this host:
docker compose --profile prod up --build
```

Caddy terminates TLS (Let's Encrypt). The web console is same-origin with `/api`. Redis holds live presence and short-lived caches.

Set `ACME_EMAIL` and `FIXFLOW_SECURITY_JWT_SECRET` in `.env`. See `.env.example`.

## Run the web console

```bash
cd web
npm install
npm run dev
```

Vite proxies `/api` to the backend. Open [http://localhost:5173](http://localhost:5173).

## Build the Android APK

This is a standalone native app (`app.prabhix.fixflow`), not Expo Go.

```powershell
cd mobile
npm install
npm run apk:debug
```

The file lands at `mobile/android/app/build/outputs/apk/debug/app-debug.apk`.  
`npx expo run:android` compiles and installs it on an emulator or USB phone.

The iOS Xcode project is in `mobile/ios/` (scheme **FixFlow**, bundle id `app.prabhix.fixflow`). It cannot be compiled on Windows. On a Mac: `cd mobile && npm install && cd ios && pod install && cd .. && npx expo run:ios`. For a USB iPhone use `npx expo run:ios --device` and open `ios/FixFlow.xcworkspace` in Xcode to pick a signing team. From Windows you can also run `npm run ios:eas` (simulator) or `npm run ios:eas-device` after `npx eas-cli login`.

Release builds talk to `https://mobistack.prabhixtechnologies.com`. Locally, `EXPO_PUBLIC_API_URL` overrides the default (`http://<expo-host>:8080`, or `http://10.0.2.2:8080` on the Android emulator, `http://localhost:8080` on the iOS simulator).

The app keeps a SQLite snapshot and queues sales / stock / repairs through `POST /api/v1/sync`. OTA JavaScript updates use the EAS `production` channel (`npx eas update --channel production`). When a native build is too old, the app blocks and opens the full APK/IPA download from `/app/android` or `/app/ios`. Platform admins set the floor under **Platform → releases**.

Each account is capped to a number of devices (default 3). Owners change it in Settings. Signing in on a new device drops the oldest session unless `FIXFLOW_DEVICE_OVER_LIMIT=reject`. Platform admins can watch live users and kick a device.

In-app Support is a chatbot that can escalate to Prabhix (`support@prabhixtechnologies.com`). Notifications land in the inbox and, when a push token is registered, on the phone.

© 2026 Prabhix Technologies Pvt Ltd. All rights reserved.

## Domain notes

**Compatibility is per part category, not per phone.** Realme 6, 6i and 7 share a display. They do not share a back cover.

**Stock is a ledger.** Receiving, selling, repairing and voiding all append a row. Offline clients send an idempotency key.

**Pricing is data.** Each variant has cost / retail / wholesale / repair / min. The engine clamps to `min_price`.

**Search is trigram + prefix.** Typing `realme 6` hits the model, its aliases, and every part linked through a compatibility group.

## Tests

```bash
cd backend
mvn test "-Djava.version=17"
```

Integration tests that need Docker are tagged `integration` and skipped unless you pass `-Pintegration`.
