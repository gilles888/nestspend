# Rapport de mise en production - NestSpend

Date initiale : 2026-03-22
Mise a jour Nginx systeme : 2026-03-22
Environnement : Ubuntu 24.04.4 LTS (x86_64) - Production locale sans Docker

---

## Contexte

Docker n'etait pas disponible sur la machine et l'utilisateur courant (`claude-worker`) ne dispose pas des droits `sudo` sans mot de passe. La strategie adoptee consiste a :

1. Installer manuellement Java JDK 17 et PostgreSQL 18 en tant qu'utilisateur local (dans `/home/claude-worker/tools/`)
2. Compiler le backend Spring Boot avec Maven
3. Lancer PostgreSQL, l'API Spring Boot et Nginx (reverse proxy + frontend statique)

---

## URLs d'acces

### Etat actuel (Nginx maison port 8000, en attendant installation Nginx systeme)

| Service       | URL                                              | Description                          |
|---------------|--------------------------------------------------|--------------------------------------|
| Frontend      | http://localhost:8000                            | Application Angular via Nginx maison |
| Frontend ext. | http://nestspend.gilmotech.be:8000               | Via sous-domaine (port 8000)         |
| API           | http://localhost:8000/api/                       | Backend proxifie par Nginx           |
| API directe   | http://localhost:8080                            | Backend Spring Boot (acces direct)   |
| Swagger       | http://localhost:8000/swagger-ui/index.html      | Documentation interactive de l'API   |
| Health        | http://localhost:8000/actuator/health            | Etat de sante du backend via Nginx   |
| PostgreSQL    | localhost:5432                                   | Base de donnees PostgreSQL 18        |

### Apres installation Nginx systeme (port 80 + HTTPS)

| Service       | URL                                              | Description                          |
|---------------|--------------------------------------------------|--------------------------------------|
| Frontend      | https://nestspend.gilmotech.be                   | Application Angular (HTTPS)          |
| API           | https://nestspend.gilmotech.be/api/              | Backend proxifie                     |
| Swagger       | https://nestspend.gilmotech.be/swagger-ui/index.html | Documentation OpenAPI            |
| Health        | https://nestspend.gilmotech.be/actuator/health   | Health check                         |

---

## Comptes de test

| Email                 | Mot de passe    | Role  | Foyer      |
|-----------------------|-----------------|-------|------------|
| admin@nestspend.be    | AdminProd2024!  | ADMIN | Foyer Prod |
| demo@nestspend.be     | Demo2024!       | ADMIN | Foyer Demo |

---

## Infrastructure deployee

### PostgreSQL 18.3

- Binaires : `/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/`
- Donnees : `/home/claude-worker/pgdata/`
- Log : `/home/claude-worker/pgdata/postgres.log`
- Base de donnees : `nestspend`
- Utilisateur : `nestspend`
- Port : 5432

Migrations Flyway executees avec succes :

| Version | Description          |
|---------|----------------------|
| V1      | init                 |
| V2      | classification_rules |
| V3      | future_events        |
| V4      | budgets              |

Tables crees : `accounts`, `budgets`, `categories`, `classification_rules`, `flyway_schema_history`, `future_events`, `households`, `transactions`, `users`

### Backend Spring Boot

- JAR : `/home/claude-worker/nestspend/api/target/nestspend-api-0.0.1-SNAPSHOT.jar`
- Profil actif : `prod`
- Log : `/home/claude-worker/nestspend/api/app.log`
- Port : 8080 (acces direct) / proxifie par Nginx
- Java : Eclipse Temurin 17.0.11 (depuis `/home/claude-worker/tools/jdk-17.0.11+9/`)

### Nginx - Etat actuel : maison (port 8000)

- Binaire statique : `/home/claude-worker/tools/nginx/nginx`
- Configuration : `/home/claude-worker/tools/nginx/conf/nginx.conf`
- PID : `/home/claude-worker/tools/nginx/nginx.pid`
- Logs acces : `/home/claude-worker/tools/nginx/logs/access.log`
- Logs erreurs : `/home/claude-worker/tools/nginx/logs/error.log`
- Port d'ecoute : **8000** (port 80 inaccessible sans droits root)
- Fichiers statiques servis depuis : `/home/claude-worker/nestspend/web/nestspend-web/dist/nestspend-web/browser/`

### Nginx - Configuration systeme preparee (port 80 + HTTPS)

La configuration Nginx systeme est entierement preparee et documentee.
Pour la deployer, un administrateur avec les droits root doit executer :

```bash
sudo bash /home/claude-worker/nestspend/ops/install-nginx-system.sh
```

Ce script effectue automatiquement les 7 etapes suivantes :

1. Arret du Nginx maison (port 8000)
2. Installation de Nginx via `apt-get install nginx`
3. Deploiement du vhost `/etc/nginx/sites-available/nestspend`
4. Activation du site (lien symbolique dans `sites-enabled/`)
5. Test de la configuration et rechargement Nginx
6. Installation de Certbot (`python3-certbot-nginx`)
7. Obtention du certificat SSL Let's Encrypt pour `nestspend.gilmotech.be`

Fichiers crees :

| Fichier | Description |
|---------|-------------|
| `/home/claude-worker/nestspend/ops/nginx/nestspend.conf` | Vhost Nginx systeme (source) |
| `/home/claude-worker/nestspend/ops/install-nginx-system.sh` | Script d'installation sudo one-shot |

En cas d'echec de Certbot (DNS pas encore propage), le site reste accessible en HTTP.
Commande de renvoi Certbot :

```bash
sudo certbot --nginx -d nestspend.gilmotech.be --non-interactive --agree-tos -m admin@gilmotech.be --redirect
```

---

## Fonctionnalites Nginx configurees

| Fonctionnalite          | Detail                                                            |
|-------------------------|-------------------------------------------------------------------|
| Frontend Angular        | Fichiers statiques servis directement (plus rapide que Node.js)  |
| SPA routing             | `try_files $uri $uri/ /index.html` pour Angular Router           |
| Proxy API               | `/api/*` -> `http://localhost:8080/api/*`                        |
| Proxy Swagger           | `/swagger-ui/*` et `/v3/api-docs` -> backend                     |
| Proxy Actuator          | `/actuator/*` -> backend                                         |
| Compression Gzip        | Activee pour JS, CSS, JSON, SVG, fonts                           |
| Cache assets            | 1 an pour les fichiers JS/CSS (hashes dans les noms de fichiers) |
| Headers de securite     | X-Frame-Options, X-Content-Type-Options, X-XSS-Protection,      |
|                         | Referrer-Policy, Permissions-Policy                              |
| Taille upload max       | 50 Mo (pour les imports de fichiers bancaires)                   |
| Timeouts proxy          | Connect 30s, Send/Read 120s (imports volumineux)                 |
| server_tokens off       | Ne divulgue pas la version Nginx dans les headers HTTP           |

---

## Script de gestion start-prod.sh

Le script `/home/claude-worker/nestspend/start-prod.sh` detecte automatiquement
quel mode Nginx est disponible :

- **Mode "system"** : si Nginx apt est installe et active via systemctl -> commandes `sudo systemctl start/stop/reload nginx`
- **Mode "home"** : sinon -> utilise le binaire statique sur le port 8000

La detection se fait au demarrage du script et bascule transparentement entre les deux modes.

---

## Configuration DNS requise

Le sous-domaine `nestspend.gilmotech.be` doit pointer vers l'IP publique de cette machine.
Ajouter un enregistrement DNS de type A :

```
nestspend.gilmotech.be.  IN  A  <IP_PUBLIQUE_DE_LA_MACHINE>
```

---

## Corrections et migrations apportees

### Session 1 : Deploiement initial

- Installation locale JDK 17, PostgreSQL 18, Maven
- Compilation et lancement du backend Spring Boot (profil prod)
- Serveur Node.js minimal pour le frontend Angular
- Correction du proxy nginx.conf Docker (`/api/` -> `/api/` conserve le prefixe)

### Session 2 : Migration vers Nginx maison

- Remplacement du serveur Node.js par Nginx 1.28.2 en binaire statique
- Configuration du vhost avec SPA routing, proxy API/Swagger/Actuator
- Compression Gzip, headers de securite, cache assets immutables
- Integration dans start-prod.sh (commandes start/stop/reload/status/logs)

### Session 3 : Preparation Nginx systeme (cette session)

- Creation du vhost Nginx systeme `/home/claude-worker/nestspend/ops/nginx/nestspend.conf`
- Creation du script d'installation sudo one-shot `/home/claude-worker/nestspend/ops/install-nginx-system.sh`
- Mise a jour de `start-prod.sh` : detection automatique du mode Nginx (systeme vs maison)
- Suppression de la dependance hard-codee au Nginx maison dans les fonctions start/stop/reload/status/logs
- Documentation complete dans ce rapport

Note : `claude-worker` ne dispose pas de droits `sudo` sans mot de passe sur cette machine.
L'administrateur doit executer le script d'installation manuellement avec `sudo`.

---

## Outils installes localement (sans root)

| Outil | Version | Chemin |
|-------|---------|--------|
| Eclipse Temurin JDK | 17.0.11+9 | `/home/claude-worker/tools/jdk-17.0.11+9/` |
| Apache Maven | 3.9.6 | `/home/claude-worker/tools/apache-maven-3.9.6/` |
| PostgreSQL | 18.3 | `/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/` |
| Nginx | 1.28.2 | `/home/claude-worker/tools/nginx/nginx` (binaire statique) |

---

## Commandes utiles

```bash
# Demarrer tous les services (PostgreSQL + Spring Boot + Nginx)
/home/claude-worker/nestspend/start-prod.sh start

# Verifier l'etat des services
/home/claude-worker/nestspend/start-prod.sh status

# Arreter tous les services
/home/claude-worker/nestspend/start-prod.sh stop

# Recharger Nginx sans coupure (apres modification de la config)
/home/claude-worker/nestspend/start-prod.sh reload

# Consulter les logs
/home/claude-worker/nestspend/start-prod.sh logs

# Installer Nginx systeme (port 80 + HTTPS) - necessite sudo
sudo bash /home/claude-worker/nestspend/ops/install-nginx-system.sh

# Renouveler le certificat SSL (apres installation Nginx systeme)
sudo certbot renew --dry-run

# Acces direct a la base de donnees
export LD_LIBRARY_PATH=/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/lib
/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/bin/psql \
  -h localhost -U nestspend -d nestspend

# Se connecter via l'API (via Nginx)
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@nestspend.be","password":"Demo2024!"}'

# Verifier la sante via Nginx
curl http://localhost:8000/actuator/health
```

---

## Notes de securite

- Le fichier `.env` contient un JWT secret genere aleatoirement de 64 caracteres (base64 URL-safe).
- Le fichier `.env` est reference dans `.gitignore` et ne doit jamais etre commite.
- PostgreSQL utilise actuellement l'authentification "trust" pour les connexions locales. Dans un environnement expose, passer a `scram-sha-256` dans `pg_hba.conf`.
- Le profil Spring `prod` desactive la console H2, limite les endpoints Actuator, et desactive l'affichage SQL.
- Nginx est configure avec `server_tokens off` (ne divulgue pas la version dans les headers HTTP).
- Le header `X-Frame-Options: SAMEORIGIN` protege contre le clickjacking.

---

## Scenario Docker (pour reference future)

Lorsque Docker sera disponible sur la machine, le deploiement se fait via :

```bash
cp /home/claude-worker/nestspend/.env.example /home/claude-worker/nestspend/.env
# Renseigner POSTGRES_PASSWORD et JWT_SECRET dans .env
cd /home/claude-worker/nestspend
docker compose up --build -d
docker compose logs -f
```

Le `docker-compose.yml` existant orchestre PostgreSQL 16, le backend Spring Boot (profil prod) et le frontend Angular avec Nginx. La configuration `nginx.conf` dans le projet Docker a ete corrigee lors de la session 1.
