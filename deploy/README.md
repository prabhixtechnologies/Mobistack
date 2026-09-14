# Shipping MobiStack

MobiStack runs on the same production host as every other Prabhix product, as the `mobistack`
profile of the one compose stack in the **Infra** repository. This repository builds the images and
owns the seed SQL; nothing in it reaches a server.

Canonical site: **https://mobistack.prabhixtechnologies.com**. Images, under the shared `prabhix/`
namespace in account `029096972251`, region `ap-south-1`:

- `029096972251.dkr.ecr.ap-south-1.amazonaws.com/prabhix/mobistack-backend`
- `029096972251.dkr.ecr.ap-south-1.amazonaws.com/prabhix/mobistack-web`

## Build

Push `main`. **Build and verify** (`.github/workflows/build.yml`) runs the guards and tests, pushes
both images tagged `latest`, the short commit sha and the full one, and writes the two tags to use
into the run summary. No registry secrets: the workflow assumes an IAM role through GitHub's OIDC
provider.

## Deploy

In the Infra repository: **Actions → Deploy → Run workflow**, with `mobistack_backend_tag` and
`mobistack_web_tag` set to the short sha from the build summary and every other tag left blank. That
runs `Infra/deploy/deploy.sh` on the host through Systems Manager — pull, health-gate the backend,
then the web, roll back on failure — and checks the public hosts afterwards. The admin console's
Promote button does the same thing.

Everything about how MobiStack runs in production — the compose services, the Caddy site blocks, the
`MOBISTACK_*` environment variables, its database and cache on RDS and ElastiCache, backups — is in
Infra: `docker-compose.yml`, `deploy/Caddyfile`, `deploy/.env.prod.example`, `deploy/RUNBOOK.md` and
`deploy/RUNBOOK-consolidate.md`.

## Local

The Infra compose stack runs MobiStack locally as well, next to Identity, which it signs in through:

```powershell
cd ..\Infra
$env:COMPOSE_PROFILES = "identity,mobistack"
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Web on http://localhost:5176, API on http://localhost:8082, Identity on http://localhost:8081. Or run
the API and the web app from source against a local Postgres — see the repository README.

## What is here

- `seed.sql` — the first account, for a database that has no way to create one. Applied by CI to
  the empty schema, and by hand to a new environment.
- `seed-commons.sql` — the shared fitment catalog's starting content.
- `smoke.ps1` — checks against a running instance, local or production.
