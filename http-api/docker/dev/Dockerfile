FROM openjdk:10-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjfx && rm -rf /var/lib/apt/lists/*

WORKDIR /bisq/http-api

#ENV HTTP_API_HOST=
#ENV HTTP_API_PORT=
ENV LANG=en_US

CMD ./docker/startApi.sh

#Fetch gradle and dependencies
COPY gradle /bisq/gradle/
COPY gradlew build.gradle gradle.properties settings.gradle /bisq/
RUN ../gradlew --no-daemon -v

COPY http-api/build.gradle /bisq/http-api/
COPY assets /bisq/assets/
COPY common /bisq/common/
COPY core /bisq/core/
COPY p2p /bisq/p2p/
COPY pricenode /bisq/pricenode/
RUN ../gradlew --no-daemon build -x test

COPY http-api /bisq/http-api

#Compile sources to speed up startup
RUN ../gradlew --no-daemon --offline compileJava -x test
