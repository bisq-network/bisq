# first the builder builds the executables
FROM gradle:jdk11 as builder
ARG SEEDNODE_URL
ARG SEEDNODE_BRANCH
RUN git clone $SEEDNODE_URL
WORKDIR /home/gradle/bisq/seednode
RUN git checkout $SEEDNODE_BRANCH
RUN gradle build
RUN chmod +x ./build/app/bin/bisq-seednode

# then the actual image is created, without gradle, using just the artifacts from the builder
FROM openjdk:10
EXPOSE 8000
COPY --from=builder /home/gradle/bisq /home/bisq
WORKDIR /home/bisq/seednode
CMD ./docker/startSeedNode.sh

