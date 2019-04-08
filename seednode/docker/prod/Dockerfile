FROM gradle:jdk11

#RUN apt update && apt install gradle

# RUN git clone https://github.com/bisq-network/bisq.git
RUN https://github.com/mrosseel/bisq.git && git checkout dao-seednode
WORKDIR /home/gradle/bisq/seednode

ENV APP_NAME=seednode
ENV NODE_PORT=8000

EXPOSE 8000
RUN gradle build
RUN chmod +x ./build/app/bin/bisq-seednode
CMD ./docker/startSeedNode.sh
#CMD sleep 10000
