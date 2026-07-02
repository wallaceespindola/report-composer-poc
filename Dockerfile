# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY frontend ./frontend
RUN mvn -B -q package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/report-composer-poc-*.jar app.jar
# master Job template for LAUNCHER_MODE=k8s (path = MASTER_JOB_TEMPLATE)
COPY k8s/templates/master-job-template.yaml /app/k8s/master-job-template.yaml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
