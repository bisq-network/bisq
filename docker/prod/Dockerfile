FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq-api

#ENV BISQ_API_HOST=
#ENV BISQ_API_PORT=
ENV LANG=en_US

CMD ./docker/startApi.sh

COPY pom.xml .
COPY docker ./docker
#This is to fetch maven plugins needed to run exec:java from docker/startApi.sh
RUN mvn dependency:resolve compile -P integration exec:java -Dexec.mainClass="bisq.desktop.app.BisqAppMain" -Dexec.args="--help"

COPY . /bisq-api

VOLUME /root/.local/share/Bisq
RUN mvn compile
ENV SKIP_BUILD=true
ENV BISQ_API_HOST=0.0.0.0
