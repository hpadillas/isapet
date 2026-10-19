# syntax=docker/dockerfile:1

# ---- Etapa 1: compilación con Maven (no requiere Java ni Maven en el anfitrión) ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Descarga de dependencias en una capa propia para aprovechar la caché de Docker.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Las pruebas de integración necesitan PostgreSQL, que no existe durante el build de la imagen.
# Se ejecutan aparte con el servicio "backend-tests" (ver README).
RUN mvn -B -q package -DskipTests \
    && cp target/isapet-backend-*.jar /workspace/app.jar

# ---- Etapa 2: imagen de ejecución solo con el JRE ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system isapet \
    && useradd --system --gid isapet --home-dir /app isapet

COPY --from=build /workspace/app.jar /app/app.jar
USER isapet

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Endpoint público que además consulta la base de datos: valida aplicación + conexión a PostgreSQL.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/v1/simulaciones/servicios" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
