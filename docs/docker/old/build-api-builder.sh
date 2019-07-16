#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t "snellocms/snello-api-builder" -f docker/Dockerfile-api-builder ./docker

docker tag snellocms/snello-api-builder:latest snellocms/snello-api-builder:latest


docker push docker.io/snellocms/snello-api-builder
