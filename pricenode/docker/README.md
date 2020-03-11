# Needed information to start a pricenode

## Copy to this directory

- a tor `hostname` file, containing your onion address
- a tor `private_key` file, containing the private key for your tor hidden service

## Edit docker-compose.yml

- fill in your public and private api keys (needs a bitcoinaverage.com developer subscription at the moment)
- optionally: update BISQ_URL and BISQ_BRANCH

## Needed software to start a pricenode

- docker
- docker-compose

## How to start

`docker-compose up -d`

## How to monitor

See if it's running: `docker ps`

Check the logs: `docker-compose logs`
