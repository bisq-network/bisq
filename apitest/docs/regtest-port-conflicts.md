# Avoiding bitcoin-core regtest port conflicts

Some developers may already be running a `bitcoind` or `bitcoin-qt` instance in regtest mode when they try to run API
test cases.  If a `bitcoin-qt` instance is bound to the default regtest port 18444, `apitest` will not be able to start
its own bitcoind instances.

Though it would be preferable for `apitest` to change the bind port for Bisq's `bitcoinj` module at runtime, this is
not currently possible because `bitcoinj` hardcodes the default regtest mode bind port in `RegTestParams`.

To avoid the bind address:port conflict, pass a port option to your bitcoin-core instance:

    bitcoin-qt -regtest -port=20444
