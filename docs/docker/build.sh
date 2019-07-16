#!/usr/bin/env bash


docker build -t "snellocms/snello-api" -f docker/Dockerfile-api .
docker tag snellocms/snello-api:latest snellocms/snello-api:latest
docker-compose -f docker/docker-compose.yml up

