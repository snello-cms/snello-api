#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t "snellocms/matcms-admin" -f docker/Dockerfile-admin .
docker build -t "snellocms/matcms-api" -f docker/Dockerfile-api .

docker tag snellocms/matcms-admin:latest snellocms/matcms-admin:latest
docker tag snellocms/matcms-api:latest snellocms/matcms-api:latest


docker push docker.io/snellocms/matcms-admin
docker push docker.io/snellocms/matcms-api

docker-compose -f docker/docker-compose.yml up

