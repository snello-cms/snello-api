FROM maven:3.6.0-jdk-11-slim AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:11.0.1-jre-slim-stretch

ENV SNELLO_HOME=/home/snello \
    SNELLO_USER=snello \
    SNELLO_GROUP=snello \
    SNELLO_PGUID=2000 \
    SNELLO_PUID=2000
RUN useradd -ms /bin/bash snello
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/snello-api*.jar $SNELLO_HOME/snello-api.jar
RUN chown -R snello $SNELLO_HOME
WORKDIR ${SNELLO_HOME}
USER ${SNELLO_USER}

CMD java ${JAVA_OPTS} -jar snello-api.jar


HEALTHCHECK --interval=1m --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit