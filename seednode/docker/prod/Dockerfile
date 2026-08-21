FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq-seednode
CMD ./docker/startSeedNode.sh

ENV APP_NAME=seednode
ENV NODE_PORT=8000

EXPOSE 8000

COPY . .
RUN ./docker/setup.sh
ENV SKIP_BUILD=true
