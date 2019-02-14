# Automated setup of regtest DAO - for Linux

The goal of these helper scripts is to set up a bisq DAO from scratch. The scripts run `bitcoind` in regtest mode for you and a `seed node`, `alice` and `bob` instances.

We assume the user is in the `autosetup-regtest-dao` directory. It is ok to move the directory, but please move it as a whole. Also `bitcoin-qt`, `bitcoin-cli`, `bitcoin-tx`, `jq` and `bc` has to be installed on the system. Do not change the shipped `bitcoin.conf` file as it will break things.

## Steps to spin up a fresh DAO (we assume the user is in the autosetup-regtest-dao directory):
1. Set up `bitcoin-qt` to run in the `autosetup-regtest-dao` directory, it detects when the `bitcoin-qt` instance comes up and once it is ready the script queries the number of blocks. If there are less then 101 blocks (fresh start) the script generates 101 blocks for you so you have an available input that will be later used for the genesis tx creation.
```
./start_bitcoind.sh
```
Example output (note the below errors are normal and can be ignored):
```
user@host:~/bin/KanoczTomas/bisq/docs/autosetup-regtest-dao$ ./start_bitcoind.sh
error: Could not connect to the server 127.0.0.1:18443
Make sure the bitcoind server is running and that you are connecting to the correct RPC port.
error code: -28
error message:
Loading wallet...
./generate_101_blocks.sh: found less blocks then 101, generating ...
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
./blocknotify: line 2: echo: write error: Broken pipe
[
  "24e04970e3b55fe45efde314cc959a7baeef379d74d322bc132b723235f1e8ca",
  <truncated output for compactness>
  "2d4aaa455c681b604a683437c657e23b19af0b614f5f822c4c7aecee865b5f5f"
]
./generate_101_blocks.sh: done, exiting
```
2. Open a new terminal (preferably a new tab), go to the `bisq` folder and start the `create_genesis.sh` script. The script tests for a running bitcoind node in regtest, so make sure step 1 was successful.
```
cd bisq
./create_genesis.sh
```
example output:
```
user@host:~/bin/KanoczTomas/bisq/docs/autosetup-regtest-dao/bisq$ ./create_genesis.sh
testing if bitcoind is running: bitcoind is running, all is ok!
Please provide Alice's BSQ Address
```
3. We need the BSQ address of `Alice`. Go to the `bisq` directory and start the `Alice` instance. Wait until the instance starts up then go to `DAO -> Receive` and copy the BSQ address. Note the address starts with a `B`. Open a new terminal and enter the commands below to start `Alice`. Then wait for the gui to start and paste the BSQ address to `create_genesis.sh` which is still running from step 2.
```
cd bisq
./alice
```
4. Enter the amount of BTC in the `create_genesis.sh` script you want `Alice` to receive as BSQ.
Example output:
```
user@host:~/bin/KanoczTomas/bisq/docs/autosetup-regtest-dao/bisq$ ./create_genesis.sh
testing if bitcoind is running: bitcoind is running, all is ok!
Please provide Alice's BSQ Address
Bn2iUHwJQTreQaoajKwnT6h7pYF9nCWULJA
How much BTC to turn to BSQ for Alice? (sum must be 2.5BTC)
1
Alice will receive 1000000.00 BSQ to Bn2iUHwJQTreQaoajKwnT6h7pYF9nCWULJA
```
5. We need the BSQ address of `Bob`. Go to the `bisq` directory and start the `Bob` instance. Wait until the instance starts up then go to `DAO -> Receive` and copy the BSQ address. Note the address starts with a `B`. Open a new terminal and enter the commands below to start `Bob`. Then wait for the gui to start and paste the BSQ address to `create_genesis.sh` which is still running from step 2.
```
cd bisq
./bob
```
6. Enter the amount of BTC in the `create_genesis.sh` script you want `Bob` to receive as BSQ.
Example output:
```
tk@workbook:~/bin/KanoczTomas/bisq/docs/autosetup-regtest-dao/bisq$ ./create_genesis.sh
testing if bitcoind is running: bitcoind is running, all is ok!
Please provide Alice's BSQ Address
Bn2iUHwJQTreQaoajKwnT6h7pYF9nCWULJA
How much BTC to turn to BSQ for Alice? (sum must be 2.5BTC)
1
Alice will receive 1000000.00 BSQ to Bn2iUHwJQTreQaoajKwnT6h7pYF9nCWULJA
Please provide Bob's BSQ Address
BmoccyQEENPwxeZUdPAnDejEeTzykD9Kdbo
How much BTC to turn to BSQ for Bob? (sum must be 2.5BTC)
1.5
Bob will receive 1500000.00 BSQ to BmoccyQEENPwxeZUdPAnDejEeTzykD9Kdbo
got address 2MwNUS6czZZ8tFeFArujf1AghFZUfcJmgRx
sending 2.5001 BTC to 2MwNUS6czZZ8tFeFArujf1AghFZUfcJmgRx
txid is: d8c9d0caaaa69ad26e3dbe2176cd7fd48a4509b96cd902982fbc9811211cf20e
creating genesis tx for you
The raw genesis tx is: 020000000001010ef21c211198bc2f9802d96cb909458ad47fcd7621be3d6ed29aa6aacad0c9d80000000017160014d3316afa653bb7ca1f7bfd2395c373c979fd14ddffffffff0200e1f505000000001976a914e8884212730dd91d620b92d2e95245baf776d78b88ac80d1f008000000001976a91458d35f88e65ded63cb22a25ffda6f83a0209822b88ac0247304402204395a123c499f05bdc9b458459d97df1910feb9a43d66b7ca7e28a91c6cf19a702203ddb3fb891442bb76a2a8e267b4fec99c8a40064461556a03a799e787d08334f01210397ff7d1aa76f2fb241b0354332bb1e9be0520e744a8a2a7e5e088a92a7c09dd200000000
Please verify if decoded tx looks ok:
{
  "txid": "a876c877de410480530f7e8fa7e34034085db4db603d30a5635610fb59e646b2",
  "hash": "81923b096990db55e44730a4c3af975b1ecd995d8cbfdac939b16081249aa8bc",
  "version": 2,
  "size": 251,
  "vsize": 170,
  "weight": 677,
  "locktime": 0,
  "vin": [
    {
      "txid": "d8c9d0caaaa69ad26e3dbe2176cd7fd48a4509b96cd902982fbc9811211cf20e",
      "vout": 0,
      "scriptSig": {
        "asm": "0014d3316afa653bb7ca1f7bfd2395c373c979fd14dd",
        "hex": "160014d3316afa653bb7ca1f7bfd2395c373c979fd14dd"
      },
      "txinwitness": [
        "304402204395a123c499f05bdc9b458459d97df1910feb9a43d66b7ca7e28a91c6cf19a702203ddb3fb891442bb76a2a8e267b4fec99c8a40064461556a03a799e787d08334f01",
        "0397ff7d1aa76f2fb241b0354332bb1e9be0520e744a8a2a7e5e088a92a7c09dd2"
      ],
      "sequence": 4294967295
    }
  ],
  "vout": [
    {
      "value": 1,
      "n": 0,
      "scriptPubKey": {
        "asm": "OP_DUP OP_HASH160 e8884212730dd91d620b92d2e95245baf776d78b OP_EQUALVERIFY OP_CHECKSIG",
        "hex": "76a914e8884212730dd91d620b92d2e95245baf776d78b88ac",
        "reqSigs": 1,
        "type": "pubkeyhash",
        "addresses": [
          "n2iUHwJQTreQaoajKwnT6h7pYF9nCWULJA"
        ]
      }
    },
    {
      "value": 1.5,
      "n": 1,
      "scriptPubKey": {
        "asm": "OP_DUP OP_HASH160 58d35f88e65ded63cb22a25ffda6f83a0209822b OP_EQUALVERIFY OP_CHECKSIG",
        "hex": "76a91458d35f88e65ded63cb22a25ffda6f83a0209822b88ac",
        "reqSigs": 1,
        "type": "pubkeyhash",
        "addresses": [
          "moccyQEENPwxeZUdPAnDejEeTzykD9Kdbo"
        ]
      }
    }
  ]
}
Press enter to broadcast transaction, ctrl+c otherwise
```
7. verify if the tx shown as json is ok (the sum of outputs must be 2.5 to be valid). Note the script does not check the sum of the outputs as one needs to create an invalid genesis tx as well to test things. Press `enter` if tx looks ok.

8. Once confirmed the script will broadcast the genesis tx to the regtest blockchain and setup the `config.sh` file in the `bisq` directory which is used by the `seed_node`, `alice` and `bob` scripts. It populates the genesis txid and genesis block height so the instances can be launched accordingly.
Example output:
```
... <output ommited - see step above>
Press enter to broadcast transaction, ctrl+c otherwise
Genesis txid is:
a876c877de410480530f7e8fa7e34034085db4db603d30a5635610fb59e646b2
Mining genesis tx for you
genesis_block is 39249aab4e42c85e14f59517ec6b45305fdc7d660315c6602aab1a1b7b96cd3b
genesis_height is 102
your conf file was modified accordingly
dao setup done
```
9. Start the `seed` node in full DAO node using the `seed_node` script. Make sure you are in the `bisq` directory.
```
cd bisq
./seed_node
```
10. Restart `Alice` and `Bob` you should see BSQ visible on the `DAO` page. Note the `seed node` is a full node and `alice`/`bob` are lite nodes in this setup.

## Continuing work on the existing setup

1. Just run `start_bitcoind.sh` script and spin up `seed node`, `alice` and `bob` with respected scripts.

## Clearing the DAO - for a fresh start

1. Run `clean.sh`. It will clear the bitcoind files and bisq files from `~/.local/share/` directory. Make sure your `bitcoind/bitcoin-qt` is stopped before running.
```
user@host:~/bin/KanoczTomas/bisq/docs/autosetup-regtest-dao$ ./clean.sh
DANGER! This will nuke your bitcoin regtest dir and bisq seed, alice and bob regtest dirs as well!
Press Enter to continue, otherwise ctrl+c
cleaned bitcoind home and bisq seed, alice, bob instances. The conf file for bisq was reset to dao=false
```
