#!/usr/bin/env bash

mvn clean package -DskipTests=true
docker build -t snellocms/snello-api -f Dockerfile-local .
docker tag snellocms/snello-api snellocms/snello-api:local
