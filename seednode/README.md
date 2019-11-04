# Bisq Seed Node

* Install bisq-seednode.service in /etc/systemd/system
* Install bisq-seednode in /etc/default
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

The production docker image uses the build dir, so make sure you have built the appropriate tag/branch.
Here's the checklist to start your docker seednode:

- build the seednode code: `./gradlew build`
- create a `.env` file in the seednode directory. 
This file will be used as variables by docker-compose.yml which is located in the same directory.

```
ONION_ADDRESS=my_onion_address.onion
APP_NAME=seed_BTC_MAINNET_my_onion_address
WORKING_DIR=/root/.local/share/seed_BTC_MAINNET_rm7b56wbrcczpjvl/
RPC_USER=some_user
RPC_PASSWORD=some_password
BTC_DATA_DIR=/home/some_user/.bitcoin/
```

The WORKING_DIR maps the ./local directory to the location where the seednode docker container stores the logs, 
tor private keys, ... . Best is to start the container and then copy your own private keys into this directory.

- start your seednode container and bitcoin full node container

```
docker-compose up -d
```

- perform 'docker ps' and 'docker logs seednode' and 'docker logs btcd' to make sure everything is working correctly.

### Known issues

- at startup, the seednode can try to call the bitcoin api to get some information. If the full node hasn't 
finished starting up this will result in an error and the btcli process will be killed. Simply restarting 
the seednode once btcd is running will fix this. (note: this is a bug in the seednode)

### TODO

- use jdk10
- fix startup bug
- cleaner working dir solution
- publish image
- remove hardcoded docker ip addresses (it works, but can be done prettier)
