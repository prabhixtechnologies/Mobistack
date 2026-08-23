FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN apk add --no-cache maven \
    && mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S fixflow && adduser -S fixflow -G fixflow
COPY --from=build /app/target/fixflow-backend-*.jar app.jar
USER fixflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
