# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# pom.xml önce kopyala — dependency layer cache'lenir
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kaynak kodu sonra kopyala — sadece src değişince bu layer yenilenir
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/log-consumer-*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
