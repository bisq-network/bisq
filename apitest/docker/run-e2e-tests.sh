#!/usr/bin/env bash
#
# Run Bisq DAO test suite end-to-end against docker-compose stack.
# Usable locally and from CI. Idempotent: rebuilds artifacts only if missing.
#
# Uses dao-setup.zip baked into the images: bitcoind seeded with chain tip @
# height 111 + genesis tx 30af00...03cf, Alice & Bob seeded with pre-synced
# bitcoinj wallet files holding their initial BTC/BSQ balances.
#
# Usage:
#   apitest/docker/run-e2e-tests.sh                       # build + up + test + down
#   apitest/docker/run-e2e-tests.sh --keep-up             # leave stack up after tests
#   apitest/docker/run-e2e-tests.sh --skip-build          # skip gradle install dist
#   apitest/docker/run-e2e-tests.sh --skip-docker-build   # skip docker image rebuild
#   apitest/docker/run-e2e-tests.sh --tests <pattern>
#
# Env overrides:
#   GRADLE         path to gradle wrapper (default: ./gradlew)
#   TEST_PATTERN   junit pattern (default: bisq.apitest.method.* bisq.apitest.dao.*)
#   GLOBAL_TIMEOUT_SECS  hard ceiling for whole run (default: 3600)
#   READY_TIMEOUT_SECS   per-daemon readiness wait (default: 300)
#   RUN_POLICY_E2E_TESTS  run deny-list policy phases after the main suite (default: true)
#   POLICY_DENY_LIST_RESOURCE  classpath fixture for policy phases
#   BITCOIND_RPC_HOST_PORT  host port published for bitcoind RPC (default: 18443)
#   BITCOIND_P2P_HOST_PORT  host port published for bitcoind P2P (default: 18444)

set -euo pipefail

KEEP_UP=0
SKIP_BUILD=0
SKIP_DOCKER_BUILD=0
# Space-separated list of patterns; each becomes its own --tests arg.
# `bisq.apitest.method.*` covers the read-only / idempotent method tests that
# were ported off the legacy Scaffold and re-enabled to run against this stack;
# `bisq.apitest.dao.*` covers DAO governance + v1 trade scenarios.
TEST_PATTERN="${TEST_PATTERN:-bisq.apitest.method.* bisq.apitest.dao.*}"
# Fresh-stack test discovery happens after SCRIPT_DIR/REPO_ROOT are set below.
# 2-core CI runners run the 5-container stack ~3-4x slower than a dev box and do 9
# fresh-stack resets on top of the shared phase, so a healthy run can approach 30min.
# 1800s sat right on that edge → legit runs tripped the ceiling. 3600s gives headroom
# while staying well under GitHub's 6h job cap. A trip now hard-fails (see TIMEOUT_MARKER).
GLOBAL_TIMEOUT_SECS="${GLOBAL_TIMEOUT_SECS:-3600}"
READY_TIMEOUT_SECS="${READY_TIMEOUT_SECS:-300}"
RUN_POLICY_E2E_TESTS="${RUN_POLICY_E2E_TESTS:-true}"
POLICY_DENY_LIST_RESOURCE="${POLICY_DENY_LIST_RESOURCE:-denylist/btc_regtest_e2e.denylist}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-up)           KEEP_UP=1; shift ;;
    --skip-build)        SKIP_BUILD=1; shift ;;
    --skip-docker-build) SKIP_DOCKER_BUILD=1; shift ;;
    --tests)
      [[ $# -ge 2 && -n "${2:-}" && "$2" != -* ]] \
          || { echo "[ERROR] --tests requires a non-empty pattern" >&2; exit 2; }
      TEST_PATTERN="$2"; shift 2 ;;
    -h|--help)     sed -n '3,/^$/p' "$0"; exit 0 ;;
    *) echo "[ERROR] unknown arg: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/dao-compose.yml"
# Optional compose override layered on top of the base file (e.g.
# dao-compose.compat.yml, which swaps one peer to the release image). Every
# `docker compose` call uses COMPOSE_ARGS so the override applies to up/down/build.
COMPOSE_OVERRIDE_FILE="${COMPOSE_OVERRIDE_FILE:-}"
COMPOSE_ARGS=(-f "${COMPOSE_FILE}")
if [[ -n "${COMPOSE_OVERRIDE_FILE}" ]]; then
  [[ -f "${COMPOSE_OVERRIDE_FILE}" ]] \
    || { echo "[ERROR] COMPOSE_OVERRIDE_FILE not found: ${COMPOSE_OVERRIDE_FILE}" >&2; exit 2; }
  COMPOSE_ARGS+=(-f "${COMPOSE_OVERRIDE_FILE}")
fi
LOGS_DIR="${SCRIPT_DIR}/logs"
GRADLE="${GRADLE:-${REPO_ROOT}/gradlew}"
PID_FILE="${PID_FILE:-/tmp/bisq-e2e-tests.pid}"
# Marker dropped by the global-timeout watchdog before it SIGTERMs us, so cleanup
# can distinguish a timeout-abort (must fail CI) from a clean exit. Without it the
# trap captures $? of whatever trivial command was mid-flight (often 0) and the
# aborted run reports success.
TIMEOUT_MARKER="${TIMEOUT_MARKER:-/tmp/bisq-e2e-tests.timedout}"
rm -f "${TIMEOUT_MARKER}"

# Auto-discover fresh-stack test classes from source: anything tagged
# `@Tag("freshstack")` in apitest/src/test/java/bisq/apitest. Single source of truth
# is the test source itself — no list to keep in sync. Each discovered class is run
# in a freshly reset stack via reset_stack; the shared-stack pass excludes them via
# JUnit Platform tag filter (-PexcludeFreshStack in apitest/build.gradle).
#
# Override the discovery with FRESH_STACK_TESTS=<space-separated FQCNs>; empty
# value = skip the fresh-stack phase entirely.
if [[ -n "${FRESH_STACK_TESTS+x}" ]]; then
  read -r -a FRESH_STACK_TESTS_ARR <<< "${FRESH_STACK_TESTS}"
else
  TEST_SRC="${REPO_ROOT}/apitest/src/test/java"
  mapfile -t FRESH_STACK_TESTS_ARR < <(
    grep -rlE '@Tag\("freshstack"\)' "${TEST_SRC}/bisq/apitest" 2>/dev/null \
      | sed -E "s|^${TEST_SRC}/||; s|\.java\$||; s|/|.|g" \
      | sort
  )
fi

# Refuse to run if another instance is alive (kill -0 confirms PID belongs to a live proc).
if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}" 2>/dev/null || echo 0)" 2>/dev/null; then
  echo "[ERROR] another run-e2e-tests.sh is already running (pid $(cat "${PID_FILE}"))" >&2
  echo "        kill it first or rm ${PID_FILE} if stale" >&2
  exit 3
fi
echo $$ > "${PID_FILE}"

# Install a minimal cleanup trap NOW — full `cleanup()` is defined further down,
# but any fatal/exit before then would otherwise leave a stale PID file and an
# orphan watchdog sleep. Replaced by the full trap at the bottom of the script.
early_cleanup() {
  rm -f "${PID_FILE}" "${TIMEOUT_MARKER}"
  if [[ -n "${TIMEOUT_PID:-}" ]]; then
    pkill -P "${TIMEOUT_PID}" 2>/dev/null || true
    kill "${TIMEOUT_PID}" 2>/dev/null || true
  fi
}
trap early_cleanup EXIT INT TERM HUP QUIT

# Compose project name pins container names so cleanup is precise.
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-bisq-e2e-tests}"

# Host ports the stack binds. Keep in sync with dao-compose.yml.
HOST_PORTS=("${BITCOIND_RPC_HOST_PORT:-18443}" "${BITCOIND_P2P_HOST_PORT:-18444}" 9997 9998 9999)
BISQ_CONTAINERS=(seednode arb alice bob)
ALL_CONTAINERS=(bitcoind "${BISQ_CONTAINERS[@]}")

cd "${REPO_ROOT}"

log()  { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
step() { echo; log "==> $*"; }
err()  { printf '[%s] [ERROR] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fatal() { err "$*"; exit 1; }

###############################################################################
# Stack lifecycle helpers (used by initial bring-up AND the fresh-stack reset
# loop). Defined up here so the cleanup trap + reset loop can both call them.
###############################################################################

cli_in() {
  local container="$1"; shift
  docker exec "${container}" /bisq/cli/bin/cli "$@"
}

container_status() {
  docker inspect --format '{{.State.Status}}' "$1" 2>/dev/null || true
}

assert_all_running() {
  local context="$1"
  for c in "${ALL_CONTAINERS[@]}"; do
    local s; s="$(container_status "${c}")"
    if [[ "${s}" != "running" ]]; then
      err "container '${c}' is in state '${s:-missing}' (expected running) during: ${context}"
      err "Last 80 log lines from ${c}:"
      docker logs --tail 80 "${c}" >&2 || true
      docker inspect --format='{{json .State.Health}}' "${c}" 2>&1 | tail -c 2000 >&2 || true
      fatal "container '${c}' is not running (${s:-missing}) — see logs above"
    fi
  done
}

# Stop containers + remove volumes + sweep any straggler containers by name.
teardown_stack() {
  docker compose "${COMPOSE_ARGS[@]}" down -v --remove-orphans 2>/dev/null || true
  # Belt-and-braces: catch any leftover containers (e.g. from a run with a different
  # COMPOSE_PROJECT_NAME) that would otherwise block the port reclaim below.
  for c in "${ALL_CONTAINERS[@]}"; do
    docker rm -f "$c" >/dev/null 2>&1 || true
  done
}

# Bring the compose stack up and block until every service reports healthy
# (or 'started' if no healthcheck). On failure, dump per-container state + logs
# and fatal-exit.
start_stack() {
  if ! docker compose "${COMPOSE_ARGS[@]}" up -d --wait --wait-timeout 240; then
    err "compose up failed; showing state + last 80 log lines for every container:"
    for c in "${ALL_CONTAINERS[@]}"; do
      local s; s="$(container_status "${c}")"
      err "----- ${c} (state=${s:-missing}) -----"
      docker inspect --format='{{json .State.Health}}' "${c}" 2>&1 | tail -c 1500 >&2 || true
      docker logs --tail 80 "${c}" >&2 || true
    done
    fatal "compose up failed — see per-container output above"
  fi
}

# Block until a single Bisq daemon answers `getversion` on its gRPC port. Catches
# stack crashes during the wait by re-checking every container's state each poll.
wait_daemon() {
  local label="$1" port="$2"
  local deadline=$(( $(date +%s) + READY_TIMEOUT_SECS ))
  while : ; do
    assert_all_running "waiting for ${label} gRPC"
    if cli_in "${label}" --port="${port}" --password=xyz getversion >/dev/null 2>&1; then
      log "  ${label} gRPC ready"
      return 0
    fi
    if [[ $(date +%s) -ge ${deadline} ]]; then
      err "${label} (:${port}) never reported ready within ${READY_TIMEOUT_SECS}s"
      err "Last 80 log lines from ${label}:"
      docker logs --tail 80 "${label}" >&2 || true
      fatal "${label} readiness timeout"
    fi
    sleep 3
  done
}

wait_for_daemons() {
  wait_daemon arb   9997
  wait_daemon alice 9998
  wait_daemon bob   9999
  assert_all_running "after daemon readiness"
}

# Full chain: teardown → bring up → wait healthy. Used by the fresh-stack loop
# between test classes that mutate persistent wallet/trade state.
reset_stack() {
  step "Tearing down stack"
  teardown_stack
  step "Bringing stack back up"
  start_stack
  step "Waiting for Bisq daemons (gRPC)"
  wait_for_daemons
}

# Global timeout: send SIGTERM to ourselves (triggers cleanup trap below) if total
# run exceeds GLOBAL_TIMEOUT_SECS. Using kill on $$ (not -$$) so trap fires before
# child processes get reaped.
( sleep "${GLOBAL_TIMEOUT_SECS}" && err "global timeout ${GLOBAL_TIMEOUT_SECS}s exceeded; aborting" && touch "${TIMEOUT_MARKER}" && kill -TERM $$ ) &
TIMEOUT_PID=$!

###############################################################################
# Preflight
###############################################################################

step "Preflight"

for bin in docker; do
  command -v "$bin" >/dev/null || fatal "required binary not found: $bin"
done
docker compose version >/dev/null 2>&1 || fatal "docker compose v2 plugin required"
docker info >/dev/null 2>&1 || fatal "docker daemon not reachable (is it running? user in 'docker' group?)"
[[ -x "${GRADLE}" ]] || fatal "gradle wrapper not executable at ${GRADLE}"

port_owner() {
  local p="$1"
  if command -v ss >/dev/null; then
    ss -lnpt "sport = :${p}" 2>/dev/null | awk 'NR>1 {print $0; exit}'
  elif command -v lsof >/dev/null; then
    lsof -iTCP:"${p}" -sTCP:LISTEN -n -P 2>/dev/null | awk 'NR>1 {print $0; exit}'
  fi
}

CONFLICTS=0
for p in "${HOST_PORTS[@]}"; do
  owner="$(port_owner "$p" || true)"
  if [[ -n "${owner}" ]]; then
    # If our own prior stack owns it, cleanup below handles it. Anything else: hard fail.
    if docker ps --format '{{.Names}} {{.Ports}}' | grep -E "(^| )(${BISQ_CONTAINERS[*]// /|}|bitcoind) " | grep -q ":${p}->"; then
      log "port ${p} held by our own stale container, will reclaim"
    else
      err "port ${p} already in use by:"
      err "  ${owner}"
      CONFLICTS=1
    fi
  fi
done
[[ "${CONFLICTS}" -eq 1 ]] && fatal "free the ports above (or stop the process holding them) and rerun"

###############################################################################
# Clean prior state (containers, networks, dao-setup staging)
###############################################################################

step "Cleaning prior stack state"
teardown_stack

###############################################################################
# Build artifacts
###############################################################################

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  step "Building install dists + dao-setup"
  "${GRADLE}" --no-daemon \
    :seednode:installDist :daemon:installDist :cli:installDist \
    :apitest:installDaoSetup \
    || fatal "gradle build failed"
fi

###############################################################################
# Stage dao-setup
###############################################################################

# Extract dao-setup.zip into apitest/src/main/resources so the Dockerfiles can
# COPY from it. Required for docker image build only; if we are skipping the
# docker build we trust the previously-built image and don't need source files.
src="${REPO_ROOT}/apitest/src/main/resources"
REQUIRED_DAO_DIRS=(Bitcoin-regtest bisq-BTC_REGTEST_Alice_dao bisq-BTC_REGTEST_Bob_dao)
needs_dao_setup=0
if [[ "${SKIP_DOCKER_BUILD}" -eq 0 ]]; then
  needs_dao_setup=1
fi

if [[ "${needs_dao_setup}" -eq 1 ]]; then
  zip_dst="${src}/dao-setup.zip"
  if [[ ! -f "${zip_dst}" ]]; then
    if [[ -f "${REPO_ROOT}/docs/dao-setup.zip" ]]; then
      mkdir -p "${src}"
      cp "${REPO_ROOT}/docs/dao-setup.zip" "${zip_dst}"
    else
      fatal "dao-setup.zip not found at ${REPO_ROOT}/docs/dao-setup.zip"
    fi
  fi
  missing=0
  for d in "${REQUIRED_DAO_DIRS[@]}"; do
    [[ -d "${src}/${d}" ]] || { missing=1; break; }
  done
  if [[ "${missing}" -eq 1 ]]; then
    command -v unzip >/dev/null || fatal "unzip required to extract dao-setup.zip"
    log "extracting dao-setup.zip → ${src}"
    tmp="$(mktemp -d)"
    unzip -q "${zip_dst}" -d "${tmp}"
    # zip layout: dao-setup/<dir>/...
    if [[ -d "${tmp}/dao-setup" ]]; then
      cp -r "${tmp}/dao-setup/." "${src}/"
    else
      cp -r "${tmp}/." "${src}/"
    fi
    rm -rf "${tmp}"
  fi
  for d in "${REQUIRED_DAO_DIRS[@]}"; do
    [[ -d "${src}/${d}" ]] || fatal "dao-setup still missing ${d} after extract"
  done
fi

###############################################################################
# Cleanup trap
###############################################################################

cleanup() {
  local exit_code=$?
  # A timeout-abort SIGTERMs us mid-command; $? is then whatever trivial command was
  # running (often 0). Force a non-zero code so the aborted run fails CI.
  if [[ -f "${TIMEOUT_MARKER}" ]]; then
    exit_code=124
    err "run aborted by global timeout — forcing exit ${exit_code}"
  fi
  # Disable the trap so re-entrant signals (e.g. user hammers Ctrl-C) don't loop.
  trap - EXIT INT TERM HUP QUIT
  # Cancel the global-timeout watchdog so it doesn't fire mid-cleanup. Also kill its
  # child sleep — otherwise the orphaned sleep keeps the script's process group alive
  # and delays the parent shell's notification of exit.
  pkill -P ${TIMEOUT_PID} 2>/dev/null || true
  kill ${TIMEOUT_PID} 2>/dev/null || true
  # Idempotent: a second signal would otherwise re-enter cleanup.
  if [[ "${CLEANUP_DONE:-0}" == "1" ]]; then exit "${exit_code}"; fi
  CLEANUP_DONE=1
  # Kill any direct children (gradle, docker compose) we still own. SIGTERM so
  # gradle's JVM can flush; final cleanup below will SIGKILL stragglers via compose.
  for pid in $(jobs -p); do kill "${pid}" 2>/dev/null || true; done
  step "Collecting container logs (exit=${exit_code})"
  mkdir -p "${LOGS_DIR}"
  for c in "${ALL_CONTAINERS[@]}"; do
    docker logs "$c" > "${LOGS_DIR}/${c}.log" 2>&1 || true
  done
  if [[ "${KEEP_UP}" -eq 0 ]]; then
    step "Tearing down stack"
    teardown_stack
  else
    log "Stack left running (--keep-up). Tear down with:"
    log "  docker compose ${COMPOSE_ARGS[*]} down -v"
  fi
  rm -f "${PID_FILE}" "${TIMEOUT_MARKER}"
  log "==> Exiting with code ${exit_code}"
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM HUP QUIT

###############################################################################
# Build images + initial bring up
###############################################################################

if [[ "${SKIP_DOCKER_BUILD}" -eq 1 ]]; then
  step "Skipping docker build (--skip-docker-build); verifying images present"
  REQUIRED_IMAGES=(bisq-bitcoind:ci bisq-daemon:ci)
  # The compat override references bisq-daemon:release; it has no build: stanza so
  # it must already exist (built by the compat workflow before invoking us).
  [[ -n "${COMPOSE_OVERRIDE_FILE}" ]] && REQUIRED_IMAGES+=(bisq-daemon:release)
  for img in "${REQUIRED_IMAGES[@]}"; do
    docker image inspect "${img}" >/dev/null 2>&1 \
      || fatal "image '${img}' missing; rerun without --skip-docker-build"
  done
else
  step "Building docker images"
  docker compose "${COMPOSE_ARGS[@]}" build || fatal "image build failed"
  for img in bisq-bitcoind:ci bisq-daemon:ci; do
    docker image inspect "${img}" >/dev/null 2>&1 \
      || fatal "image '${img}' not present after build (check compose 'image:' / 'build:' fields)"
  done
fi

step "Starting compose stack (with bitcoind healthcheck gating Bisq containers)"
start_stack
step "Waiting for Bisq daemons (gRPC)"
wait_for_daemons

###############################################################################
# Run tests
###############################################################################

step "Running shared-stack tests: ${TEST_PATTERN}"
# Expand each space-delimited pattern into its own `--tests` arg so Gradle can OR them.
TESTS_ARGS=()
for p in ${TEST_PATTERN}; do
  TESTS_ARGS+=(--tests "${p}")
done
# Phase 1 excludes @Tag("freshstack")-tagged tests via -PexcludeFreshStack.
# Phase 2 invocations (one per fresh-stack class) omit the property → tag included.
JVM_PROPS=(
  -DrunApiTests=true
  -DapiHost.alice=localhost -DapiPort.alice=9998
  -DapiHost.bob=localhost   -DapiPort.bob=9999
  -DapiHost.arb=localhost   -DapiPort.arb=9997
  -DapiPassword=xyz
  -DbitcoindContainer=bitcoind
)
set +e
"${GRADLE}" --no-daemon :apitest:test \
  "${TESTS_ARGS[@]}" \
  -PexcludeFreshStack \
  "${JVM_PROPS[@]}"
TEST_EXIT=$?
set -e

# Capture the shared-stack containers' logs NOW if the phase failed. The fresh-stack
# loop below tears this stack down on its first reset_stack, and the final cleanup()
# would otherwise only see the last reset's containers — losing the logs for the very
# failure we care about. Saved aside so cleanup()'s top-level dump doesn't clobber them.
if [[ ${TEST_EXIT} -ne 0 ]]; then
  err "shared-stack phase failed (exit ${TEST_EXIT}); capturing its container logs before reset"
  mkdir -p "${LOGS_DIR}/shared"
  for c in "${ALL_CONTAINERS[@]}"; do
    docker logs "$c" > "${LOGS_DIR}/shared/${c}.log" 2>&1 || true
  done
fi

# Fresh-stack phase: one stack reset per test class. Any failure here flips TEST_EXIT
# but lets the loop finish so we collect logs for every failing class, not just the first.
if [[ ${#FRESH_STACK_TESTS_ARR[@]} -gt 0 ]]; then
  for cls in "${FRESH_STACK_TESTS_ARR[@]}"; do
    step "Resetting stack for ${cls}"
    # reset_stack fatal-exits on its own bring-up failure; wrap so a stack-reset
    # failure flips TEST_EXIT and we continue on to collect logs for other classes
    # instead of taking the whole CI run down.
    if ! ( set -e; reset_stack ); then
      err "stack reset failed before ${cls}; skipping"
      TEST_EXIT=1
      continue
    fi
    step "Running fresh-stack test: ${cls}"
    set +e
    "${GRADLE}" --no-daemon :apitest:test \
      --tests "${cls}" \
      "${JVM_PROPS[@]}"
    CLS_EXIT=$?
    set -e
    # Copy this invocation's test report aside so the next gradle :apitest:test
    # call doesn't overwrite it. Without this only the last fresh-stack class's
    # HTML report would survive in apitest/build/reports/tests/test/.
    if [[ -d "${REPO_ROOT}/apitest/build/reports/tests/test" ]]; then
      mkdir -p "${REPO_ROOT}/apitest/build/reports/tests/freshstack/${cls}"
      cp -r "${REPO_ROOT}/apitest/build/reports/tests/test/." \
            "${REPO_ROOT}/apitest/build/reports/tests/freshstack/${cls}/" || true
    fi
    if [[ ${CLS_EXIT} -ne 0 ]]; then
      err "${cls} failed (exit ${CLS_EXIT}); collecting logs"
      mkdir -p "${LOGS_DIR}/freshstack/${cls}"
      for c in "${ALL_CONTAINERS[@]}"; do
        docker logs "$c" > "${LOGS_DIR}/freshstack/${cls}/${c}.log" 2>&1 || true
      done
      TEST_EXIT=${CLS_EXIT}
    fi
  done
fi

run_policy_tests() {
  local phase_name="$1"
  local extra_args="$2"
  local test_pattern="$3"

  step "Resetting stack for ${phase_name}"
  local old_extra_args="${BISQ_EXTRA_ARGS-}"
  local had_extra_args=0
  [[ -n "${BISQ_EXTRA_ARGS+x}" ]] && had_extra_args=1
  export BISQ_EXTRA_ARGS="${extra_args}"
  reset_stack
  if [[ "${had_extra_args}" -eq 1 ]]; then
    export BISQ_EXTRA_ARGS="${old_extra_args}"
  else
    unset BISQ_EXTRA_ARGS
  fi

  step "Running ${phase_name}: ${test_pattern}"
  set +e
  "${GRADLE}" --no-daemon :apitest:test \
    --tests "${test_pattern}" \
    "${JVM_PROPS[@]}"
  local phase_exit=$?
  set -e

  if [[ -d "${REPO_ROOT}/apitest/build/reports/tests/test" ]]; then
    mkdir -p "${REPO_ROOT}/apitest/build/reports/policy/${phase_name}"
    cp -r "${REPO_ROOT}/apitest/build/reports/tests/test/." \
      "${REPO_ROOT}/apitest/build/reports/policy/${phase_name}/" || true
  fi
  if [[ ${phase_exit} -ne 0 ]]; then
    err "${phase_name} failed (exit ${phase_exit}); collecting logs"
    mkdir -p "${LOGS_DIR}/policy/${phase_name}"
    for c in "${ALL_CONTAINERS[@]}"; do
      docker logs "$c" > "${LOGS_DIR}/policy/${phase_name}/${c}.log" 2>&1 || true
    done
    TEST_EXIT=${phase_exit}
  fi
}

if [[ "${RUN_POLICY_E2E_TESTS}" == "true" ]]; then
  run_policy_tests \
    "deny-list-blocks" \
    "--denyListResource=${POLICY_DENY_LIST_RESOURCE}" \
    "bisq.apitest.policy.DenyListBlocksCreateOfferTest"

  run_policy_tests \
    "deny-list-ignored" \
    "--denyListResource=${POLICY_DENY_LIST_RESOURCE} --ignoreDenyList=true" \
    "bisq.apitest.policy.IgnoreDenyListAllowsCreateOfferTest"
fi

if [[ ${TEST_EXIT} -ne 0 ]]; then
  err "tests failed (exit ${TEST_EXIT}). Inspect:"
  err "  apitest/build/reports/tests/test/index.html"
  err "  apitest/build/reports/tests/freshstack/"
  err "  apitest/build/reports/policy/"
  err "  ${LOGS_DIR}/{alice,bob,arb,seednode,bitcoind}.log"
  err "  ${LOGS_DIR}/freshstack/"
  err "  ${LOGS_DIR}/policy/"
fi

exit ${TEST_EXIT}
