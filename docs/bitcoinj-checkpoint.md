# How to add a new bitcoinj checkpoint

### Update checkpoint
1. `git checkout https://github.com/bisq-network/bitcoinj.git` 
2. `cd tools ; ./build-checkpoints --peer=127.0.0.1 --days=10` (you have to have a local Bitcoin node running)
3. Copy generated `/tools/checkpoints.txt` into `core/src/main/resources/wallet`
