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
# The real backup wants a live Postgres container. Only whether the deploy calls
# it is in question here, so the seeded copy just says it ran. The script itself
# is exercised against a real container elsewhere.
printf '#!/usr/bin/env bash\necho "STUB BACKUP RAN"\n' > "$seed/deploy/db-backup.sh"
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
  *"ps"*"--filter"*"mobistack-postgres"*)
    # Named only when asked for: otherwise there is no database and the deploy
    # skips its pre-migration backup.
    if [ -n "${STUB_POSTGRES_UP:-}" ]; then echo mobistack-postgres; fi ;;
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
  "$PY" extract-workflow-script.py \
    --step "Pull and restart on EC2" --key script \
    --app-dir "$appdir" --tag "$tag" --origin "$origin" \
    --out "$root/remote.sh" >/dev/null || return 99
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
  "Previous config saved to" "syncing the checkout to origin/master" "New build is live" \
  "deploy finished with status 0"
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
echo "=== 8b. a database that is up gets backed up before migrations"
# The detection used to pipe `docker ps` into `grep -q`. grep exits on the first
# match, docker takes SIGPIPE, and pipefail turned the test false -- skipping the
# backup on precisely the deploy that migrates the schema.
app="$root/app8b"; git clone -q "$origin" "$app"; newenv "$app"
out=$(STUB_POSTGRES_UP=1 attempt backup "$app" latest); rc=$?
report "backs up when postgres is running" "$rc" "$out" \
  "backing up the database before migrations" "STUB BACKUP RAN" "New build is live"
expect_rc "backup path" zero "$rc"

echo
echo "=== 8c. no database means no backup attempt"
app="$root/app8c"; git clone -q "$origin" "$app"; newenv "$app"
out=$(attempt nobackup "$app" latest); rc=$?
refute "skips the backup when postgres is absent" "$out" "STUB BACKUP RAN"
expect_rc "no-backup path" zero "$rc"

echo
echo "=== 9. choosing which build to deploy"
# Runs the real text of the resolve step, with gh stubbed and a throwaway repo
# standing in for the checkout. Picking the wrong run here would put an older
# build live while reporting success, which no later check would catch.
if ! command -v jq >/dev/null 2>&1; then
  echo "SKIP: jq is not installed, cannot exercise the build-selection step"
else
  "$PY" extract-workflow-script.py \
    --step "Pick a build that passed" --key run --out "$root/pick.sh" >/dev/null

  history="$root/history"
  git init -q "$history"
  git -C "$history" config user.email t@t.t
  git -C "$history" config user.name t
  declare -a shas=()
  for n in 1 2 3; do
    echo "$n" > "$history/f"
    git -C "$history" add f
    git -C "$history" commit -qm "commit $n"
    shas+=("$(git -C "$history" rev-parse HEAD)")
  done
  built_old="${shas[0]}"
  unbuilt="${shas[1]}"
  built_new="${shas[2]}"

  # Newest first, as gh returns them, and deliberately missing the middle commit.
  printf '[{"databaseId":300,"headSha":"%s"},{"databaseId":100,"headSha":"%s"}]\n' \
    "$built_new" "$built_old" > "$root/runs.json"
  cat > "$stub/gh" <<STUB
#!/usr/bin/env bash
cat "$root/runs.json"
STUB
  chmod +x "$stub/gh"

  choose() {
    (
      cd "$history"
      export REQUESTED="$1" REPO="prabhixtechnologies/Mobistack" GH_TOKEN=stub
      export GITHUB_OUTPUT="$root/out.txt" GITHUB_STEP_SUMMARY="$root/summary.md"
      : > "$GITHUB_OUTPUT"
      : > "$GITHUB_STEP_SUMMARY"
      bash "$root/pick.sh" 2>&1
    )
  }
  chose() { sed -nE "s/^$1=(.*)/\1/p" "$root/out.txt"; }

  out=$(choose latest); rc=$?
  if [ "$rc" -eq 0 ] && [ "$(chose sha)" = "$built_new" ] && [ "$(chose run_id)" = "300" ]; then
    pass "latest picks the newest run that passed"
  else
    fail "latest gave rc=$rc sha=$(chose sha) run_id=$(chose run_id)"
    printf '%s\n' "$out" | sed 's/^/      | /'
  fi

  out=$(choose "${built_old:0:7}"); rc=$?
  if [ "$rc" -eq 0 ] && [ "$(chose sha)" = "$built_old" ] && [ "$(chose run_id)" = "100" ]; then
    pass "a short SHA picks that commit's run, not the newest"
  else
    fail "pinned commit gave rc=$rc sha=$(chose sha) run_id=$(chose run_id)"
    printf '%s\n' "$out" | sed 's/^/      | /'
  fi

  out=$(choose "$unbuilt"); rc=$?
  if [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -qF 'Nothing to deploy'; then
    pass "a commit with no successful build is refused"
  else
    fail "unbuilt commit gave rc=$rc out=$out"
  fi

  out=$(choose "definitely-not-a-ref"); rc=$?
  if [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -qF 'Unknown build'; then
    pass "an unknown ref is refused"
  else
    fail "unknown ref gave rc=$rc out=$out"
  fi

  echo '[]' > "$root/runs.json"
  out=$(choose latest); rc=$?
  if [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -qF 'Nothing to deploy'; then
    pass "no successful build at all is refused"
  else
    fail "empty history gave rc=$rc out=$out"
  fi

  rm -f "$stub/gh"
fi

echo
echo "=== 10. the ssh action must not be allowed to rewrite the script"
# script_stop sounds like errexit. What it actually does is split the script on
# newlines and inject an exit-code test after every line that does not end in a
# backslash. That has now broken two deploys: `case "$x" in` got a command
# injected where a pattern must go, which is a parse error the shell only reaches
# after the deploy has already run; and the line after a pipeline got one
# injected before it, so PIPESTATUS held the injected test's status instead of
# the pipeline's, and a clean deploy reported failure.
if grep -rnE '^[[:space:]]*script_stop[[:space:]]*:' ../../.github/workflows >/dev/null 2>&1; then
  fail "a workflow opts into script_stop, which rewrites multi-line scripts"
else
  pass "no workflow opts into script_stop"
fi

# Not a theoretical guard: apply the same rewrite and the script stops parsing.
awk '{
  line = $0
  sub(/^[[:space:]]+/, "", line); sub(/[[:space:]]+$/, "", line)
  if (line == "") next
  print line
  if (line !~ /\\$/) print "DRONE_SSH_PREV_COMMAND_EXIT_CODE=$? ; if [ $DRONE_SSH_PREV_COMMAND_EXIT_CODE -ne 0 ]; then exit $DRONE_SSH_PREV_COMMAND_EXIT_CODE; fi;"
}' "$root/remote.sh" > "$root/rewritten.sh"
if bash -n "$root/rewritten.sh" 2>/dev/null; then
  fail "the rewrite still parses, so this case no longer proves anything"
else
  pass "the rewrite breaks the script, which is what the server was reporting"
fi

# And the scripts we do send have to parse before a deploy finds out for us.
for step in "Pull and restart on EC2" "Replace the live download"; do
  if "$PY" extract-workflow-script.py --step "$step" --key script \
       --app-dir /opt/mobistack --tag latest --origin "$origin" \
       --out "$root/parse.sh" >/dev/null 2>&1 \
     && bash -n "$root/parse.sh" 2>"$root/parse.err"; then
    pass "$step parses"
  else
    fail "$step does not parse: $(tr -d '\r' < "$root/parse.err" | tr '\n' ' ')"
  fi
done

echo
if [ "$failures" -eq 0 ]; then
  echo "All deploy workflow cases passed."
else
  echo "$failures deploy workflow assertion(s) failed." >&2
fi
exit $(( failures > 0 ? 1 : 0 ))
