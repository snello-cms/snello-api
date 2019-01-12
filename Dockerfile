FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:8u171-alpine3.7
RUN apk --no-cache add curl
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/snello-api*.jar snello-api.jar
CMD java ${JAVA_OPTS} -jar snello-api.jar
HEALTHCHECK --interval=1m --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost:8080/snello-api/ || exit