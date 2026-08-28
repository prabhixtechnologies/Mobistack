#!/usr/bin/env bash
# Drives the real text of the Deploy to EC2 remote script through the states the
# server has actually been in, with docker and curl stubbed so nothing touches
# production. Written after six deploys failed with no usable output; the first
# run of it found that a failed deploy exited 0 and reported the site live.
#
# Usage: bash deploy/test/deploy-workflow-cases.sh
set -uo pipefail
cd "$(dirname "$0")"

# Probe rather than trust `command -v`: on Windows, python3 resolves to a Store
# stub that exists, runs, and only prints an advert.
PY=""
for candidate in python3 python py; do
  if "$candidate" -c 'import sys; sys.exit(0)' >/dev/null 2>&1; then PY="$candidate"; break; fi
done
if [ -z "$PY" ]; then
  echo "SKIP: no working python on PATH, cannot extract the workflow script" >&2
  exit 0
fi

failures=0

root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT

origin="$root/origin.git"
git init -q --bare "$origin"

# A source tree standing in for the repo: only the pieces the remote script reads.
seed="$root/seed"
mkdir -p "$seed/deploy"
cp ../ec2-up.sh ../db-backup.sh ../db-restore.sh "$seed/deploy/"
cp ../../docker-compose.yml ../../docker-compose.prod.yml "$seed/"
(
  cd "$seed"
  git init -q
  git config user.email t@t.t
  git config user.name t
  git symbolic-ref HEAD refs/heads/master
  git add -A
  git commit -qm "seed"
  git push -q "$origin" master
) >/dev/null 2>&1
seed_sha=$(git -C "$seed" rev-parse HEAD)
seed_short=$(git -C "$seed" rev-parse --short HEAD)

# Stub docker and curl on PATH. compose is the only docker surface the script
# touches, and each stub can be told to fail to model a specific breakage.
stub="$root/bin"
mkdir -p "$stub"
cat > "$stub/docker" <<'STUB'
#!/usr/bin/env bash
case "$*" in
  *"compose"*"config"*)
    if [ -n "${STUB_CONFIG_FAILS:-}" ]; then
      echo 'validating docker-compose.prod.yml: error while interpolating services.backend.environment.SPRING_DATASOURCE_PASSWORD: required variable POSTGRES_PASSWORD is missing a value' >&2
      exit 1
    fi ;;
  *"compose"*"pull"*)
    if [ -n "${STUB_PULL_FAILS:-}" ]; then
      echo 'Error response from daemon: manifest for prabhixtechnologies/mobistack-backend:deadbeef not found' >&2
      exit 1
    fi
    echo "pulled ${IMAGE_TAG:-unset}" ;;
  *"compose"*"up"*)
    if [ -n "${STUB_UP_FAILS:-}" ]; then
      echo 'Error response from daemon: driver failed programming external connectivity' >&2
      exit 1
    fi ;;
  *"compose"*"logs"*) echo "stub backend log line" ;;
  *"ps"*"--format"*) : ;;   # no postgres container, so the backup is skipped
  *) : ;;
esac
exit 0
STUB
cat > "$stub/curl" <<'STUB'
#!/usr/bin/env bash
if [ -n "${STUB_HEALTH_FAILS:-}" ]; then exit 7; fi
for arg in "$@"; do
  case "$prev" in -D) printf 'HTTP/2 200\r\nx-request-id: abc123\r\n' > "$arg" ;; esac
  prev="$arg"
done
exit 0
STUB
chmod +x "$stub/docker" "$stub/curl"
export PATH="$stub:$PATH"

# A .env good enough for the script's own checks.
newenv() {
  printf 'POSTGRES_PASSWORD=testpassword\n' > "$1/.env"
  printf 'FIXFLOW_SECURITY_JWT_SECRET=%s\n' "$(printf '0123456789%.0s' {1..7})" >> "$1/.env"
  printf 'JAVA_OPTS=-Xms256m -Xmx512m\n' >> "$1/.env"
}

attempt() {
  local label="$1" appdir="$2" tag="$3"
  "$PY" extract-workflow-script.py "$appdir" "$tag" "$origin" "$root/remote.sh" >/dev/null || return 99
  # sleep is stubbed so the health-probe retry loop does not take three minutes.
  bash -c "sleep() { :; }; source '$root/remote.sh'" 2>&1
}

pass() { echo "PASS [$1]"; }
fail() { echo "FAIL [$1]"; failures=$((failures + 1)); }

report() {
  local label="$1" rc="$2" out="$3"; shift 3
  local ok=1 why=""
  for want in "$@"; do
    if ! printf '%s' "$out" | grep -qF -- "$want"; then ok=0; why="missing: $want"; fi
  done
  if [ "$ok" = 1 ]; then
    pass "$label"
  else
    fail "$label] rc=$rc $why"
    printf '%s\n' "$out" | sed 's/^/      | /'
  fi
}

# The exit status is what the workflow turns into a red or green run, so a case
# that produces the right text with the wrong status is still a failure.
expect_rc() {
  local label="$1" want="$2" got="$3"
  case "$want" in
    zero)    if [ "$got" -eq 0 ]; then pass "$label: exit 0"; else fail "$label: exit $got, wanted 0"; fi ;;
    nonzero) if [ "$got" -ne 0 ]; then pass "$label: exit $got"; else fail "$label: exit 0, wanted failure"; fi ;;
  esac
}

refute() {
  local label="$1" out="$2" unwanted="$3"
  if printf '%s' "$out" | grep -qF -- "$unwanted"; then fail "$label"; else pass "$label"; fi
}

echo "=== 1. non-git directory, the state the server was actually in"
app="$root/app1"; mkdir -p "$app"
cp ../../docker-compose.yml "$app/"        # a stale hand-copied snapshot
printf '#!/usr/bin/env bash\r\necho stale\r\n' > "$app/stale.sh"
newenv "$app"
out=$(attempt adopt "$app" latest); rc=$?
report "adopts the directory and reaches the end" "$rc" "$out" \
  "Previous config saved to" "syncing the checkout to origin/master" "New build is live"
expect_rc "adopt" zero "$rc"
if [ -f "$app/.env" ]; then pass ".env survived the hard reset"; else fail ".env was destroyed"; fi

echo
echo "=== 2. existing checkout, short SHA (the tag I told the user to use)"
app="$root/app2"; git clone -q "$origin" "$app"; newenv "$app"
out=$(attempt short "$app" "$seed_short"); rc=$?
report "expands the short SHA before pulling" "$rc" "$out" \
  "resolves to $seed_sha" "pulled $seed_sha" "New build is live"
expect_rc "short SHA" zero "$rc"

echo
echo "=== 3. local edits to tracked files, which is what broke git pull --ff-only"
app="$root/app3"; git clone -q "$origin" "$app"; newenv "$app"
printf 'echo tampered\n' >> "$app/deploy/ec2-up.sh"
printf 'tampered\n' >> "$app/docker-compose.yml"
out=$(attempt dirty "$app" latest); rc=$?
report "resets past local edits instead of failing" "$rc" "$out" "New build is live"
expect_rc "dirty tree" zero "$rc"
if git -C "$app" diff --quiet; then pass "tree is clean after sync"; else fail "tree still dirty"; fi

echo
echo "=== 4. compose config rejects .env: does the annotation carry the reason?"
app="$root/app4"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_CONFIG_FAILS=1 attempt cfg "$app" latest); rc=$?
report "annotates the compose error and does not claim success" "$rc" "$out" \
  "::error title=EC2 deploy failed::" "POSTGRES_PASSWORD is missing a value"
expect_rc "compose config" nonzero "$rc"
refute "does not claim the site is live after a failed deploy" "$out" "New build is live"

echo
echo "=== 5. image tag does not exist on the registry"
app="$root/app5"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_PULL_FAILS=1 attempt pull "$app" latest); rc=$?
report "annotates the pull failure and says nothing restarted" "$rc" "$out" \
  "::error title=EC2 deploy failed::" "Nothing was restarted"
expect_rc "bad tag" nonzero "$rc"
refute "does not claim the site is live after a failed pull" "$out" "New build is live"

echo
echo "=== 6. containers start but the API never answers"
app="$root/app6"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_HEALTH_FAILS=1 attempt health "$app" latest); rc=$?
report "annotates and includes the backend log" "$rc" "$out" \
  "::error title=EC2 deploy failed::" "stub backend log line"
expect_rc "dead API" nonzero "$rc"

echo
echo "=== 7. missing .env entirely"
app="$root/app7"; git clone -q "$origin" "$app"
out=$(attempt noenv "$app" latest); rc=$?
report "annotates the missing .env" "$rc" "$out" "::error title=EC2 deploy failed::" "Missing .env"
expect_rc "no .env" nonzero "$rc"

echo
echo "=== 7b. an unguarded command failing mid-deploy still names its phase"
app="$root/app7b"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_UP_FAILS=1 attempt up "$app" latest); rc=$?
report "ERR trap names the phase" "$rc" "$out" \
  "DEPLOY FAILED during: starting containers" "::error title=EC2 deploy failed::"
expect_rc "compose up" nonzero "$rc"

echo
echo "=== 8. the annotation must be a single line"
app="$root/app8"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_CONFIG_FAILS=1 attempt oneline "$app" latest)
ann=$(printf '%s\n' "$out" | grep -F '::error title=' | head -n 1)
count=$(printf '%s\n' "$out" | grep -cF '::error title=')
if [ "$count" = "1" ] && printf '%s' "$ann" | grep -q '%0A'; then
  pass "one folded annotation line, $(printf '%s' "$ann" | wc -c) chars"
else
  fail "expected exactly 1 folded annotation, found $count"
fi

echo
if [ "$failures" -eq 0 ]; then
  echo "All deploy workflow cases passed."
else
  echo "$failures deploy workflow assertion(s) failed." >&2
fi
exit $(( failures > 0 ? 1 : 0 ))
