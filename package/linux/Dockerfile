###
#
# Quick dockerfile meant to help building.
# Missing:
# - crypto fixes to JDK
# - various paths in the build script
###

# pull base image
FROM openjdk:8-jdk
ENV version 0.6.0

RUN apt-get update && apt-get install -y --no-install-recommends openjfx && rm -rf /var/lib/apt/lists/* &&
apt-get install -y vim fakeroot


COPY 64bitBuild.sh /root
COPY bisq-$version.jar /root
# cd to the Dex directory and execute the jar.
#CMD cd ~/Dex && java -jar Dex.jar
