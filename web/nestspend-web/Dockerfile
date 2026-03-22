# =============================================================================
# Dockerfile - NestSpend Web (Angular)
# =============================================================================
# Construction multi-étapes :
#   1. Étape "build"  : compilation Angular avec Node.js
#   2. Étape "runtime": serveur Nginx pour servir les fichiers statiques
# =============================================================================

# ---------------------------------------------------------------------------
# Étape 1 : Compilation Angular
# ---------------------------------------------------------------------------
FROM node:20-alpine AS build

WORKDIR /workspace

# Copie des fichiers de dépendances en premier (cache Docker)
COPY package.json package-lock.json ./

# Installation des dépendances npm
RUN npm ci --ignore-scripts

# Copie du code source
COPY . .

# Build de production Angular
RUN npm run build

# ---------------------------------------------------------------------------
# Étape 2 : Serveur Nginx
# ---------------------------------------------------------------------------
FROM nginx:alpine AS runtime

# Copie de la configuration Nginx personnalisée
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copie des fichiers buildés Angular
COPY --from=build /workspace/dist/nestspend-web/browser /usr/share/nginx/html

# Port exposé
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
