FROM openjdk:10-jdk

# This docker image should be used with
# `docker run ... -v /path/to/host/dir:/root/.local/share/Bisq` in order to
# persist Bisq configuration to the host.

RUN apt-get update \
  && apt-get install -y x11-apps \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir /src && git clone https://github.com/bisq-network/bisq /src/bisq
WORKDIR /src/bisq
RUN ./gradlew build
VOLUME /home/root/.local/share/Bisq
