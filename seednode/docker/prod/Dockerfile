# first the builder builds the executables
FROM gradle:jdk10 as builder
ARG SEEDNODE_URL
ARG SEEDNODE_BRANCH
RUN git clone $SEEDNODE_URL
WORKDIR /home/gradle/bisq/seednode
RUN git config --global user.email "you@example.com"
RUN git config --global user.name "Your Name"
RUN git pull origin $SEEDNODE_BRANCH && git checkout $SEEDNODE_BRANCH
RUN gradle build
RUN chmod +x ./build/app/bin/bisq-seednode

# then the actual image is created, without gradle, using just the artifacts from the builder
FROM openjdk:10
EXPOSE 8000
COPY --from=builder /home/gradle/bisq /home/bisq
COPY ./docker/startSeednode /home/bisq/seednode
WORKDIR /home/bisq/seednode
CMD ./startSeedNode.sh

