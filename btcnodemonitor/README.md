## Bitcoin node monitor

This is a simple headless node with a http server which connects periodically to the Bisq-provided Bitcoin nodes and
disconnect quickly afterwards.

### Run Bitcoin node monitor

Run the Gradle task:

```sh
./gradlew btcnodemonitor:run
```

Or create a run scrip by:

```sh
./gradlew btcnodemonitor:startBisqApp
```

And then run:

```sh
./bisq-btcnodemonitor
```

### Tor

With `--useTorForBtcMonitor=true`, the default, the monitor connects to the Bitcoin nodes through its own Tor, which it
runs in the sandbox, as every Bisq application does. `--useTorSandbox=false` turns the sandbox off.
