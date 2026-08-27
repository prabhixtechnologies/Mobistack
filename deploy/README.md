# Ship MobiStack: laptop → Docker Hub → EC2

Canonical site: **https://mobistack.prabhixtechnologies.com**

The laptop builds and tests. Docker Hub stores the images. EC2 only pulls and runs them. Java and Node are never compiled on the server.

Images (change the namespace if your Hub user is different):

- `prabhixtechnologies/mobistack-backend`
- `prabhixtechnologies/mobistack-web`

## 1. Test on this laptop

```powershell
docker compose --profile full -f docker-compose.yml -f docker-compose.local.yml up --build -d
```

Web: http://localhost:4173  
API: http://localhost:8080  

Do not start Caddy on the laptop (`--profile prod`).

## 2. Push images to Docker Hub

Create two Hub repositories (`mobistack-backend`, `mobistack-web`) if the first push does not create them.

```powershell
docker login
.\deploy\publish.ps1
```

That tags the git SHA and `latest`, then pushes both. Use `-SkipTests` only if you already ran tests. Override the Hub user with `$env:DOCKERHUB_NAMESPACE = "youruser"`.

Or push `master` to GitHub after adding repository secrets:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | Hub user (also the image namespace unless you set the variable below) |
| `DOCKERHUB_TOKEN` | Hub access token (not your password) |

Optional repository variable: `DOCKERHUB_NAMESPACE` if images live under an org that is not the login user.

The **Build and push images** workflow tests the backend, typechecks the web app, then publishes `latest` and the commit SHA.

## 3. First time on EC2

Security group: inbound **80** and **443** from the internet. SSH (22) only from your IP. Do not open 5432, 6379, 8080, or 4173.

Install Docker Engine + the Compose plugin + git, clone this repo, then:

```bash
cd /opt/mobistack   # or wherever you cloned
cp deploy/.env.prod.example .env
nano .env           # JWT secret ≥ 64 chars, a real Postgres password, ACME email
# If the Hub repos are private:
docker login
chmod +x deploy/ec2-up.sh
./deploy/ec2-up.sh
```

Caddy issues a Let's Encrypt certificate for `mobistack.prabhixtechnologies.com`. Use **https://** in the browser. HTTP on port 80 only exists so ACME and the HTTPS redirect work.

Production does **not** seed a demo shop. Register the first workspace from the site.

## 4. Every later release

On the laptop (or via GitHub Actions): test, then publish.

On EC2:

```bash
cd /opt/mobistack
git pull --ff-only          # only needed when compose/Caddy files change
./deploy/ec2-up.sh          # pulls IMAGE_TAG (default latest) and restarts
```

Pin a SHA to roll forward or back:

```bash
IMAGE_TAG=abc123def ./deploy/ec2-up.sh
```

Optional: GitHub **Deploy to EC2** (manual). Add secrets `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_APP_DIR` (absolute path of the clone).

## 5. Backups

Postgres lives in a Docker volume. Losing the instance or the volume loses every invoice, repair job, and stock balance in it, so a dump off the box is not optional.

`ec2-up.sh` takes one automatically before each deploy, because a new image runs its Flyway migrations on boot. Pass `SKIP_BACKUP=1` on the very first deploy, when there is no database yet.

Nightly, on the server:

```bash
crontab -e
# 15 2 * * * cd /opt/mobistack && bash deploy/db-backup.sh >> /var/log/mobistack-backup.log 2>&1
```

Dumps land in `./backups` (override with `BACKUP_DIR`) and the newest 14 are kept (`KEEP`). **Copy them off the instance** — to S3, or anywhere that is not the disk holding the database:

```bash
aws s3 sync ./backups s3://your-bucket/mobistack/ --storage-class STANDARD_IA
```

To restore:

```bash
bash deploy/db-restore.sh backups/mobistack-fixflow-20260827T021500Z.sql.gz
```

That stops the API, replaces the database, and starts it again. It asks you to type the database name first, because there is no undo.

Test a restore before you need one. An untested backup is a guess.
