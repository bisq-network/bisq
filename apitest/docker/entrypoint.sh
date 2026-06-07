#!/usr/bin/env bash
#
# Bisq DAO test container entrypoint. Roles: seednode | daemon.
# Daemons start with the dao-setup genesis tx (txid 30af00...03cf, height 111).

set -euo pipefail

: "${ROLE:?ROLE must be set (seednode|daemon)}"
: "${APP_NAME:?APP_NAME must be set}"
: "${NODE_PORT:?NODE_PORT must be set}"
: "${RPC_BLOCK_NOTIFICATION_PORT:?RPC_BLOCK_NOTIFICATION_PORT must be set}"

APP_DATA_DIR="${APP_DATA_DIR:-/bisq/data}"
mkdir -p "${APP_DATA_DIR}"

# Seed APP_DATA_DIR/$APP_NAME from baked-in dao-setup on first boot. Idempotent.
if [[ -d "/opt/dao-setup/${APP_NAME}" && ! -d "${APP_DATA_DIR}/${APP_NAME}" ]]; then
  echo "[entrypoint] seeding ${APP_DATA_DIR}/${APP_NAME} from /opt/dao-setup/${APP_NAME}"
  cp -r "/opt/dao-setup/${APP_NAME}" "${APP_DATA_DIR}/${APP_NAME}"
fi

# Permissive gRPC rate-metering config so tests can drive endpoints freely.
RATEMETERS_FILE="${APP_DATA_DIR}/${APP_NAME}/ratemeters.json"
mkdir -p "$(dirname "${RATEMETERS_FILE}")"
cat > "${RATEMETERS_FILE}" <<'EOF'
[
  {"grpcServiceClassName":"GrpcOffersService",        "methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]},
  {"grpcServiceClassName":"GrpcTradesService",        "methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]},
  {"grpcServiceClassName":"GrpcPaymentAccountsService","methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]},
  {"grpcServiceClassName":"GrpcWalletsService",       "methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]},
  {"grpcServiceClassName":"GrpcDaoService",           "methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]},
  {"grpcServiceClassName":"GrpcDisputeAgentsService", "methodRateMeters":[{"disabled":{"allowedCallsPerTimeWindow":1,"timeUnit":"SECONDS","numTimeUnits":1}}]}
]
EOF

COMMON_ARGS=(
  --genesisBlockHeight=111
  --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf
  --baseCurrencyNetwork=BTC_REGTEST
  --useDevPrivilegeKeys=true
  --useLocalhostForP2P=true
  --seedNodes=seednode:2002
  --nodePort="${NODE_PORT}"
  --appName="${APP_NAME}"
  --appDataDir="${APP_DATA_DIR}/${APP_NAME}"
  --bitcoinRegtestHost=bitcoind
)

EXTRA_ARGS=()
if [[ -n "${BISQ_EXTRA_ARGS:-}" ]]; then
  read -r -a EXTRA_ARGS <<< "${BISQ_EXTRA_ARGS}"
fi

case "${ROLE}" in
  seednode|daemon) ;;
  *)
    echo "[entrypoint] unsupported ROLE '${ROLE}' (expected seednode or daemon)" >&2
    exit 2
    ;;
esac

if [[ "${ROLE}" == "seednode" ]]; then
  exec /bisq/seednode/bin/seednode \
    "${COMMON_ARGS[@]}" \
    --fullDaoNode=true \
    --isBmFullNode=true \
    --rpcUser=bisqdao \
    --rpcPassword=bsq \
    --rpcHost=bitcoind \
    --rpcPort=18443 \
    --rpcBlockNotificationPort="${RPC_BLOCK_NOTIFICATION_PORT}" \
    --rpcBlockNotificationHost=0.0.0.0 \
    "${EXTRA_ARGS[@]}" \
    "$@"
fi

# Daemon role.
: "${API_PORT:?API_PORT must be set for daemon role}"
: "${API_PASSWORD:?API_PASSWORD must be set for daemon role}"

DAEMON_ARGS=(
  --apiPort="${API_PORT}"
  --apiPassword="${API_PASSWORD}"
)
# Each daemon parses chain itself — bypasses lite-node DAO state propagation
# from seednode. Requires RPC creds to bitcoind.
if [[ "${FULL_DAO_NODE:-true}" == "true" ]]; then
  DAEMON_ARGS+=(
    --fullDaoNode=true
    --rpcUser=bisqdao
    --rpcPassword=bsq
    --rpcHost=bitcoind
    --rpcPort=18443
    --rpcBlockNotificationPort="${RPC_BLOCK_NOTIFICATION_PORT}"
    # Accept blocknotify pings from bitcoind's container IP (default is 127.0.0.1
    # which only works for local-process scaffolding, not docker bridge).
    --rpcBlockNotificationHost=0.0.0.0
  )
fi

if [[ "${REGISTER_DISPUTE_AGENT:-false}" == "true" ]]; then
  (
    DEV_KEY="6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a"
    until /bisq/cli/bin/cli --port="${API_PORT}" --password="${API_PASSWORD}" getversion >/dev/null 2>&1; do
      sleep 2
    done
    sleep 5
    # Registration is idempotent at the storage layer; the daemon reports "already
    # registered" if invoked twice within the same process. Treat that one specific
    # error as success but fail loudly on any other registration error — otherwise
    # the container comes up without dispute agents and trade tests fail later in
    # a much less obvious place.
    register_agent() {
      local type="$1"
      local out
      if out=$(/bisq/cli/bin/cli --port="${API_PORT}" --password="${API_PASSWORD}" \
          registerdisputeagent --dispute-agent-type="${type}" --registration-key="${DEV_KEY}" 2>&1); then
        return 0
      fi
      if grep -qi "already registered" <<<"${out}"; then
        echo "[entrypoint] ${type} already registered"
        return 0
      fi
      echo "[entrypoint] ${type} registration failed:" >&2
      echo "${out}" >&2
      exit 1
    }
    register_agent mediator
    register_agent refundagent
  ) &
fi

exec /bisq/daemon/bin/daemon \
  "${COMMON_ARGS[@]}" \
  "${DAEMON_ARGS[@]}" \
  "${EXTRA_ARGS[@]}" \
  "$@"
