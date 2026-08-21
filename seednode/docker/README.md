# bisq-seednode docker

Both images use the same [startSeedNode.sh](startSeedNode.sh) script so inspect it to see what environment variables you can tweak.

## Production image

In order to build image:

    docker build . -f docker/prod/Dockerfile -t bisq/seednode

Run:

    docker run bisq/seednode

You might want to mount tor hidden service directory:

    docker run -v /your/tor/dir:/root/.local/share/seednode/btc_mainnet/tor/hiddenservice/ bisq/seednode

## Development image

    docker-compose build
