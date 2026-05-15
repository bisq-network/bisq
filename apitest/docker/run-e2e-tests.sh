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
#   TEST_PATTERN   junit pattern (default: bisq.apitest.dao.*)
#   GLOBAL_TIMEOUT_SECS  hard ceiling for whole run (default: 1800)
#   READY_TIMEOUT_SECS   per-daemon readiness wait (default: 300)

set -euo pipefail

KEEP_UP=0
SKIP_BUILD=0
SKIP_DOCKER_BUILD=0
TEST_PATTERN="${TEST_PATTERN:-bisq.apitest.dao.*}"
GLOBAL_TIMEOUT_SECS="${GLOBAL_TIMEOUT_SECS:-1800}"
READY_TIMEOUT_SECS="${READY_TIMEOUT_SECS:-300}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-up)           KEEP_UP=1; shift ;;
    --skip-build)        SKIP_BUILD=1; shift ;;
    --skip-docker-build) SKIP_DOCKER_BUILD=1; shift ;;
    --tests)
      [[ $# -ge 2 && -n "${2:-}" && "$2" != -* ]] \
          || { echo "[ERROR] --tests requires a non-empty pattern" >&2; exit 2; }
      TEST_PATTERN="$2"; shift 2 ;;
    -h|--help)     sed -n '3,21p' "$0"; exit 0 ;;
    *) echo "[ERROR] unknown arg: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/dao-compose.yml"
LOGS_DIR="${SCRIPT_DIR}/logs"
GRADLE="${GRADLE:-${REPO_ROOT}/gradlew}"
PID_FILE="${PID_FILE:-/tmp/bisq-e2e-tests.pid}"

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
  rm -f "${PID_FILE}"
  if [[ -n "${TIMEOUT_PID:-}" ]]; then
    pkill -P "${TIMEOUT_PID}" 2>/dev/null || true
    kill "${TIMEOUT_PID}" 2>/dev/null || true
  fi
}
trap early_cleanup EXIT INT TERM HUP QUIT

# Compose project name pins container names so cleanup is precise.
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-bisq-e2e-tests}"

# Host ports the stack binds. Keep in sync with dao-compose.yml.
HOST_PORTS=(18443 9997 9998 9999)
BISQ_CONTAINERS=(seednode arb alice bob)
ALL_CONTAINERS=(bitcoind "${BISQ_CONTAINERS[@]}")

cd "${REPO_ROOT}"

log()  { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
step() { echo; log "==> $*"; }
err()  { printf '[%s] [ERROR] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
fatal() { err "$*"; exit 1; }

# Global timeout: send SIGTERM to ourselves (triggers cleanup trap below) if total
# run exceeds GLOBAL_TIMEOUT_SECS. Using kill on $$ (not -$$) so trap fires before
# child processes get reaped.
( sleep "${GLOBAL_TIMEOUT_SECS}" && err "global timeout ${GLOBAL_TIMEOUT_SECS}s exceeded; aborting" && kill -TERM $$ ) &
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
docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans 2>/dev/null || true
# Belt-and-braces: also remove any leftover containers by name (e.g. someone ran without COMPOSE_PROJECT_NAME).
for c in "${ALL_CONTAINERS[@]}"; do
  docker rm -f "$c" >/dev/null 2>&1 || true
done

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
    docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans || true
  else
    log "Stack left running (--keep-up). Tear down with:"
    log "  docker compose -f ${COMPOSE_FILE} down -v"
  fi
  rm -f "${PID_FILE}"
  log "==> Exiting with code ${exit_code}"
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM HUP QUIT

###############################################################################
# Bring up stack
###############################################################################

###############################################################################
# Container helpers (defined before compose up so error paths can call them).
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

if [[ "${SKIP_DOCKER_BUILD}" -eq 1 ]]; then
  step "Skipping docker build (--skip-docker-build); verifying images present"
  for img in bisq-bitcoind:ci bisq-daemon:ci; do
    docker image inspect "${img}" >/dev/null 2>&1 \
      || fatal "image '${img}' missing; rerun without --skip-docker-build"
  done
else
  step "Building docker images"
  docker compose -f "${COMPOSE_FILE}" build || fatal "image build failed"
  for img in bisq-bitcoind:ci bisq-daemon:ci; do
    docker image inspect "${img}" >/dev/null 2>&1 \
      || fatal "image '${img}' not present after build (check compose 'image:' / 'build:' fields)"
  done
fi

step "Starting compose stack (with bitcoind healthcheck gating Bisq containers)"
# --wait blocks until all services are healthy (or 'started' if no healthcheck).
# Healthcheck on bitcoind verifies RPC + chain parsing + genesis tx present.
if ! docker compose -f "${COMPOSE_FILE}" up -d --wait --wait-timeout 240; then
  err "compose up failed; showing state + last 80 log lines for every container:"
  for c in "${ALL_CONTAINERS[@]}"; do
    s="$(container_status "${c}")"
    err "----- ${c} (state=${s:-missing}) -----"
    docker inspect --format='{{json .State.Health}}' "${c}" 2>&1 | tail -c 1500 >&2 || true
    docker logs --tail 80 "${c}" >&2 || true
  done
  fatal "compose up failed — see per-container output above"
fi

###############################################################################
# Wait for Bisq daemons to answer gRPC.
# Uses docker exec into each container (no per-poll docker run, no extra network).
###############################################################################

wait_daemon() {
  local label="$1" port="$2"
  local deadline=$(( $(date +%s) + READY_TIMEOUT_SECS ))
  while : ; do
    # Catch any crash across the entire stack, not just this daemon.
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
step "Waiting for Bisq daemons (gRPC)"
wait_daemon arb   9997
wait_daemon alice 9998
wait_daemon bob   9999
assert_all_running "after daemon readiness, before gradle test"

###############################################################################
# Run tests
###############################################################################

step "Running tests: ${TEST_PATTERN}"
set +e
"${GRADLE}" --no-daemon :apitest:test \
  --tests "${TEST_PATTERN}" \
  -DrunApiTests=true \
  -DapiHost.alice=localhost -DapiPort.alice=9998 \
  -DapiHost.bob=localhost   -DapiPort.bob=9999 \
  -DapiHost.arb=localhost   -DapiPort.arb=9997 \
  -DapiPassword=xyz \
  -DbitcoindContainer=bitcoind
TEST_EXIT=$?
set -e

if [[ ${TEST_EXIT} -ne 0 ]]; then
  err "tests failed (exit ${TEST_EXIT}). Inspect:"
  err "  apitest/build/reports/tests/test/index.html"
  err "  ${LOGS_DIR}/{alice,bob,arb,seednode,bitcoind}.log"
fi
exit ${TEST_EXIT}
