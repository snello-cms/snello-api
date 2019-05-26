#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker build -t "snellocms/snello-admin-builder" -f docker/Dockerfile-admin-builder .

docker tag snellocms/snello-admin-builder:latest snellocms/snello-admin-builder:latest

docker push docker.io/snellocms/snello-admin-builder
