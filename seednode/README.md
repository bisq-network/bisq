# Bisq Seed Node

## Running using systemd

* Install bisq-seednode.service in /etc/systemd/system
* Install bisq-seednode in /etc/default
* Install blocknotify.sh in bitcoind's ~/.bitcoin/ folder and chmod 700 it
* Modify the executable paths and configuration as necessary
* Then you can do:


```
systemctl start bisq-seednode.service
systemctl stop bisq-seednode.service
```
and
```
systemctl enable bisq-seednode.service
systemctl disable bisq-seednode.service
```

Follow the logs created by the service by inspecting

```
journalctl --unit bisq-seednode --follow
```


## Running using docker (experimental)

The production docker image checks out its own copy of the bisq source code, 
Here's the checklist to start your docker seednode:

- create a `.env` file in the seednode directory. 
This file will be used as variables by docker-compose.yml which is located in the same directory.

```
ONION_ADDRESS=my_onion_address
APP_NAME=seed_BTC_MAINNET_my_onion_address
WORKING_DIR=/root/.local/share/seed_BTC_MAINNET_rm7b56wbrcczpjvl/
TOR_HIDDEN_SERVICE_DIR=/root/.local/share/seed_BTC_MAINNET_5quyxpxheyvzmb2d/btc_mainnet/tor/hiddenservice
RPC_PORT=8332
RPC_USER=some_user
RPC_PASSWORD=some_password
BTC_DATA_DIR=/home/some_user/.bitcoin/
SEEDNODE_URL=https://github.com/bisq-network/bisq.git
SEEDNODE_BRANCH=master
```

For the SEEDNODE_BRANCH value not only a branch name is allowed, a commit hash works as well

The WORKING_DIR maps the ./local directory to the location where the seednode docker container stores the logs, 
tor private keys, ... . Best is to start the container and then copy your own private keys into this directory.

- start your seednode and bitcoin full node:

```
docker-compose up -d
```

- perform 'docker ps' and 'docker logs seednode' and 'docker logs btcd' to make sure everything is working correctly.

### Known issues

- at startup, the seednode can try to call the bitcoin api to get some information. If the full node hasn't 
finished starting up this will result in an error and the btcli process will be killed. Simply restarting 
the seednode once btcd is running will fix this. (note: this is a bug in the seednode)

### TODO

- fix startup bug
- publish image
- remove hardcoded docker ip addresses (it works, but might be done prettier)
