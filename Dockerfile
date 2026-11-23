# syntax=docker/dockerfile:1

# --- Фронтенд: собираем Vite в статические файлы -------------------------
FROM node:24-alpine AS frontend
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# --- Бэкенд: собираем Spring Boot вместе со статикой React ---------------
FROM maven:3.9.6-eclipse-temurin-21 AS backend
WORKDIR /app/backend

COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests -Dmaven.test.skip=true package

# --- Рантайм: один процесс, один порт ------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV PORT=8080
COPY --from=backend /app/backend/target/callbooking-backend-1.0.0.jar /app/app.jar

EXPOSE 8080

# application.properties читает PORT через server.port=${PORT:8080}.
CMD ["java", "-jar", "/app/app.jar"]
