###
# The directory of the Dockerfile should contain your 'hostname' and 'private_key' files.
# In the docker-compose.yml file you can pass the ONION_ADDRESS referenced below.
###

# pull base image
FROM openjdk:8-jdk

ARG ONION_ADDRESS

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    vim \
    fakeroot \
    openjfx && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/bisq-network/exchange.git
WORKDIR /exchange/
RUN mvn clean install
RUN mkdir -p /root/.local/share/seed_BTC_MAINNET_${ONION_ADDRESS}/btc_mainnet/tor/hiddenservice

COPY hostname private_key /root/.local/share/seed_BTC_MAINNET_${ONION_ADDRESS}/btc_mainnet/tor/hiddenservice/

CMD java -Xms1800m -Xmx1800m -jar ./seednode/target/SeedNode.jar --maxConnections=30 --baseCurrencyNetwork=BTC_MAINNET --nodePort=8000 \
    --myAddress=${ONION_ADDRESS}.onion:8000 --appName=seed_BTC_MAINNET_${ONION_ADDRESS} \
    2>error_seed_BTC_MAINNET_${ONION_ADDRESS}.log
