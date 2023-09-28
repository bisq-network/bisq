FROM azul/zulu-openjdk:11.0.20-11.66.15 as build

RUN apt-get update \
    && apt-get install git -y \
    && rm -rf /var/lib/apt/lists/*

RUN git config --global advice.detachedHead false \
    && git clone --depth 1 --branch v1.9.13 https://github.com/bisq-network/bisq.git

WORKDIR /bisq
RUN ./gradlew seednode:distTar

FROM azul/zulu-openjdk:11.0.20-11.66.15

COPY --from=build /bisq/bisq/seednode/build/distributions/seednode.tar /
RUN tar xvf /seednode.tar

ENTRYPOINT ["bash",  "/seednode/bin/seednode"]
