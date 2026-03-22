#!/usr/bin/env bash
# =============================================================================
# Script d'installation Nginx systeme pour NestSpend
# =============================================================================
# Necessite les droits root. Executer avec :
#   sudo bash /home/claude-worker/nestspend/ops/install-nginx-system.sh
#
# Ce script :
#   1. Arrête le Nginx maison (port 8000) si actif
#   2. Installe Nginx via apt (port 80)
#   3. Deploie le vhost nestspend.gilmotech.be
#   4. Teste et recharge la configuration
#   5. Installe Certbot et obtient le certificat SSL Let's Encrypt
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NESTSPEND_DIR="$(dirname "$SCRIPT_DIR")"
NGINX_MAISON_PID="$NESTSPEND_DIR/../tools/nginx/nginx.pid"
VHOST_SRC="$SCRIPT_DIR/nginx/nestspend.conf"
VHOST_DST="/etc/nginx/sites-available/nestspend"

# Verification que le script tourne en root
if [ "$(id -u)" -ne 0 ]; then
    echo "ERREUR : Ce script doit etre execute en root (sudo)."
    exit 1
fi

echo "============================================================"
echo " Installation Nginx systeme pour NestSpend"
echo "============================================================"

# --- Etape 1 : Arrêt du Nginx maison (port 8000) ---
echo ""
echo "[1/7] Arret du Nginx maison (port 8000)..."
if [ -f "$NGINX_MAISON_PID" ] && kill -0 "$(cat "$NGINX_MAISON_PID")" 2>/dev/null; then
    kill -QUIT "$(cat "$NGINX_MAISON_PID")"
    rm -f "$NGINX_MAISON_PID"
    echo "  Nginx maison arrete (PID file supprime)."
else
    # Tenter pkill au cas ou le PID file est absent
    pkill -f "/home/claude-worker/tools/nginx/nginx" 2>/dev/null \
        && echo "  Nginx maison arrete via pkill." \
        || echo "  Nginx maison n'etait pas en cours."
fi

# --- Etape 2 : Installation de Nginx systeme via apt ---
echo ""
echo "[2/7] Installation de Nginx systeme via apt..."
DEBIAN_FRONTEND=noninteractive apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
echo "  Nginx systeme installe."

# --- Etape 3 : Deploiement du vhost ---
echo ""
echo "[3/7] Deploiement du vhost /etc/nginx/sites-available/nestspend..."
cp "$VHOST_SRC" "$VHOST_DST"
echo "  Vhost copie depuis $VHOST_SRC"

# --- Etape 4 : Activation du site et desactivation du default ---
echo ""
echo "[4/7] Activation du site nestspend..."
if [ ! -L /etc/nginx/sites-enabled/nestspend ]; then
    ln -s "$VHOST_DST" /etc/nginx/sites-enabled/nestspend
    echo "  Lien symbolique cree : /etc/nginx/sites-enabled/nestspend"
else
    echo "  Lien symbolique deja present."
fi
# Desactiver le site par defaut pour eviter les conflits
if [ -L /etc/nginx/sites-enabled/default ]; then
    rm /etc/nginx/sites-enabled/default
    echo "  Site default desactive."
fi

# --- Etape 5 : Test et rechargement de Nginx ---
echo ""
echo "[5/7] Test de la configuration Nginx..."
nginx -t
systemctl enable nginx
systemctl reload nginx 2>/dev/null || systemctl start nginx
echo "  Nginx systeme operationnel sur le port 80."

# --- Etape 6 : Installation de Certbot ---
echo ""
echo "[6/7] Installation de Certbot (Let's Encrypt)..."
DEBIAN_FRONTEND=noninteractive apt-get install -y certbot python3-certbot-nginx
echo "  Certbot installe."

# --- Etape 7 : Obtention du certificat SSL ---
echo ""
echo "[7/7] Obtention du certificat SSL Let's Encrypt pour nestspend.gilmotech.be..."
if certbot --nginx \
    -d nestspend.gilmotech.be \
    --non-interactive \
    --agree-tos \
    -m admin@gilmotech.be \
    --redirect; then
    echo ""
    echo "  Certificat SSL obtenu et configure avec succes."
    echo "  Nginx ecoute maintenant sur les ports 80 (redirect HTTPS) et 443 (HTTPS)."
else
    echo ""
    echo "  AVERTISSEMENT : Certbot a echoue (DNS pas encore propage ou port 80 non accessible)."
    echo "  NestSpend est disponible en HTTP sur http://nestspend.gilmotech.be"
    echo ""
    echo "  Une fois le DNS propage, relancer :"
    echo "  sudo certbot --nginx -d nestspend.gilmotech.be --non-interactive --agree-tos -m admin@gilmotech.be --redirect"
fi

echo ""
echo "============================================================"
echo " Resultat final"
echo "============================================================"
echo "  HTTP    : http://nestspend.gilmotech.be"
echo "  HTTPS   : https://nestspend.gilmotech.be (si certbot reussi)"
echo "  API     : https://nestspend.gilmotech.be/api/"
echo "  Swagger : https://nestspend.gilmotech.be/swagger-ui/index.html"
echo "  Health  : https://nestspend.gilmotech.be/actuator/health"
echo ""
echo "  Commandes utiles :"
echo "  sudo systemctl status nginx"
echo "  sudo nginx -t"
echo "  sudo systemctl reload nginx"
echo "  sudo certbot renew --dry-run"
echo "  sudo journalctl -u nginx -f"
echo "============================================================"
