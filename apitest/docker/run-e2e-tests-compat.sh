#!/usr/bin/env bash
#
# Local runner for the cross-version e2e compat test: the full DAO + trade suite
# against a mixed-version stack where one peer (bob) runs a tagged release and the
# rest (seednode, arb, alice) run the current working tree. Local mirror of
# .github/workflows/e2e-tests-compat.yml — same images, same override, same suite.
#
# Requires a running docker daemon, JDK 21, and a clean git tree with tags
# fetched. Builds two source trees (current + the release tag), so the first run
# is slow.
#
# Usage:
#   apitest/docker/run-e2e-tests-compat.sh                  # bob = latest tag
#   apitest/docker/run-e2e-tests-compat.sh v1.10.1          # bob = explicit ref
#   apitest/docker/run-e2e-tests-compat.sh --keep-up        # pass-through to runner
#   apitest/docker/run-e2e-tests-compat.sh v1.10.0 --tests 'bisq.apitest.dao.*'
#
# Env overrides:
#   RELEASE_REF    release ref for the bob peer (default: latest tag); a leading
#                  non-dash positional arg takes precedence.
#   GRADLE         gradle wrapper for the current tree (default: ./gradlew)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/dao-compose.yml"
OVERRIDE_FILE="${SCRIPT_DIR}/dao-compose.compat.yml"
RELEASE_DIST="${SCRIPT_DIR}/.release-dist"
GRADLE="${GRADLE:-${REPO_ROOT}/gradlew}"

log()   { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
step()  { echo; log "==> $*"; }
fatal() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

# First non-dash arg = release ref; everything else is forwarded to run-e2e-tests.sh.
RUNNER_ARGS=()
RELEASE_REF="${RELEASE_REF:-}"
ref_from_arg=""
for a in "$@"; do
  if [[ -z "${ref_from_arg}" && "${a}" != -* ]]; then
    ref_from_arg="${a}"
  else
    RUNNER_ARGS+=("${a}")
  fi
done
[[ -n "${ref_from_arg}" ]] && RELEASE_REF="${ref_from_arg}"

cd "${REPO_ROOT}"

step "Preflight"
command -v docker >/dev/null || fatal "docker not found"
docker info >/dev/null 2>&1 || fatal "docker daemon not reachable (is it running?)"
docker compose version >/dev/null 2>&1 || fatal "docker compose v2 plugin required"
[[ -x "${GRADLE}" ]] || fatal "gradle wrapper not executable at ${GRADLE}"
[[ -f "${OVERRIDE_FILE}" ]] || fatal "override compose file missing: ${OVERRIDE_FILE}"

if [[ -z "${RELEASE_REF}" ]]; then
  RELEASE_REF="$(git describe --tags --abbrev=0 2>/dev/null)" \
    || fatal "could not auto-detect latest tag; pass a ref explicitly (run 'git fetch --tags')"
fi
git rev-parse --verify --quiet "${RELEASE_REF}^{commit}" >/dev/null \
  || fatal "unknown release ref '${RELEASE_REF}' (run 'git fetch --tags'?)"
log "Release peer (bob) ref: ${RELEASE_REF}"

# Detached worktree for the release build; removed on exit.
WORKTREE="$(mktemp -d)/src"
cleanup() {
  step "Cleaning up release worktree"
  git worktree remove --force "${WORKTREE}" 2>/dev/null || true
  rm -rf "$(dirname "${WORKTREE}")" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

###############################################################################
# 1. Current-tree install dists + dao-setup (master harness + 3 peers).
###############################################################################
step "Building current-tree install dists + dao-setup"
"${GRADLE}" --no-daemon \
  :seednode:installDist :daemon:installDist :cli:installDist \
  :apitest:installDaoSetup \
  || fatal "current-tree gradle build failed"

###############################################################################
# 2. Master docker images (bisq-bitcoind:ci, bisq-daemon:ci).
###############################################################################
step "Building master docker images (bitcoind + daemon)"
docker compose -f "${COMPOSE_FILE}" build || fatal "master image build failed"

###############################################################################
# 3. Release install dists in a worktree → stage for Dockerfile.bisq-release.
###############################################################################
step "Building release (${RELEASE_REF}) install dists in worktree"
git worktree add --detach "${WORKTREE}" "${RELEASE_REF}"
git -C "${WORKTREE}" submodule update --init --recursive
( cd "${WORKTREE}" && ./gradlew --no-daemon \
    :seednode:installDist :daemon:installDist :cli:installDist ) \
  || fatal "release gradle build failed for ${RELEASE_REF} (incompatible toolchain? try another ref)"

step "Staging release dists → ${RELEASE_DIST}"
rm -rf "${RELEASE_DIST}"
mkdir -p "${RELEASE_DIST}"
cp -r "${WORKTREE}/seednode/build/install/seednode" "${RELEASE_DIST}/seednode"
cp -r "${WORKTREE}/daemon/build/install/daemon"     "${RELEASE_DIST}/daemon"
cp -r "${WORKTREE}/cli/build/install/cli"           "${RELEASE_DIST}/cli"

###############################################################################
# 4. Release peer image (inherits master harness, swaps app binaries).
###############################################################################
step "Building release peer image bisq-daemon:release"
docker build \
  --build-arg BASE_IMAGE=bisq-daemon:ci \
  -t bisq-daemon:release \
  -f "${SCRIPT_DIR}/Dockerfile.bisq-release" \
  "${REPO_ROOT}" \
  || fatal "release image build failed"

###############################################################################
# 5. Run the suite with the compat override (skip builds — done above).
###############################################################################
step "Running e2e suite (bob=${RELEASE_REF}, rest=current tree)"
# Skip the deny-list policy phase: it injects the master-only --denyListResource
# daemon flag via BISQ_EXTRA_ARGS, which an older release peer (bob) does not
# recognize and exits on. It tests a single-node master feature, not cross-version
# compatibility, so it has no meaning in this mixed-version run.
COMPOSE_OVERRIDE_FILE="${OVERRIDE_FILE}" \
RUN_POLICY_E2E_TESTS=false \
  "${SCRIPT_DIR}/run-e2e-tests.sh" --skip-build --skip-docker-build "${RUNNER_ARGS[@]}"
