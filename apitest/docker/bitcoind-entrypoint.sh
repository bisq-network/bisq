#!/bin/sh
#
# bitcoind wrapper. Datadir = /data/.bitcoin (lnliz/bitcoind:29.3 default; matches
# the user's reference compose `./data_dirs/bitcoind:/data/.bitcoin:z`).
#
# On first boot (volume empty), seed the regtest chain from the baked-in dao-setup
# data at /opt/dao-setup/regtest. The chain contains the DAO genesis tx at height
# 111 with txid 30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf,
# pre-funding Alice and Bob's BSQ wallets.

set -eu

BITCOIN_DIR="${BITCOIN_DIR:-/data/.bitcoin}"
# Default whitelist CIDR covers the full RFC1918 172.16/12 range — Docker bridge
# networks assign subnets dynamically from this range, so a /16 like 172.20.x/16
# can miss the actual bridge. Override via WHITELIST_CIDR env if needed.
WHITELIST_CIDR="${WHITELIST_CIDR:-172.16.0.0/12}"
mkdir -p "${BITCOIN_DIR}"

# Seed once on first boot.
if [ -d /opt/dao-setup/regtest ] && [ ! -d "${BITCOIN_DIR}/regtest" ]; then
  echo "[bitcoind-entrypoint] seeding ${BITCOIN_DIR}/regtest from /opt/dao-setup/regtest"
  cp -r /opt/dao-setup/regtest "${BITCOIN_DIR}/regtest"
fi

# Write bitcoin.conf each start (cheap, ensures correct rpc bind / blocknotify).
cat > "${BITCOIN_DIR}/bitcoin.conf" <<EOF
regtest=1
txindex=1
server=1
peerbloomfilters=1
rpcuser=bisqdao
rpcpassword=bsq
# Bisq's bitcoinj P2P broadcasts use sat-per-byte fees that fall below v29's
# default relay floor on regtest; lower it so blind-vote / proposal txs always
# accept into mempool.
minrelaytxfee=0.00000001
# Bisq's blind-vote tx layout uses OP_RETURN + tiny outputs that some v29 policy
# checks flag as non-standard on regtest. Allow them through.
acceptnonstdtxn=1
debug=mempool
debug=mempoolrej
debug=net
# Whitelist the docker bridge network so bitcoinj peers' txs are accepted without
# the standard INV-fetch tx-request jitter (default 2s + random; observed 30+ s
# stalls in regtest CI). Whitelisted peers also bypass mempool min-fee policy.
whitelist=${WHITELIST_CIDR}
[regtest]
rpcport=18443
rpcbind=0.0.0.0
rpcallowip=0.0.0.0/0
blocknotify=/usr/local/bin/blocknotify.sh %s
fallbackfee=0.0002
EOF

cat > /usr/local/bin/blocknotify.sh <<'EOF'
#!/bin/sh
hash="$1"
for host_port in seednode:5120 arb:5121 alice:5122 bob:5123; do
  host="${host_port%%:*}"
  port="${host_port##*:}"
  if command -v nc >/dev/null 2>&1; then
    printf '%s\n' "${hash}" | nc -w 1 "${host}" "${port}" >/dev/null 2>&1 || true
  elif command -v bash >/dev/null 2>&1; then
    bash -c "exec 3<>/dev/tcp/${host}/${port}; printf '%s\n' '${hash}' >&3; exec 3<&-" >/dev/null 2>&1 || true
  fi
done
EOF
chmod +x /usr/local/bin/blocknotify.sh

# Create a descriptor wallet "testwallet" after RPC comes up. The legacy
# wallet.dat shipped in dao-setup.zip is unloadable by bitcoind 29; tests just
# need ANY wallet for generatetoaddress, so we make a fresh descriptor one.
(
  for _ in $(seq 1 120); do
    if bitcoin-cli -regtest -rpcuser=bisqdao -rpcpassword=bsq getblockchaininfo >/dev/null 2>&1; then
      if bitcoin-cli -regtest -rpcuser=bisqdao -rpcpassword=bsq \
          createwallet "testwallet" false false "" false true true false 2>/dev/null \
          || bitcoin-cli -regtest -rpcuser=bisqdao -rpcpassword=bsq loadwallet "testwallet" 2>/dev/null; then
        echo "[bitcoind-entrypoint] testwallet ready"
        exit 0
      fi
    fi
    sleep 1
  done
  echo "[bitcoind-entrypoint] WARNING: unable to prepare testwallet after 120s" >&2
) &

exec bitcoind -datadir="${BITCOIN_DIR}" -printtoconsole "$@"
