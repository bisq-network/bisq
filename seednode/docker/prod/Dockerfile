# first the builder builds the executables
###
FROM gradle:jdk10 as builder
ARG SEEDNODE_URL
ARG SEEDNODE_BRANCH
RUN git clone $SEEDNODE_URL
WORKDIR /home/gradle/bisq/seednode
# git sometimes complains about this, can be bogus because we're not going to commit things
RUN git config --global user.email "you@example.com"
RUN git config --global user.name "Your Name"
RUN git fetch origin $SEEDNODE_BRANCH && git checkout $SEEDNODE_BRANCH
RUN gradle build
RUN chmod +x ./build/app/bin/bisq-seednode

# then the actual image is created, without gradle, using just the artifacts from the builder
###
FROM openjdk:10
EXPOSE 8000
# copy the seednode artifact from the builder to the final container
RUN mkdir /home/bisq
COPY --from=builder /home/gradle/bisq/seednode/build/app/bin/bisq-seednode /home/bisq/bin/bisq-seednode
COPY --from=builder /home/gradle/bisq/seednode/build/app/lib /home/bisq/lib/
COPY ./docker/startSeedNode.sh /home/bisq/
WORKDIR /home/bisq/
CMD ./startSeedNode.sh
