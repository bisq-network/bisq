# Bisq DAO Test Stack (docker)

Headless 5-container regtest stack for Bisq DAO governance + trade flows.
Entry point: [`run-e2e-tests.sh`](run-e2e-tests.sh).

## TLDR

From the repository root:

```bash
apitest/docker/run-e2e-tests.sh
```

For a faster local rerun after images already exist:

```bash
apitest/docker/run-e2e-tests.sh --skip-build --skip-docker-build
```

If local regtest ports are already in use:

```bash
BITCOIND_RPC_HOST_PORT=18445 BITCOIND_P2P_HOST_PORT=18446 \
  apitest/docker/run-e2e-tests.sh
```

Run one JUnit class only:

```bash
FRESH_STACK_TESTS= RUN_POLICY_E2E_TESTS=false \
  apitest/docker/run-e2e-tests.sh --tests "bisq.apitest.dao.ProposalPhaseTest"
```

The default run executes the shared-stack method/DAO tests, fresh-stack tests,
and deny-list policy phases. Set `RUN_POLICY_E2E_TESTS=false` to skip only the
policy phases while iterating on unrelated tests.

## Topology

| Container | Role | Notes |
|---|---|---|
| `bitcoind` | `lnliz/bitcoind:29.3` regtest | dao-setup chain seeded into `/data/.bitcoin` |
| `seednode` | Bisq seed + full DAO node | `--fullDaoNode=true --isBmFullNode=true` |
| `arb`      | Mediator + refund agent host | Registers both on startup with dev key |
| `alice`    | Lite Bisq node, gRPC `:9998` | dao-setup app dir → `/bisq/data/bisq-BTC_REGTEST_Alice` |
| `bob`      | Lite Bisq node, gRPC `:9999` | dao-setup app dir → `/bisq/data/bisq-BTC_REGTEST_Bob` |

Bridge network `bisqnet`; bitcoind exposes RPC `:18443` + bitcoinj P2P `:18444` by default;
daemons on `:9997-9999`.

## dao-setup.zip data placement

`./gradlew :apitest:installDaoSetup` unpacks the zip to `apitest/src/main/resources/`.
The Dockerfiles COPY from there at build time, and the entrypoints seed runtime
volumes on first boot.

### Bitcoind chain

**Source in repo** (post-installDaoSetup):
```
apitest/src/main/resources/Bitcoin-regtest/
├── bitcoin.conf            ← template; not used (entrypoint generates fresh one)
├── blocknotify             ← template; not used
└── regtest/                ← USED — chain data
    ├── blocks/
    ├── chainstate/
    ├── wallets/wallet.dat  ← legacy bitcoind wallet, not loaded by bitcoind 29
    ├── peers.dat, mempool.dat, fee_estimates.dat, banlist.dat
```

**Bake** ([`Dockerfile.bitcoind`](Dockerfile.bitcoind)):
```dockerfile
COPY apitest/src/main/resources/Bitcoin-regtest/regtest /opt/dao-setup/regtest
```

**Seed at boot** ([`bitcoind-entrypoint.sh`](bitcoind-entrypoint.sh)):
- Datadir = `/data/.bitcoin` (lnliz/bitcoind:29.3 default; matches user's
  reference compose `./data_dirs/bitcoind:/data/.bitcoin:z`)
- On first boot, `cp -r /opt/dao-setup/regtest /data/.bitcoin/regtest`
- `bitcoin.conf` regenerated each start (rpc bind + container-aware blocknotify)
- Final chain tip: height 111, genesis tx `30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf`

Volume: `bitcoind-data:/data/.bitcoin`

### Alice & Bob app data

**Source in repo** (per role):
```
apitest/src/main/resources/bisq-BTC_REGTEST_Alice_dao/
└── btc_regtest/
    ├── db/{AddressEntryList,PreferencesPayload,UserPayload}
    └── wallet/{bisq_BTC.wallet, bisq_BSQ.wallet}
apitest/src/main/resources/bisq-BTC_REGTEST_Bob_dao/
└── btc_regtest/
    └── (same shape)
```

Source dirs in dao-setup.zip carry a `_dao` suffix; the Dockerfile renames them
at COPY time so the runtime app-name matches the compose service env.

**Bake** ([`Dockerfile.bisq`](Dockerfile.bisq)):
```dockerfile
COPY apitest/src/main/resources/bisq-BTC_REGTEST_Alice_dao /opt/dao-setup/bisq-BTC_REGTEST_Alice
COPY apitest/src/main/resources/bisq-BTC_REGTEST_Bob_dao   /opt/dao-setup/bisq-BTC_REGTEST_Bob
```

**Seed at boot** ([`entrypoint.sh`](entrypoint.sh)):
- `APP_DATA_DIR=/bisq/data` (container default from `ENV`)
- `APP_NAME` is set per service in compose (e.g. `bisq-BTC_REGTEST_Alice`)
- On first boot: `cp -r /opt/dao-setup/${APP_NAME} ${APP_DATA_DIR}/${APP_NAME}`
- Daemon launched with `--appDataDir=${APP_DATA_DIR}/${APP_NAME}` — Bisq reads
  `${appDataDir}/btc_regtest/{db,wallet}` directly

Volumes: `alice-data:/bisq/data`, `bob-data:/bisq/data` (note: per-container).
Arb + seednode use empty volumes — no dao-setup data needed for them.

### Genesis tx params

Passed to every Bisq daemon as CLI args (in `entrypoint.sh`):
```
--genesisBlockHeight=111
--genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf
```

## Files

| File | Purpose |
|---|---|
| `Dockerfile.bitcoind` | Wraps `lnliz/bitcoind:29.3` with `bitcoind-entrypoint.sh` + baked chain |
| `Dockerfile.bisq` | Base for all four Bisq roles; bakes install-dists + javafx-linux jars + dao-setup app dirs |
| `bitcoind-entrypoint.sh` | seeds `/data/.bitcoin` + writes `bitcoin.conf` + blocknotify hook |
| `entrypoint.sh` | Bisq daemon/seed launcher; seeds `${APP_DATA_DIR}/${APP_NAME}` |
| `dao-compose.yml` | 5-service stack with healthchecks |
| `run-e2e-tests.sh` | Orchestrator: build → up → wait → test → down |

## Local usage

```bash
apitest/docker/run-e2e-tests.sh                              # full
apitest/docker/run-e2e-tests.sh --skip-build                 # reuse gradle outputs
apitest/docker/run-e2e-tests.sh --skip-build --skip-docker-build  # fastest iter
apitest/docker/run-e2e-tests.sh --keep-up                    # leave stack up
apitest/docker/run-e2e-tests.sh --tests "bisq.apitest.dao.ProposalPhaseTest"
RUN_POLICY_E2E_TESTS=false apitest/docker/run-e2e-tests.sh   # skip deny-list policy phases
```

## Diagnostics

Logs always collected to `apitest/docker/logs/{bitcoind,seednode,arb,alice,bob}.log`.

While `--keep-up`:
```bash
docker exec bitcoind bitcoin-cli -regtest -rpcuser=bisqdao -rpcpassword=bsq getblockcount
docker exec alice /bisq/cli/bin/cli --port=9998 --password=xyz getbalance
docker exec alice /bisq/cli/bin/cli --port=9998 --password=xyz getcycleinfo
```
