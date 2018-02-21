###
# WARNING!!! THIS IMAGE IS FOR  D E V E L O P M E N T  USE ONLY!
#
# The directory of the Dockerfile should contain your 'hostname' and 'private_key' files.
###

# pull base image
FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    vim \
    fakeroot \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /exchange/
CMD ./docker/startSeedNode.sh

ENV ONION_ADDRESS=
ENV MAX_CONNECTIONS=30
ENV BASE_CURRENCY_NETWORK=BTC_REGTEST
ENV NODE_PORT=8000
ENV APP_NAME=seednode
ENV USE_LOCALHOST_FOR_P2P=

COPY ./ ./
