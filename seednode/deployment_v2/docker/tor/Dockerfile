FROM ubuntu:22.04 as build

RUN apt-get update \
    && apt-get install curl gpg -y \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /root

COPY Tor_Browser_Developers_signing_key.asc /root/Tor_Browser_Developers_signing_key.asc
RUN gpg --import Tor_Browser_Developers_signing_key.asc

RUN curl -O "https://archive.torproject.org/tor-package-archive/torbrowser/12.5.6/tor-expert-bundle-12.5.6-linux-x86_64{.tar.gz,.tar.gz.asc}" \
    && gpg --verify tor-expert-bundle-12.5.6-linux-x86_64.tar.gz.asc \
    && tar xf tor-expert-bundle-12.5.6-linux-x86_64.tar.gz

FROM ubuntu:22.04

COPY --from=build /root/tor/libcrypto.so.1.1 /root/tor/libevent-2.1.so.7 /root/tor/libssl.so.1.1 "/root/tor/libstdc++.so.6" /usr/lib/x86_64-linux-gnu/
COPY --from=build /root/tor/tor /usr/bin/

ENTRYPOINT ["tor"]
