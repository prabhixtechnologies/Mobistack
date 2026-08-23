# MobiStack

*See what fits. See stock. Sell.*

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

## Local seed (development only)

Under the `dev` profile, an empty database gets a sample shop so the counter workflow can be tried. Seeded accounts use `@prabhixtechnologies.com`. Passwords live only in `backend/src/main/resources/application-dev.yml` and the seeder — they are never shown on the login screen, in the API, or in OTP responses.

Sign-in methods: email + password, magic link, email OTP, phone OTP, WhatsApp OTP, user registration, and new-shop registration. Google SSO exchanges an authorization code at `POST /auth/sso/google` when `FIXFLOW_GOOGLE_CLIENT_ID` / `FIXFLOW_GOOGLE_CLIENT_SECRET` are set.

SMS and WhatsApp OTPs go through **Twilio**. One-time codes are never returned in the API response.

A workspace is still the `shops` row — same UUID. Access is `User → Membership → Workspace`. Switching calls `POST /api/v1/workspaces/{id}/select` and issues a new JWT. Join-by-code creates a **PENDING** membership; an owner approves it from People.

## Prerequisites

- JDK 21 (the `pom.xml` targets 21; it also compiles on 17 if you pass `-Djava.version=17`)
- Maven 3.8+
- Node 20+
- PostgreSQL 16
- Docker optional — `docker compose -f docker-compose.yml -f docker-compose.local.yml up postgres redis` if you have it

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

## Laptop Docker

```powershell
docker compose --profile full -f docker-compose.yml -f docker-compose.local.yml up --build -d
```

Web: [http://localhost:4173](http://localhost:4173). API: [http://localhost:8080](http://localhost:8080).  
Do not start `--profile prod` / Caddy on the laptop.

## Production (Docker Hub → EC2)

Canonical origin: **https://mobistack.prabhixtechnologies.com**

Magic links, Google redirects, invoices, CORS, and release-app URLs use that host.

1. Test on the laptop with the command above.
2. `docker login` then `.\deploy\publish.ps1` — or push `master` and let GitHub Actions publish (secrets `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`).
3. On the EC2 that the subdomain points at: copy `deploy/.env.prod.example` to `.env`, then `./deploy/ec2-up.sh`.

EC2 only pulls images. It does not build Java or Node. Caddy terminates TLS (Let's Encrypt) on ports 80 and 443. Postgres and Redis are not published on the host.

Full runbook: [deploy/README.md](deploy/README.md).

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
