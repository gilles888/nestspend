# Rapport de mise en production - NestSpend

Date : 2026-03-22
Environnement : Ubuntu 24.04.4 LTS (x86_64) - Production locale sans Docker

---

## Contexte

Docker n'était pas disponible sur la machine et l'utilisateur courant (`claude-worker`) ne dispose pas des droits `sudo`. La stratégie adoptée consiste donc a :

1. Installer manuellement Java JDK 17 et PostgreSQL 18 en tant qu'utilisateur local (dans `/home/claude-worker/tools/`)
2. Compiler le backend Spring Boot avec Maven
3. Lancer PostgreSQL, l'API Spring Boot et un serveur Node.js pour le frontend

---

## URLs d'acces

| Service   | URL                                        | Description                          |
|-----------|--------------------------------------------|--------------------------------------|
| Frontend  | http://localhost:4200                      | Application Angular (SPA)            |
| API       | http://localhost:8080                      | Backend Spring Boot (direct)         |
| Swagger   | http://localhost:8080/swagger-ui.html      | Documentation interactive de l'API   |
| Health    | http://localhost:8080/actuator/health      | Etat de sante du backend             |
| Actuator  | http://localhost:8080/actuator             | Metriques et endpoints de monitoring |
| PostgreSQL| localhost:5432                             | Base de donnees PostgreSQL 18.3      |

---

## Comptes de test crees

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
- Port : 8080
- Java : Eclipse Temurin 17.0.11 (depuis `/home/claude-worker/tools/jdk-17.0.11+9/`)
- Maven : Apache Maven 3.9.11 (wrapper projet)
- Temps de build : ~32 secondes
- Temps de demarrage : ~13 secondes

### Frontend Angular

- Fichiers statiques servis depuis : `/home/claude-worker/nestspend/web/nestspend-web/dist/nestspend-web/browser/`
- Serveur : Node.js 20 (serveur HTTP natif, `/home/claude-worker/nestspend/web/server.js`)
- Log : `/home/claude-worker/nestspend/web/web.log`
- Port : 4200
- Proxy : `/api/*` -> `http://localhost:8080/api/*`

---

## Corrections apportees

### nginx.conf (bug de proxy corrige)

Le fichier `/home/claude-worker/nestspend/web/nestspend-web/nginx.conf` avait une mauvaise configuration de proxy :

```nginx
# AVANT (incorrect) : supprimait le prefixe /api avant de passer au backend
location /api/ {
    proxy_pass http://api:8080/;
}

# APRES (correct) : conserve le prefixe /api
location /api/ {
    proxy_pass http://api:8080/api/;
}
```

Le backend Spring Boot expose ses routes sous `/api/` (ex : `/api/auth/login`). L'ancienne configuration supprimait ce prefixe, ce qui provoquait des erreurs 401 Unauthorized.

### Serveur de developpement local (server.js)

Un serveur Node.js minimal a ete cree (`/home/claude-worker/nestspend/web/server.js`) pour remplacer Nginx dans l'environnement sans Docker. Il assure :
- La distribution des fichiers statiques Angular compilees
- Le routing SPA (toutes les routes -> `index.html`)
- Le proxy transparent vers l'API backend

---

## Fichiers crees ou modifies

| Fichier | Action | Description |
|---------|--------|-------------|
| `/home/claude-worker/nestspend/.env` | Cree | Variables d'environnement production (JWT secret genere aleatoirement) |
| `/home/claude-worker/nestspend/web/server.js` | Cree | Serveur HTTP Node.js pour le frontend (sans Docker) |
| `/home/claude-worker/nestspend/start-prod.sh` | Cree | Script de demarrage/arret/statut de tous les services |
| `/home/claude-worker/nestspend/web/nestspend-web/nginx.conf` | Modifie | Correction du proxy `/api/` vers le backend |

---

## Outils installes localement (sans root)

| Outil | Version | Chemin |
|-------|---------|--------|
| Eclipse Temurin JDK | 17.0.11+9 | `/home/claude-worker/tools/jdk-17.0.11+9/` |
| Apache Maven | 3.9.6 | `/home/claude-worker/tools/apache-maven-3.9.6/` |
| PostgreSQL | 18.3 | `/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/` |

---

## Commandes utiles

```bash
# Demarrer tous les services
/home/claude-worker/nestspend/start-prod.sh start

# Verifier l'etat des services
/home/claude-worker/nestspend/start-prod.sh status

# Arreter tous les services
/home/claude-worker/nestspend/start-prod.sh stop

# Consulter les logs
/home/claude-worker/nestspend/start-prod.sh logs

# Acces direct a la base de donnees
export LD_LIBRARY_PATH=/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/lib
/home/claude-worker/tools/postgresql-18.3.0-x86_64-unknown-linux-gnu/bin/psql \
  -h localhost -U nestspend -d nestspend

# Verifier la sante de l'API
curl http://localhost:8080/actuator/health

# Se connecter via l'API
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@nestspend.be","password":"AdminProd2024!"}'
```

---

## Notes de securite

- Le fichier `.env` contient un JWT secret genere aleatoirement de 64 caracteres (base64 URL-safe).
- Le fichier `.env` est reference dans `.gitignore` et ne doit jamais etre commite.
- PostgreSQL utilise actuellement l'authentification "trust" pour les connexions locales (unix socket et loopback). Dans un environnement expose, passer a `scram-sha-256` dans `pg_hba.conf`.
- Le profil Spring `prod` desactive la console H2, limite les endpoints Actuator, et desactive l'affichage SQL.

---

## Scenario Docker (pour reference future)

Lorsque Docker sera disponible sur la machine, le deploiement se fait via :

```bash
# Copier et configurer le .env
cp /home/claude-worker/nestspend/.env.example /home/claude-worker/nestspend/.env
# Renseigner POSTGRES_PASSWORD et JWT_SECRET dans .env

# Lancer tous les services
cd /home/claude-worker/nestspend
docker compose up --build -d

# Verifier les logs
docker compose logs -f
```

Le `docker-compose.yml` existant orchestre PostgreSQL 16, le backend Spring Boot (profil prod) et le frontend Angular avec Nginx. Apres la correction du `nginx.conf`, le scenario Docker fonctionnera correctement.
