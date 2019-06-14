FROM openjdk:11
ENV APP_NAME=seednode
ENV NODE_PORT=8000
EXPOSE 8000
EXPOSE 5120
WORKDIR /home/bisq/seednode
COPY ./build/ ./build/
COPY ./docker/*.sh ./docker/
CMD ./wait-for-it.sh 172.28.1.4:8332 --strict -- nohup sh ./docker/startSeedNode.sh
#CMD sleep 100000
