### STAGE 1: GIT CLONE admin project AND api project ###
FROM alpine/git  as gitter
WORKDIR /app
RUN git clone  --depth 1 https://github.com/snello-cms/snello-api.git

### STAGE 4: MAVEN build api project, OVERRIDING application.yaml file ###
FROM maven:3.6.1-jdk-11-slim as builder_api
COPY --from=gitter /app/snello-api/pom.xml /tmp/
COPY --from=gitter /app/snello-api/src /tmp/src
WORKDIR /tmp
RUN mvn package -Dmaven.test.skip=true

### STAGE 4: CREATE FINAL DOCKER MACHINE ADDING PUBLIC INDEX PAGE ###
FROM adoptopenjdk/openjdk11:alpine-jre
ENV SNELLO_HOME=/home/snello \
    SNELLO_USER=snello \
    SNELLO_GROUP=snello \
    SNELLO_PGUID=1000 \
    SNELLO_PUID=1000

RUN apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Europe/Rome /etc/localtime \
 && addgroup -g ${SNELLO_PGUID} -S ${SNELLO_GROUP} \
 && adduser -h ${SNELLO_HOME} -s /sbin/nologin -G ${SNELLO_GROUP} -S -D -u ${SNELLO_PUID} ${SNELLO_USER}


RUN mkdir -p /home/snello/public/files
COPY --from=builder_api /tmp/target/snello*.jar $SNELLO_HOME/snello.jar

RUN chown -R ${SNELLO_USER}:${SNELLO_GROUP} ${SNELLO_HOME}
RUN chmod -R 755 ${SNELLO_HOME}

WORKDIR ${SNELLO_HOME}
USER ${SNELLO_USER}
# update ownership
CMD java ${JAVA_OPTS} -jar snello.jar
