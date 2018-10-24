FROM openjdk:10-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq/http-api

#ENV BISQ_API_HOST=
#ENV BISQ_API_PORT=
ENV LANG=en_US

CMD ./docker/startApi.sh

#Fetch gradle and dependencies
COPY gradlew /bisq/
COPY gradle /bisq/gradle/
COPY build.gradle gradle.properties settings.gradle /bisq/
COPY http-api/build.gradle /bisq/http-api/
COPY assets/build.gradle /bisq/assets/
COPY common/build.gradle /bisq/common/
COPY core/build.gradle /bisq/core/
COPY p2p/build.gradle /bisq/p2p/
COPY desktop/build.gradle settings.gradle /bisq/desktop/
COPY desktop/gradle /bisq/desktop/gradle/
RUN ../gradlew --no-daemon build -x test

COPY . /bisq

#Compile sources to speed up startup
RUN ../gradlew --no-daemon compileJava -x test
