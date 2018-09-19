#!/bin/sh
docker container create -v m2:/m2 -v gradle:/gradle --name m2helperContainer busybox
docker cp ~/.m2/repository m2helperContainer:/m2/
for dir in `ls ~/.gradle` ; do
   docker cp ~/.gradle/$dir m2helperContainer:/gradle/
done
docker rm m2helperContainer
