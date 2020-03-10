# first the builder builds the executables
###
FROM gradle:jdk11 as builder
ARG BISQ_URL
ARG BISQ_BRANCH
RUN git clone $BISQ_URL
WORKDIR /home/gradle/bisq/
# git sometimes complains about this, can be bogus because we're not going to commit things
RUN git config --global user.email "you@example.com"
RUN git config --global user.name "Your Name"
RUN git fetch origin $BISQ_BRANCH && git checkout $BISQ_BRANCH
RUN git rev-parse --verify HEAD > git_head.txt
RUN ./gradlew :pricenode:installDist  -x test < /dev/null
RUN chmod +x ./pricenode/build/app/bin/bisq-pricenode

# then the actual image is created, without gradle, using just the artifacts from the builder
###
FROM openjdk:10
EXPOSE 8000
RUN apt-get update && apt-get install -y --no-install-recommends \
    vim \
    tor \
    fakeroot \
    sudo \
    openjfx && rm -rf /var/lib/apt/lists/*
# copy the seednode artifact from the builder to the final container
RUN mkdir /home/bisq
COPY --from=builder /home/gradle/bisq/pricenode/build/app/bin/bisq-pricenode /home/bisq/bin/bisq-pricenode
COPY --from=builder /home/gradle/bisq/pricenode/build/app/lib /home/bisq/lib/
COPY --from=builder /home/gradle/bisq/git_head.txt /home/bisq/git_head.txt
WORKDIR /home/bisq/
COPY loop.sh start_node.sh start_tor.sh ./
COPY hostname private_key /var/lib/tor/
COPY torrc /etc/tor/
RUN  chmod +x *.sh && chown debian-tor:debian-tor /etc/tor/torrc /var/lib/tor/hostname /var/lib/tor/private_key
CMD ./start_tor.sh && ./start_node.sh

