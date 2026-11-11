# One Dockerfile for all three services: docker build --build-arg MODULE=order-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
ARG OTEL_AGENT_VERSION=2.33.1
# Zero-code tracing: the agent instruments HTTP, Kafka, JDBC, logback (MDC) at startup. No tracing code
# in the app beyond the custom saga spans.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=build /src/${MODULE}/target/${MODULE}-exec.jar /app/app.jar
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
