FROM ubuntu:22.04

RUN apt-get update \
    && apt-get install curl gpg -y \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /root

RUN mkdir gpg_keys
COPY gpg_keys/* /root/gpg_keys/
RUN gpg --import /root/gpg_keys/*

RUN curl -O "https://bitcoincore.org/bin/bitcoin-core-22.0/{bitcoin-22.0-x86_64-linux-gnu.tar.gz,SHA256SUMS,SHA256SUMS.asc}" \
    && sha256sum --ignore-missing --check SHA256SUMS \
    && gpg --verify SHA256SUMS.asc \
    && tar xf bitcoin-22.0-x86_64-linux-gnu.tar.gz

RUN mv /root/bitcoin-22.0/bin/* /usr/bin/ \
    && rm -rf bitcoin* gpg_keys SHA256SUMS*

RUN apt-get update \
    && apt-get install netcat -y \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["bitcoind"]
