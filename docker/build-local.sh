#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t "snello/matcms-admin" -f docker/Dockerfile-admin-local .
docker build -t "snello/matcms-api" -f docker/Dockerfile-api-local .

docker-compose -f docker/docker-compose.yml up

