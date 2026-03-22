#!/usr/bin/env bash
# =============================================================================
# Script de démarrage NestSpend - Production locale (sans Docker)
# =============================================================================
# Démarre PostgreSQL, le backend Spring Boot et Nginx (reverse proxy + frontend).
#
# Mode Nginx détecté automatiquement :
#   - Si Nginx système est installé (/usr/sbin/nginx) -> utilise systemctl (port 80/443)
#   - Sinon -> utilise le Nginx maison (port 8000)
#
# Usage :
#   ./start-prod.sh          Démarrer tous les services
#   ./start-prod.sh stop     Arrêter tous les services
#   ./start-prod.sh status   Vérifier l'état des services
#   ./start-prod.sh logs     Afficher les logs
#   ./start-prod.sh reload   Recharger la configuration Nginx sans coupure
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_DIR="/home/claude-worker/tools"
PG_HOME="$TOOLS_DIR/postgresql-18.3.0-x86_64-unknown-linux-gnu"
PGDATA="/home/claude-worker/pgdata"
JAVA_HOME="$TOOLS_DIR/jdk-17.0.11+9"
# Nginx maison (fallback si le Nginx système n'est pas disponible)
NGINX_MAISON_BIN="$TOOLS_DIR/nginx/nginx"
NGINX_MAISON_CONF="$TOOLS_DIR/nginx/conf/nginx.conf"
NGINX_MAISON_PID="$TOOLS_DIR/nginx/nginx.pid"
API_JAR="$SCRIPT_DIR/api/target/nestspend-api-0.0.1-SNAPSHOT.jar"
API_LOG="$SCRIPT_DIR/api/app.log"
# Logs Nginx maison
NGINX_LOG="$TOOLS_DIR/nginx/logs/access.log"
NGINX_ERROR_LOG="$TOOLS_DIR/nginx/logs/error.log"
PG_LOG="$PGDATA/postgres.log"

export LD_LIBRARY_PATH="$PG_HOME/lib:${LD_LIBRARY_PATH:-}"
export PATH="$PG_HOME/bin:$JAVA_HOME/bin:$PATH"

# Variables d'environnement depuis .env
if [ -f "$SCRIPT_DIR/.env" ]; then
  # shellcheck disable=SC2046
  export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
fi

JWT_SECRET="${JWT_SECRET:-}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-NestSpendProd2024!}"
JWT_EXPIRATION_MS="${JWT_EXPIRATION_MS:-86400000}"

# Détection du mode Nginx : système (apt, port 80) ou maison (port 8000)
if [ -x "/usr/sbin/nginx" ] && systemctl is-enabled nginx >/dev/null 2>&1; then
    NGINX_MODE="system"
else
    NGINX_MODE="home"
fi

# -----------------------------------------------------------------------------
# PostgreSQL
# -----------------------------------------------------------------------------
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

stop_postgres() {
  pg_ctl -D "$PGDATA" stop 2>/dev/null && echo "[PG] Arrêté." || echo "[PG] Pas en cours."
}

# -----------------------------------------------------------------------------
# Backend Spring Boot
# -----------------------------------------------------------------------------
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

stop_api() {
  pkill -f "nestspend-api.*jar" 2>/dev/null && echo "[API] Arrêté." || echo "[API] Pas en cours."
}

# -----------------------------------------------------------------------------
# Nginx - Mode système (sudo systemctl, port 80/443)
# -----------------------------------------------------------------------------
start_nginx_system() {
  echo "[WEB] Nginx système (port 80/443)..."
  if systemctl is-active nginx >/dev/null 2>&1; then
    echo "[WEB] Nginx système déjà actif."
    return 0
  fi
  sudo systemctl start nginx
  echo "[WEB] Nginx système démarré."
}

stop_nginx_system() {
  if systemctl is-active nginx >/dev/null 2>&1; then
    sudo systemctl stop nginx && echo "[WEB] Nginx système arrêté." || true
  else
    echo "[WEB] Nginx système déjà arrêté."
  fi
}

reload_nginx_system() {
  sudo nginx -t && sudo systemctl reload nginx && echo "[WEB] Nginx système rechargé." || echo "[WEB] Erreur lors du rechargement Nginx système."
}

# -----------------------------------------------------------------------------
# Nginx - Mode maison (binaire statique, port 8000)
# -----------------------------------------------------------------------------
start_nginx_home() {
  echo "[WEB] Démarrage du Nginx maison (port 8000)..."
  if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
    echo "[WEB] Nginx maison déjà en cours d'exécution (PID $(cat "$NGINX_MAISON_PID"))."
    return 0
  fi
  # Tester la configuration avant de démarrer
  "$NGINX_MAISON_BIN" -t -c "$NGINX_MAISON_CONF" 2>/dev/null || {
    echo "[WEB] ERREUR : Configuration Nginx invalide !"
    "$NGINX_MAISON_BIN" -t -c "$NGINX_MAISON_CONF"
    exit 1
  }
  "$NGINX_MAISON_BIN" -c "$NGINX_MAISON_CONF"
  sleep 1
  if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
    echo "[WEB] Nginx maison opérationnel (port 8000, PID $(cat "$NGINX_MAISON_PID"))."
  else
    echo "[WEB] ERREUR : Nginx maison n'a pas démarré. Vérifiez $NGINX_ERROR_LOG"
    exit 1
  fi
}

stop_nginx_home() {
  if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
    kill -QUIT "$(cat "$NGINX_MAISON_PID")" 2>/dev/null && echo "[WEB] Nginx maison arrêté." || true
    rm -f "$NGINX_MAISON_PID"
  else
    echo "[WEB] Nginx maison pas en cours."
  fi
}

reload_nginx_home() {
  if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
    "$NGINX_MAISON_BIN" -t -c "$NGINX_MAISON_CONF" && \
      kill -HUP "$(cat "$NGINX_MAISON_PID")" && \
      echo "[WEB] Nginx maison rechargé (configuration mise à jour, sans coupure)."
  else
    echo "[WEB] Nginx maison n'est pas en cours. Utilisez 'start'."
  fi
}

# Dispatchers selon le mode détecté
start_nginx() {
  if [ "$NGINX_MODE" = "system" ]; then
    start_nginx_system
  else
    start_nginx_home
  fi
}

stop_nginx() {
  if [ "$NGINX_MODE" = "system" ]; then
    stop_nginx_system
  else
    stop_nginx_home
  fi
}

reload_nginx() {
  if [ "$NGINX_MODE" = "system" ]; then
    reload_nginx_system
  else
    reload_nginx_home
  fi
}

# -----------------------------------------------------------------------------
# Arrêt du serveur Node.js (remplacé par Nginx)
# -----------------------------------------------------------------------------
stop_nodejs_if_running() {
  local NODE_PID
  NODE_PID=$(pgrep -f "web/server.js" 2>/dev/null || true)
  if [ -n "$NODE_PID" ]; then
    kill "$NODE_PID" 2>/dev/null && echo "[WEB] Serveur Node.js arrêté (PID $NODE_PID)." || true
  fi
}

# -----------------------------------------------------------------------------
# Statut
# -----------------------------------------------------------------------------
status_all() {
  echo "=== Statut des services NestSpend ==="
  echo "[MODE] Nginx : $NGINX_MODE"
  if pg_isready -h localhost -p 5432 -U nestspend -q 2>/dev/null; then
    echo "[PG]   PostgreSQL : EN LIGNE (port 5432)"
  else
    echo "[PG]   PostgreSQL : HORS LIGNE"
  fi
  if curl -s http://localhost:8080/actuator/health -o /dev/null 2>/dev/null; then
    echo "[API]  Backend    : EN LIGNE (port 8080)"
  else
    echo "[API]  Backend    : HORS LIGNE"
  fi
  if [ "$NGINX_MODE" = "system" ]; then
    if systemctl is-active nginx >/dev/null 2>&1; then
      echo "[WEB]  Nginx      : EN LIGNE (système, port 80/443)"
    else
      echo "[WEB]  Nginx      : HORS LIGNE (système)"
    fi
  else
    if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
      echo "[WEB]  Nginx      : EN LIGNE (maison, port 8000, PID $(cat "$NGINX_MAISON_PID"))"
    else
      echo "[WEB]  Nginx      : HORS LIGNE (maison)"
    fi
  fi
  if pgrep -f "web/server.js" >/dev/null 2>&1; then
    echo "[WARN] Serveur Node.js encore actif (port 4200) - utiliser 'stop' pour l'arrêter"
  fi
}

# -----------------------------------------------------------------------------
# Logs
# -----------------------------------------------------------------------------
logs_all() {
  echo "=== Logs Backend (20 dernières lignes) ==="
  tail -20 "$API_LOG" 2>/dev/null || echo "(aucun log)"
  echo ""
  if [ "$NGINX_MODE" = "system" ]; then
    echo "=== Logs Nginx système (20 dernières lignes) ==="
    sudo journalctl -u nginx --no-pager -n 20 2>/dev/null || tail -20 /var/log/nginx/access.log 2>/dev/null || echo "(aucun log)"
    echo ""
    echo "=== Logs Nginx erreurs système (10 dernières lignes) ==="
    tail -10 /var/log/nginx/error.log 2>/dev/null || echo "(aucun log)"
  else
    echo "=== Logs Nginx accès (10 dernières lignes) ==="
    tail -10 "$NGINX_LOG" 2>/dev/null || echo "(aucun log)"
    echo ""
    echo "=== Logs Nginx erreurs (10 dernières lignes) ==="
    tail -10 "$NGINX_ERROR_LOG" 2>/dev/null || echo "(aucun log)"
  fi
}

# -----------------------------------------------------------------------------
# Commandes
# -----------------------------------------------------------------------------
case "${1:-start}" in
  start)
    stop_nodejs_if_running
    start_postgres
    start_api
    start_nginx
    echo ""
    echo "=== NestSpend est opérationnel ==="
    if [ "$NGINX_MODE" = "system" ]; then
      echo "  Application  : http://nestspend.gilmotech.be"
      echo "  Application  : https://nestspend.gilmotech.be (si SSL configuré)"
      echo "  API directe  : http://localhost:8080"
      echo "  Swagger      : https://nestspend.gilmotech.be/swagger-ui/index.html"
      echo "  Health       : https://nestspend.gilmotech.be/actuator/health"
    else
      echo "  Application  : http://localhost:8000    (Nginx maison - frontend + proxy API)"
      echo "  Application  : http://nestspend.gilmotech.be:8000"
      echo "  API directe  : http://localhost:8080"
      echo "  Swagger      : http://localhost:8000/swagger-ui/index.html"
      echo "  Health       : http://localhost:8000/actuator/health"
      echo ""
      echo "  CONSEIL : Pour passer sur le port 80/HTTPS, exécuter :"
      echo "  sudo bash /home/claude-worker/nestspend/ops/install-nginx-system.sh"
    fi
    ;;
  stop)
    echo "Arrêt des services NestSpend..."
    stop_nginx
    stop_api
    stop_nodejs_if_running
    stop_postgres
    ;;
  status)
    status_all
    ;;
  reload)
    reload_nginx
    ;;
  logs)
    logs_all
    ;;
  *)
    echo "Usage: $0 {start|stop|status|reload|logs}"
    exit 1
    ;;
esac
