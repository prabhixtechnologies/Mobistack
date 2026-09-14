# syntax=docker/dockerfile:1
# Base images come from AWS's public gallery rather than Docker Hub. These are the same
# Docker Official Images, mirrored by AWS, and the move takes Docker Hub's anonymous pull
# limit off the build path: a rate-limited base image fails the build for a reason that has
# nothing to do with the change being built.
FROM public.ecr.aws/docker/library/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
# Local Identity starter — GitHub Packages is not available during a laptop build.
COPY --from=identity-client / /identity-client
RUN mvn -B -f /identity-client/pom.xml -DskipTests install
COPY pom.xml .
COPY src src
RUN mvn -q -B -DskipTests -Dmaven.test.skip=true package

FROM public.ecr.aws/docker/library/eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S fixflow && adduser -S fixflow -G fixflow
COPY --from=build /app/target/fixflow-backend-*.jar app.jar
USER fixflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
