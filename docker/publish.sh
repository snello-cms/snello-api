#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#docker build -t "snellocms/matcms-admin" -f docker/Dockerfile-admin .
docker build -t "snellocms/snello-api" -f docker/Dockerfile-api .

#docker tag snellocms/matcms-admin:latest snellocms/matcms-admin:0.1
docker tag snellocms/snello-api:latest snellocms/snello-api:0.1


#docker push docker.io/snellocms/matcms-admin:0.1
docker push docker.io/snellocms/snello-api:0.1

