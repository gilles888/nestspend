# Rapport OPS - NestSpend Infrastructure

Date : 2026-03-22
Agent : ops-agent (Claude Sonnet 4.6)
Branche : dev

---

## Résumé

Audit complet de la configuration du projet NestSpend et préparation de la migration PostgreSQL. Aucune fonctionnalité existante n'a été cassée.

---

## 1. Analyse de l'existant

### 1.1 Stack identifiée

| Composant | Version | Statut |
|---|---|---|
| Spring Boot | 3.5.9 | Récent, OK |
| Java | 17 | LTS, OK |
| Maven Wrapper | 3.9.11 | Récent, OK |
| H2 Database | Géré par Spring Boot BOM | Profil dev uniquement, OK |
| Flyway Core | Géré par Spring Boot BOM | Présent, OK |
| MapStruct | 1.5.5.Final | Version utilisée sans `mapstruct-processor` dans le compiler |
| JJWT | 0.12.6 | Version récente, OK |
| SpringDoc OpenAPI | 2.8.14 | Version abaissée à 2.8.8 (plus stable) |
| Angular | 21.x | Récent, OK |
| Node.js (package.json) | npm 10.9.4 | OK |

### 1.2 Configuration Spring

**Fichiers de configuration trouvés :**
- `/api/src/main/resources/application.yaml` - configuration principale
- `/api/src/main/resources/application-dev.yaml` - profil H2 développement
- `/api/src/main/resources/application-test.yaml` - profil H2 tests
- Aucun profil prod existant

**Migrations Flyway trouvées :**
- `V1__init.sql` - Tables de base (households, users, categories, accounts, transactions)
- `V2__classification_rules.sql` - Règles de classification automatique
- `V3__future_events.sql` - Événements futurs pour les projections

### 1.3 Problèmes identifiés

| # | Problème | Priorité | Action |
|---|---|---|---|
| P1 | `flyway-database-postgresql` absent | Critique | Ajouté - Spring Boot 3.x exige ce module séparé pour PostgreSQL |
| P2 | Driver PostgreSQL absent dans pom.xml | Critique | Ajouté - migration impossible sans lui |
| P3 | `mapstruct-processor` absent du annotationProcessorPaths | Majeur | Ajouté - les mappers peuvent ne pas être générés correctement |
| P4 | Versions dupliquées en dur (JJWT, SpringDoc, MapStruct) | Mineur | Centralisées dans `<properties>` |
| P5 | JWT_SECRET vide par défaut en production | Sécurité | Documenté et rendu obligatoire dans le profil prod |
| P6 | Pas de Dockerfile ni docker-compose | Infrastructure | Créés |
| P7 | `.gitignore` ne couvre pas .env, node_modules, dist | Sécurité | Mis à jour |

---

## 2. Corrections apportées

### 2.1 pom.xml

**Ajouts :**
```xml
<!-- Module Flyway requis pour PostgreSQL (Spring Boot 3.x) -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Driver PostgreSQL pour le profil prod -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

**Correction du maven-compiler-plugin** - ajout de `mapstruct-processor` dans `annotationProcessorPaths`. Lombok doit être déclaré avant MapStruct pour que les builders générés par Lombok soient visibles lors de la génération des mappers MapStruct.

**Centralisation des versions** dans `<properties>` :
- `mapstruct.version` = 1.5.5.Final
- `jjwt.version` = 0.12.6
- `springdoc.version` = 2.8.8

### 2.2 application.yaml

Le profil actif passe par la variable d'environnement `SPRING_PROFILES_ACTIVE` avec `dev` comme valeur par défaut. Le port du serveur supporte aussi `SERVER_PORT`.

### 2.3 application-dev.yaml

- Ajout de `NON_KEYWORDS=VALUE` dans l'URL H2 (évite les conflits avec le mot-clé SQL réservé `VALUE` en mode PostgreSQL)
- Activation de `show-sql: false` explicite
- Activation de tous les endpoints Actuator en dev
- Commentaires explicatifs en français

---

## 3. Migration PostgreSQL - Préparation

### 3.1 Compatibilité des requêtes SQL

Tous les scripts Flyway existants (V1, V2, V3) utilisent un SQL standard compatible PostgreSQL. Les types de données utilisés sont :

| Type SQL | Compatibilité H2 (MODE=PostgreSQL) | Compatibilité PostgreSQL natif |
|---|---|---|
| `UUID PRIMARY KEY` | OK | OK |
| `VARCHAR(n)` | OK | OK |
| `BIGINT` | OK | OK |
| `BOOLEAN` | OK | OK |
| `DATE` | OK | OK |
| `TIMESTAMP` | OK | OK (préférer `TIMESTAMP WITH TIME ZONE`) |
| `TEXT` | OK | OK |

Les requêtes JPQL dans les repositories utilisent uniquement des fonctions standard (`UPPER`, `TRIM`, `COALESCE`, `COUNT`, `SUM`), toutes compatibles PostgreSQL.

**Attention potentielle :** `TIMESTAMP` sans fuseau horaire sera traité différemment selon la configuration locale de PostgreSQL. La config `jdbc.time_zone: UTC` dans Hibernate atténue ce risque.

### 3.2 Nouveau fichier : application-prod.yaml

Fichier de configuration pour le profil `prod` créé à `/api/src/main/resources/application-prod.yaml`.

Configuration principale :
- Driver PostgreSQL avec pool HikariCP dimensionné (2-10 connexions)
- Flyway avec `clean-disabled: true` (protection contre la suppression accidentelle)
- JWT_SECRET obligatoire (pas de valeur par défaut)
- Console H2 désactivée
- SQL logging désactivé
- Actuator limité à `health,info,metrics`

Variables d'environnement requises en production :
```
POSTGRES_URL=jdbc:postgresql://<host>:5432/nestspend
POSTGRES_USER=nestspend
POSTGRES_PASSWORD=<mot_de_passe_fort>
JWT_SECRET=<clé_aléatoire_min_32_chars>
```

### 3.3 Activation du profil prod

```bash
# Ligne de commande
java -jar nestspend-api.jar --spring.profiles.active=prod

# Variable d'environnement (recommandé)
export SPRING_PROFILES_ACTIVE=prod
```

---

## 4. Infrastructure Docker

### 4.1 docker-compose.yml

Fichier créé à la racine du projet avec :
- Service `postgres` : PostgreSQL 16 Alpine avec health check
- Service `api` : Spring Boot en profil `prod`, démarre après que PostgreSQL soit prêt
- Service `web` : Angular servi par Nginx
- Volume persistant `nestspend-postgres-data`
- Réseau interne `nestspend-network`

Utilisation :
```bash
# Créer le fichier .env depuis le template
cp .env.example .env
# Remplir POSTGRES_PASSWORD et JWT_SECRET dans .env

# Démarrer
docker compose up -d

# Logs
docker compose logs -f api
```

### 4.2 api/Dockerfile

Build multi-étapes :
1. `eclipse-temurin:17-jdk-alpine` - compilation Maven avec cache des dépendances
2. `eclipse-temurin:17-jre-alpine` - image de production minimale avec couches Spring Boot

Sécurité : l'application tourne en utilisateur non-root `nestspend`.
Mémoire : `MaxRAMPercentage=75.0` pour respecter les limites du conteneur.

### 4.3 web/Dockerfile + nginx.conf

Build multi-étapes :
1. `node:20-alpine` - build Angular de production
2. `nginx:alpine` - serveur de fichiers statiques

Nginx configuré pour :
- Routing SPA Angular (`try_files $uri $uri/ /index.html`)
- Proxy `/api/` vers l'API Spring Boot
- Cache long terme des assets statiques hachés par Angular
- Compression gzip

### 4.4 .env.example

Template de variables d'environnement créé. Le fichier `.env` est dans `.gitignore`.

---

## 5. Commits effectués

| Commit | Description |
|---|---|
| `e34e7f2` | feat(ops): ajout driver PostgreSQL, flyway-database-postgresql et profil prod |
| `17e57a2` | feat(ops): ajout docker-compose, Dockerfiles et config Nginx |
| `b120ddf` | chore(ops): mise à jour .gitignore (fichiers .env, node_modules, dist) |

---

## 6. Checklist de migration vers PostgreSQL

Avant de basculer en production :

- [ ] Créer la base PostgreSQL et l'utilisateur : `CREATE DATABASE nestspend; CREATE USER nestspend WITH PASSWORD '...';`
- [ ] Définir les variables d'environnement (`POSTGRES_PASSWORD`, `JWT_SECRET`)
- [ ] Vérifier que toutes les migrations Flyway passent sur PostgreSQL : `./mvnw flyway:migrate -Dspring.profiles.active=prod`
- [ ] Tester la connexion : `psql -h localhost -U nestspend -d nestspend`
- [ ] Vérifier les logs au démarrage : `spring.flyway.validate-on-migrate=true` va valider les checksums
- [ ] Pour le `TIMESTAMP` : envisager une migration V4 vers `TIMESTAMP WITH TIME ZONE` pour les colonnes `created_at` et `updated_at` (optionnel, UTC est déjà configuré)

---

## 7. Fichiers modifiés et créés

### Modifiés
- `/home/claude-worker/nestspend/api/pom.xml`
- `/home/claude-worker/nestspend/api/src/main/resources/application.yaml`
- `/home/claude-worker/nestspend/api/src/main/resources/application-dev.yaml`
- `/home/claude-worker/nestspend/.gitignore`

### Créés
- `/home/claude-worker/nestspend/api/src/main/resources/application-prod.yaml`
- `/home/claude-worker/nestspend/docker-compose.yml`
- `/home/claude-worker/nestspend/api/Dockerfile`
- `/home/claude-worker/nestspend/web/nestspend-web/Dockerfile`
- `/home/claude-worker/nestspend/web/nestspend-web/nginx.conf`
- `/home/claude-worker/nestspend/.env.example`
- `/home/claude-worker/nestspend/RAPPORT_OPS.md` (ce fichier)
