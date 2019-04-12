FROM gradle:jdk11 as builder

#RUN apt update && apt install gradle

# RUN git clone https://github.com/bisq-network/bisq.git
RUN git clone https://github.com/mrosseel/bisq.git
WORKDIR /home/gradle/bisq/seednode
RUN git checkout dao-seednode
RUN gradle build
RUN chmod +x ./build/app/bin/bisq-seednode

FROM openjdk:11
ENV APP_NAME=seednode
ENV NODE_PORT=8000
EXPOSE 8000
COPY --from=builder /home/gradle/bisq /home/bisq
WORKDIR /home/bisq/seednode
CMD ./docker/startSeedNode.sh
#CMD sleep 100000
