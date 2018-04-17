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
RUN ./docker/setup.sh
#This is to fetch maven plugins needed to run exec:java from docker/startApi.sh
RUN mvn exec:java -Dexec.mainClass="bisq.desktop.app.BisqAppMain" -Dexec.args="--help"

COPY . /bisq-api

VOLUME /root/.local/share/Bisq
RUN ./docker/setup.sh
ENV SKIP_BUILD=true
ENV BISQ_API_HOST=0.0.0.0
