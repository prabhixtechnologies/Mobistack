# Ship MobiStack: CI → Amazon ECR → EC2

Canonical site: **https://mobistack.prabhixtechnologies.com**

CI builds and tests. Amazon ECR stores the images. EC2 only pulls and runs them. Java and Node are never compiled on the server.

Amazon ECR is the only registry. The Docker Hub account is gone, and nothing pushes to or pulls from it.

Images, under the shared `prabhix/` namespace in account `029096972251`, region `ap-south-1`:

- `029096972251.dkr.ecr.ap-south-1.amazonaws.com/prabhix/mobistack-backend`
- `029096972251.dkr.ecr.ap-south-1.amazonaws.com/prabhix/mobistack-web`

## 1. Test on this laptop

```powershell
docker compose --profile full -f docker-compose.yml -f docker-compose.local.yml up --build -d
```

Web: http://localhost:4173  
API: http://localhost:8080  

Do not start Caddy on the laptop (`--profile prod`).

## 2. Publish images

The usual route is to push `master` and let CI do it. **Build and verify** tests the backend, typechecks the web app, then publishes `latest` and the commit SHA to ECR.

Nothing needs configuring per repository: no registry secrets, no namespace variable. The workflow assumes `arn:aws:iam::029096972251:role/prabhix-github-ecr-push` through GitHub's OIDC provider, so no long-lived key exists to leak or rotate.

The ECR repositories `prabhix/mobistack-backend` and `prabhix/mobistack-web` must exist before the first push. `deploy/aws/ecr-create-repos.sh` in the Platform repository creates them.

When CI is unavailable, publish from a laptop instead:

```powershell
.\deploy\publish.ps1
```

That logs in to ECR with your local AWS credentials, tags the git SHA and `latest`, then pushes both. Use `-SkipTests` only if you already ran tests. Prefer CI: a laptop build is not reproducible the way the workflow is.

### The three workflows

Building and shipping are deliberately separate. A push proves a commit works; putting it in front of users is a decision someone makes afterwards.

| Workflow | Trigger | What it does |
| --- | --- | --- |
| **Build and verify** | every push to `master` | Guards, tests, typechecks, pushes images to ECR, builds the signed APK/AAB |
| **Deploy to EC2** | by hand | Restarts the server on a chosen build and replaces the APK the site offers |
| **Release to Google Play** | by hand | Uploads a build to a Play track |

Only a build that passed can be deployed. Images and the APK come out of one run of **Build and verify**, so the server and the download on the site are always the same commit. Choosing `latest` takes the most recent run that passed; a commit SHA takes that commit's run, and fails if it never had one.

## 3. First time on EC2

Security group: inbound **80** and **443** from the internet. SSH (22) only from your IP. Do not open 5432, 6379, 8080, or 4173.

Install Docker Engine + the Compose plugin + git, clone this repo, then:

```bash
cd /opt/mobistack   # or wherever you cloned
cp deploy/.env.prod.example .env
nano .env           # JWT secret ≥ 64 chars, a real Postgres password, ACME email
chmod +x deploy/ec2-up.sh
./deploy/ec2-up.sh
```

Caddy issues a Let's Encrypt certificate for `mobistack.prabhixtechnologies.com`. Use **https://** in the browser. HTTP on port 80 only exists so ACME and the HTTPS redirect work.

Production does **not** seed a demo shop. Register the first workspace from the site.

## 4. Every later release

Normally: wait for **Build and verify** to pass, then run **Deploy to EC2** and pick `latest`. That restarts the server and refreshes the APK download together. Clear `refresh_apk` to leave the download alone.

By hand on EC2, when the workflow is not an option:

```bash
cd /opt/mobistack
git pull --ff-only          # compose and the Caddyfile are read from here, not the image
bash deploy/ec2-up.sh       # pulls IMAGE_TAG (default latest) and restarts
```

Pin a SHA to roll forward or back. CI tags images `latest` and the **full**
40-character commit SHA, so the short SHA git prints is not a tag — expand it
first, or the pull fails as an unknown manifest:

```bash
IMAGE_TAG=$(git rev-parse HEAD) bash deploy/ec2-up.sh
```

The **Deploy to EC2** workflow does that expansion for you, so a short SHA is
fine there. It needs secrets `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, and
`EC2_APP_DIR` (absolute path of the clone).

When a run fails, read the **Annotations** box on the run summary rather than
hunting through the log: the remote output sits in a collapsed group, and the
workflow copies the tail of the failure up into an annotation for that reason.

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
bash deploy/db-restore.sh backups/mobistack-20260827T021500Z.sql.gz
```

That stops the API, replaces the database, and starts it again. It asks you to type the database name first, because there is no undo.

Test a restore before you need one. An untested backup is a guess.
