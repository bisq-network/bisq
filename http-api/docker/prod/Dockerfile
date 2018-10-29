FROM openjdk:10-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq-api

#ENV HTTP_API_HOST=
#ENV HTTP_API_PORT=
ENV LANG=en_US

CMD ./docker/startApi.sh

COPY gradlew build.gradle ./
COPY gradle/wrapper ./gradle/wrapper
#This is to fetch maven plugins needed to run exec:java from docker/startApi.sh
RUN ./gradlew dependencies

COPY . /bisq-api

VOLUME /root/.local/share/Bisq
RUN ./gradlew build -x test
ENV SKIP_BUILD=true
ENV BISQ_API_HOST=0.0.0.0
