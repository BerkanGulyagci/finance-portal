# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Download OpenTelemetry Java Agent (2.x — stable, Spring Boot 3 uyumlu)
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.4.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# OTel agent JVM'e eklenir; tüm config env var ile docker-compose'dan gelir.
# -Dsun.net.http.allowRestrictedHeaders=true: HttpURLConnection'ın "Origin" gibi kısıtlı
# header'ları sessizce düşürmesini engeller (ekonomik takvim — TradingView Origin header'ı
# zorunlu kılar; bu flag olmadan 403 döner). JVM başlangıcında set EDİLMELİDİR; runtime
# System.setProperty geç kalır (restricted liste önce initialize olur).
ENTRYPOINT ["java", "-Dsun.net.http.allowRestrictedHeaders=true", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
