#!/usr/bin/env bash


docker build -t "snellocms/snello-api" -f docker/Dockerfile-api .

docker tag snellocms/snello-api:latest snellocms/snello-api:0.1

docker push docker.io/snellocms/snello-api:0.1

