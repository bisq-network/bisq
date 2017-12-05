###
# The directory of the Dockerfile should contain your 'hostname' and 'private_key' files.
# In the docker-compose.yml file you can pass the ONION_ADDRESS referenced below.
###

# pull base image
FROM openjdk:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    vim \
    fakeroot \
    sudo \
    openjfx \
    build-essential && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/mrosseel/bisq-api.git
WORKDIR /bisq-api/
RUN mvn clean install

CMD mvn exec:java -Dexec.mainClass="io.bisq.api.app.ApiMain"
