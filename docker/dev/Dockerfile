FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq-api

#ENV BISQ_API_HOST=
#ENV BISQ_API_PORT=
ENV LANG=en_US

CMD ./docker/startApi.sh

COPY . /bisq-api
