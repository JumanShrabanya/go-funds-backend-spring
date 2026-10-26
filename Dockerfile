# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies separately from source for faster rebuilds
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser

COPY --from=build /workspace/target/*.jar app.jar

# Neon hands out postgres://... URLs; Spring requires jdbc:postgresql://...
# Convert at startup so either form works in DATABASE_URL.
COPY <<'EOF' /app/entrypoint.sh
#!/bin/sh
set -e
case "${DATABASE_URL:-}" in
  postgres://*|postgresql://*)
    rest="${DATABASE_URL#*://}"
    host="${rest%%/*}"
    pathq="${rest#*/}"
    path="${pathq%%\?*}"
    query=""
    case "$pathq" in
      *\?*)
        OLDIFS=$IFS; IFS='&'
        for p in ${pathq#*\?}; do
          case "$p" in channel_binding=*|"") continue ;; esac
          if [ -z "$query" ]; then query="$p"; else query="${query}&$p"; fi
        done
        IFS=$OLDIFS
        ;;
    esac
    if [ -n "$query" ]; then
      DATABASE_URL="jdbc:postgresql://${host}/${path}?${query}"
    else
      DATABASE_URL="jdbc:postgresql://${host}/${path}"
    fi
    export DATABASE_URL
    echo "Converted postgres:// DATABASE_URL to JDBC (host=${host}, db=${path})"
    ;;
esac
exec java -XX:MaxRAMPercentage=75.0 -jar app.jar
EOF
RUN chmod +x /app/entrypoint.sh && chown appuser:appuser /app/app.jar /app/entrypoint.sh
USER appuser

EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
