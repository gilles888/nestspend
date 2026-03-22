#!/usr/bin/env bash
# =============================================================================
# Script de démarrage NestSpend - Production locale (sans Docker)
# =============================================================================
# Démarre PostgreSQL, le backend Spring Boot et le serveur web Angular.
#
# Usage :
#   ./start-prod.sh          Démarrer tous les services
#   ./start-prod.sh stop     Arrêter tous les services
#   ./start-prod.sh status   Vérifier l'état des services
#   ./start-prod.sh logs     Afficher les logs
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="/home/claude-worker/tools"
PG_HOME="$TOOLS_DIR/postgresql-18.3.0-x86_64-unknown-linux-gnu"
PGDATA="/home/claude-worker/pgdata"
JAVA_HOME="$TOOLS_DIR/jdk-17.0.11+9"
API_JAR="$SCRIPT_DIR/api/target/nestspend-api-0.0.1-SNAPSHOT.jar"
WEB_SERVER="$SCRIPT_DIR/web/server.js"
API_LOG="$SCRIPT_DIR/api/app.log"
WEB_LOG="$SCRIPT_DIR/web/web.log"
PG_LOG="$PGDATA/postgres.log"

export LD_LIBRARY_PATH="$PG_HOME/lib:${LD_LIBRARY_PATH:-}"
export PATH="$PG_HOME/bin:$JAVA_HOME/bin:$PATH"

# Variables d'environnement depuis .env
if [ -f "$SCRIPT_DIR/.env" ]; then
  export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
fi

JWT_SECRET="${JWT_SECRET:-}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-NestSpendProd2024!}"
JWT_EXPIRATION_MS="${JWT_EXPIRATION_MS:-86400000}"

start_postgres() {
  echo "[PG] Démarrage de PostgreSQL..."
  if pg_isready -h localhost -p 5432 -U nestspend -q 2>/dev/null; then
    echo "[PG] PostgreSQL déjà en cours d'exécution."
    return 0
  fi
  pg_ctl -D "$PGDATA" -l "$PG_LOG" start
  sleep 3
  pg_isready -h localhost -p 5432 -U nestspend
  echo "[PG] PostgreSQL démarré."
}

start_api() {
  echo "[API] Démarrage du backend Spring Boot..."
  if curl -s http://localhost:8080/actuator/health -o /dev/null 2>/dev/null; then
    echo "[API] Le backend est déjà en cours d'exécution."
    return 0
  fi
  nohup java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -DPOSTGRES_URL="jdbc:postgresql://localhost:5432/nestspend" \
    -DPOSTGRES_USER="nestspend" \
    -DPOSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    -DJWT_SECRET="$JWT_SECRET" \
    -DJWT_EXPIRATION_MS="$JWT_EXPIRATION_MS" \
    -DSERVER_PORT=8080 \
    -jar "$API_JAR" \
    > "$API_LOG" 2>&1 &
  echo "[API] En attente du démarrage (max 60s)..."
  for i in $(seq 1 60); do
    if curl -s http://localhost:8080/actuator/health -o /dev/null 2>/dev/null; then
      echo "[API] Backend opérationnel."
      return 0
    fi
    sleep 1
  done
  echo "[API] ERREUR : Le backend n'a pas démarré dans les temps. Vérifiez $API_LOG"
  exit 1
}

start_web() {
  echo "[WEB] Démarrage du serveur web..."
  if curl -s http://localhost:4200/ -o /dev/null 2>/dev/null; then
    echo "[WEB] Le serveur web est déjà en cours d'exécution."
    return 0
  fi
  nohup node "$WEB_SERVER" > "$WEB_LOG" 2>&1 &
  sleep 2
  echo "[WEB] Serveur web opérationnel."
}

stop_all() {
  echo "Arrêt des services NestSpend..."
  pkill -f "nestspend-api.*jar" 2>/dev/null && echo "[API] Arrêté." || echo "[API] Pas en cours."
  pkill -f "web/server.js" 2>/dev/null && echo "[WEB] Arrêté." || echo "[WEB] Pas en cours."
  pg_ctl -D "$PGDATA" stop 2>/dev/null && echo "[PG] Arrêté." || echo "[PG] Pas en cours."
}

status_all() {
  echo "=== Statut des services NestSpend ==="
  if pg_isready -h localhost -p 5432 -U nestspend -q 2>/dev/null; then
    echo "[PG]  PostgreSQL : EN LIGNE (port 5432)"
  else
    echo "[PG]  PostgreSQL : HORS LIGNE"
  fi
  if curl -s http://localhost:8080/actuator/health -o /dev/null 2>/dev/null; then
    echo "[API] Backend    : EN LIGNE (http://localhost:8080)"
  else
    echo "[API] Backend    : HORS LIGNE"
  fi
  if curl -s http://localhost:4200/ -o /dev/null 2>/dev/null; then
    echo "[WEB] Frontend   : EN LIGNE (http://localhost:4200)"
  else
    echo "[WEB] Frontend   : HORS LIGNE"
  fi
}

case "${1:-start}" in
  start)
    start_postgres
    start_api
    start_web
    echo ""
    echo "=== NestSpend est opérationnel ==="
    echo "  Frontend : http://localhost:4200"
    echo "  API      : http://localhost:8080"
    echo "  Swagger  : http://localhost:8080/swagger-ui.html"
    echo "  Health   : http://localhost:8080/actuator/health"
    ;;
  stop)
    stop_all
    ;;
  status)
    status_all
    ;;
  logs)
    echo "=== Logs Backend ==="
    tail -20 "$API_LOG" 2>/dev/null || echo "(aucun log)"
    echo ""
    echo "=== Logs Web ==="
    tail -10 "$WEB_LOG" 2>/dev/null || echo "(aucun log)"
    ;;
  *)
    echo "Usage: $0 {start|stop|status|logs}"
    exit 1
    ;;
esac
