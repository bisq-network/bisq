###
# The directory of the Dockerfile should contain your 'hostname' and 'private_key' files.
# In the docker-compose.yml file you can pass the ONION_ADDRESS referenced below.
###

# pull base image
FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    vim \
    tor \
    fakeroot \
    sudo \
    openjfx && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/bisq-network/pricenode.git
WORKDIR /pricenode/
RUN ./gradlew assemble

COPY loop.sh start_node.sh start_tor.sh ./
COPY hostname private_key /var/lib/tor/
COPY torrc /etc/tor/
RUN  chmod +x *.sh && chown debian-tor:debian-tor /etc/tor/torrc /var/lib/tor/hostname /var/lib/tor/private_key

CMD ./start_tor.sh && ./start_node.sh
#CMD tail -f /dev/null
