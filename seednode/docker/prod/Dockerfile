FROM openjdk:11
ENV APP_NAME=seednode
ENV NODE_PORT=8000
EXPOSE 8000
WORKDIR /home/bisq/seednode
COPY ./build/ ./build/
COPY ./docker/*.sh ./docker/
CMD ./docker/startSeedNode.sh
#CMD sleep 100000
