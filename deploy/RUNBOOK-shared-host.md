# Running MobiStack on the shared Prabhix box

MobiStack no longer has a machine of its own. Its old instance is terminated and its stack runs on
`35.154.59.116` alongside the Prabhix platform, against the same RDS instance and its own Valkey
cache.

This is the measured state after the move, not a plan.

## What runs, and what does not

| Piece | Where |
| --- | --- |
| `mobistack-backend` | Container on the shared box, 640m limit |
| `mobistack-web` | Container on the shared box, 48m limit |
| Database | RDS `prabhix.crc86cio2bng.ap-south-1.rds.amazonaws.com`, database `mobistack`, role `mobistack` |
| Cache | `mobistack-fd3oni.serverless.aps1.cache.amazonaws.com:6379`, its own serverless Valkey |
| TLS and routing | The **platform's** Caddy, from `deploy/conf.d/mobistack.caddyfile` in the Platform repo |
| MobiStack's own `postgres` | Does not run. Parked behind the `never` profile |
| MobiStack's own `caddy` | Does not run. The platform's Caddy already holds 80 and 443 |

The database role is `mobistack`, not the RDS master. It owns the `mobistack` database and its public
schema, so Flyway can create objects, and it cannot read the platform's three other databases on the
same instance.

## Deploying

```bash
cd /opt/mobistack
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.shared.yml \
  --env-file .env up -d --pull never backend web
```

Three things about that command are deliberate.

All three compose files. `docker-compose.shared.yml` is what parks the local postgres and the second
Caddy, points the datasource at RDS, and joins the platform's network. Without it the stack tries to
start a database and to bind ports the platform's Caddy already holds.

`backend web` named explicitly, rather than `--profile prod`. Naming a service enables that service's
profile and nothing else, so the parked containers stay parked. `--profile prod` would bring the
second Caddy back.

`--pull never`, which is a workaround and should be deleted once ECR is in place. `mobistack-backend`
is a **private** Docker Hub repository and this box has no registry credential, so the pull fails —
and the failure mode is not an error. The prod overlay sets `pull_policy: always`, and when that pull
fails compose falls back to *building the image from source on the box*. On a 4 GiB production
instance that is the one thing the overlay's own header tells you not to do. It was caught in a
`--dry-run`, which is worth doing before any change to these files.

Until ECR exists, a new image reaches this box by hand:

```powershell
docker pull --platform linux/amd64 prabhixtechnologies/mobistack-backend:<sha>
docker save -o ms.tar prabhixtechnologies/mobistack-backend:<sha> `
                      prabhixtechnologies/mobistack-web:<sha>
scp -i <key> ms.tar prabhix@35.154.59.116:/tmp/
ssh ... 'docker load -i /tmp/ms.tar && rm /tmp/ms.tar'
```

CI tags MobiStack images with the **full** commit sha as well as `latest`. The platform's images use
the short sha, so a seven-character tag will not be found here. Do not pipe `docker save` through
PowerShell; it corrupts the stream and `docker load` reports `archive/tar: invalid tar header`.

## Why both products carry network aliases

Both name services `backend` and `web`. Docker registers a service name as a network alias on every
network the container joins and gives no way to opt out, so with both stacks on one network the name
`backend` resolves to two unrelated applications and a proxy round-robins between them.

So nothing routes on the bare names any more. The platform's compose adds `prabhix-backend` and
`prabhix-web`; this repository's shared overlay adds `mobistack-backend` and `mobistack-web`; the
platform's Caddyfile, its MobiStack fragment, and this repository's `web/nginx.conf` all target those.
The colliding aliases still exist, because Docker insists, and nothing depends on them.

If you add a service to either product, check whether the other has one by that name before wiring a
proxy to it.

## Public routing is not enabled yet, and why

`mobistack.prabhixtechnologies.com` resolves to **13.127.227.95**, the old instance's Elastic IP. That
address has been released: `aws ec2 describe-addresses --public-ips 13.127.227.95` answers
`InvalidAddress.NotFound`, so it is back in AWS's Mumbai pool and any account can allocate it.

That is worth treating as urgent rather than untidy. Whoever next gets that address can serve whatever
they like on a `prabhixtechnologies.com` hostname, and can obtain a publicly trusted certificate for
it through an ACME HTTP-01 challenge, because passing that challenge only requires answering on the
address the DNS record points to.

The fix is one record, in GoDaddy:

| Type | Name | Value |
| --- | --- | --- |
| A | `mobistack` | `35.154.59.116` |

`api.mobistack` already points at `35.154.59.116`.

Only after that record has propagated, enable the site on the box:

```bash
cd /opt/prabhix
mv deploy/conf.d/mobistack.caddyfile.example deploy/conf.d/mobistack.caddyfile
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file deploy/.env.prod \
  up -d --force-recreate caddy
```

Not before. Caddy requests a certificate for every name it is configured to serve, and while the
record points elsewhere those orders fail in a loop against a per-name weekly rate limit — which,
once exhausted, delays the working certificate too.

## Verifying

```bash
docker ps --filter name=mobistack
docker exec prabhix-caddy-1 wget -qO- http://mobistack-backend:8080/actuator/health
```

The second one is the useful check: it asks the question from where the proxy stands, so it fails if
the alias or the network is wrong rather than only if the application is.

For the database, `flyway_schema_history` is the assertion — 61 tables and a single successful
baseline is what a correct first start looks like:

```sql
SELECT max(version), bool_and(success) FROM flyway_schema_history;
```

## Memory

The box has 3815 MiB. With both stacks up, roughly 1500 MiB is in use and 2000 MiB is available, so
there is real headroom — but it exists only because MobiStack's postgres container is no longer
resident. Reclaiming that 512m is what made the arithmetic work. If you ever un-park that container,
this stops fitting.
