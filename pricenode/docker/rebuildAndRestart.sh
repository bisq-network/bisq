#!/bin/sh
docker-compose build --no-cache && docker-compose up -d
docker image prune -f
docker-compose logs -f
