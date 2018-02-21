FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    xauth \
    maven \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq-api

#ENV BISQ_API_PORT=
ENV LANG=en_US

#TODO get rid of xvfb-run and xauth once api is decoupled from javafx
CMD xvfb-run ./docker/startApi.sh

COPY . /bisq-api
