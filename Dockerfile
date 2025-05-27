FROM node:24-alpine AS web-build
WORKDIR /app
RUN npm install -g pnpm@11.23.0
COPY frontend/web/package.json frontend/web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/web/ ./
RUN pnpm build

FROM node:24-alpine AS admin-build
WORKDIR /app
RUN npm install -g pnpm@11.23.0
COPY frontend/admin/package.json frontend/admin/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/admin/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=web-build /app/dist ./src/main/resources/static
COPY --from=admin-build /app/dist ./src/main/resources/static/admin
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 ticketbox
COPY --from=backend-build /build/target/ticketbox-*.jar app.jar
USER 1001
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
