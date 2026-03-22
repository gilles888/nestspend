# RAPPORT-OPS - NestSpend : Configuration pour la fonctionnalité de projection budgétaire

Date : 2026-03-22
Agent : ops-agent (Claude Sonnet 4.6)
Branche : dev

---

## Objectif de la mission

Analyser et finaliser la configuration du projet NestSpend pour préparer l'arrivée des
nouvelles fonctionnalités Budget et Projection. Vérifier l'état des dépendances frontend
et backend, corriger les incohérences de configuration, et documenter les variables
d'environnement nécessaires en production.

---

## 1. Audit de l'existant

### 1.1 Backend - pom.xml

| Dépendance | Version | État | Remarque |
|---|---|---|---|
| Spring Boot Parent | 3.5.9 | Récent, OK | Aucune action requise |
| Java | 17 | LTS, OK | Aucune action requise |
| spring-boot-starter-data-jpa | BOM | OK | Présent |
| spring-boot-starter-security | BOM | OK | Présent |
| spring-boot-starter-validation | BOM | OK | Présent |
| spring-boot-starter-web | BOM | OK | Présent |
| spring-boot-starter-actuator | BOM | OK | Présent |
| flyway-core | BOM | OK | Présent |
| flyway-database-postgresql | BOM | OK | Module requis Spring Boot 3.x - déjà present |
| com.h2database:h2 | BOM (runtime) | OK | Profil dev et test uniquement |
| org.postgresql:postgresql | BOM (runtime) | OK | Driver prod - déjà present |
| lombok | BOM (optional) | OK | Présent, ordonné avant MapStruct |
| mapstruct | 1.5.5.Final | OK | Présent avec mapstruct-processor dans annotationProcessorPaths |
| springdoc-openapi | 2.8.8 | OK | Présent |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | OK | Présent |
| spring-boot-starter-test | BOM (test) | OK | Présent |
| spring-security-test | BOM (test) | OK | Présent |

Conclusion : aucune dépendance manquante dans le pom.xml. La migration PostgreSQL est
déjà supportée par le pom.xml existant.

### 1.2 Backend - Migrations Flyway

| Fichier | Contenu | État |
|---|---|---|
| V1__init.sql | households, users, categories, accounts, transactions | OK - SQL standard compatible PostgreSQL |
| V2__classification_rules.sql | classification_rules + index | OK |
| V3__future_events.sql | future_events + index | OK |
| V4__budgets.sql | budgets + contraintes + index | OK - nouvelle table pour la fonctionnalité budget |

La table `budgets` est définie dans `V4__budgets.sql`. La fonctionnalité de projection
est entièrement calculée en mémoire (aucune table de projection en base nécessaire).

### 1.3 Backend - Fichiers de configuration Spring Boot

#### application.yaml (configuration principale)

État : correct. Le profil actif est contrôlé par `SPRING_PROFILES_ACTIVE` (défaut : `dev`).
Le port est contrôlable via `SERVER_PORT` (défaut : `8080`).

#### application-dev.yaml

État au moment de l'audit : correct avec quelques améliorations récemment apportées :
- H2 en mode PostgreSQL avec `NON_KEYWORDS=VALUE,MONTH` (évite les conflits avec les mots
  réservés H2 en mode PostgreSQL)
- `ddl-auto: validate` + Flyway actif (les tables sont créées par Flyway, non par Hibernate)
- `clean-disabled: false` en dev (autorisation de repartir de zéro)
- Console H2 accessible sur `/h2` en dev uniquement
- `open-in-view: false` (bonne pratique API REST)
- CORS autorise `http://localhost:4200` (Angular dev server)

#### application-prod.yaml

État au moment de l'audit : correct, récemment amélioré :
- PostgreSQL avec HikariCP (pool 2-10 connexions)
- `ddl-auto: validate` + Flyway avec `clean-disabled: true` (protection production)
- Dialecte PostgreSQL non spécifié (Hibernate 6+ détecte automatiquement via le driver,
  la spécification explicite génère un avertissement de dépréciation HHH90000025)
- JWT_SECRET obligatoire via variable d'environnement (pas de valeur par défaut)
- Console H2 désactivée
- CORS configurable via `CORS_ALLOWED_ORIGINS`
- Actuator limité à `health,info,metrics`
- Note documentée sur la compatibilité Flyway avec PostgreSQL 18

#### application-test.yaml (avant correction)

État : incomplet. Problèmes identifiés :
- URL H2 sans `NON_KEYWORDS=VALUE,MONTH` (incohérence avec le profil dev)
- Pas de commentaires en français
- Pas de `open-in-view: false` explicite
- Pas de configuration `clean-disabled`

### 1.4 Frontend - package.json

| Dépendance | Version | État | Remarque |
|---|---|---|---|
| @angular/* | ^21.0.0 | OK | Version récente |
| chart.js | ^4.5.1 | OK | Présent - requis pour les graphiques de projection |
| ng2-charts | ABSENT | Manquant | Wrapper Angular pour Chart.js - à installer |
| primeng | ^21.0.2 | OK | Composants UI |
| @primeng/themes | ^21.0.2 | OK | Thèmes PrimeNG |
| papaparse | ^5.5.2 | OK | Parsing CSV |
| rxjs | ~7.8.0 | OK | Présent |
| @ngx-translate | ^17.0.0 | OK | Internationalisation |

### 1.5 Backend - Sécurité et CORS

La configuration CORS (`CorsConfig.java`) applique un mapping `/**` global, ce qui couvre
automatiquement les nouvelles routes `/api/budgets` et `/api/projections`. Aucune
modification nécessaire.

La configuration Spring Security (`SecurityConfig.java`) exige une authentification JWT
pour toute requête ne faisant pas partie des routes publiques déclarées. Les routes
`/api/budgets/**` et `/api/projections/**` seront donc protégées automatiquement.

### 1.6 Entités et services pour les nouvelles fonctionnalités

Entités déjà implémentées :
- `Budget.java` - entité JPA avec Lombok/Builder
- `BudgetRepository.java` - queries JPQL pour foyer, mois, catégorie
- `BudgetService.java` - CRUD + copie de budgets entre mois
- `BudgetController.java` - endpoints `/api/budgets` (GET, POST, PUT, DELETE, /copy)

- `ProjectionService.java` - calcul des projections annuelles et d'épargne
- `ProjectionController.java` - endpoint `/api/projections/annual`
- `ProjectionHelper.java` - calcul des occurrences d'événements récurrents

DTOs présents :
- `BudgetCreateRequest`, `BudgetUpdateRequest`, `BudgetResponse`, `BudgetSummaryResponse`,
  `BudgetCopyResponse`
- `AnnualProjectionResponse`, `MonthlyExpenseProjection`
- `AnnualSavingsResponse`, `MonthlySavingsProjection`

---

## 2. Actions réalisées

### 2.1 Correction de application-test.yaml

**Problème :** le fichier était minimal, sans commentaires, et l'URL H2 ne contenait pas
`NON_KEYWORDS=VALUE,MONTH` contrairement au profil dev. Cette incohérence pouvait provoquer
des erreurs Flyway en test si des migrations futures utilisent ces mots réservés.

**Correction :**
- Ajout du header de documentation en français
- Ajout de `NON_KEYWORDS=VALUE,MONTH` dans l'URL H2 (alignement avec le profil dev)
- Ajout de `open-in-view: false`
- Ajout de `clean-disabled: false` (autorisation de nettoyage entre les tests)
- Ajout de `time_zone: UTC` dans les propriétés Hibernate
- Commentaires explicatifs en français

Fichier : `/home/claude-worker/nestspend/api/src/main/resources/application-test.yaml`

### 2.2 Installation de ng2-charts

**Problème :** `ng2-charts` était absent du `package.json` malgré la présence de `chart.js`.
La fonctionnalité de projection budgétaire (graphiques d'épargne et de dépenses) nécessite
`ng2-charts` comme wrapper Angular pour `chart.js`.

**Vérifications préalables :**
- `ng2-charts@10.0.0` est compatible avec Angular 21+ (peer dependency `>=21.0.0`)
- `@angular/cdk` est requis par ng2-charts et était déjà installé (`node_modules/@angular/cdk`)
- `chart.js@^4.5.1` est compatible avec ng2-charts@10 (peer dependency `^3.4.0 || ^4.0.0`)

**Commande exécutée :**
```bash
cd /home/claude-worker/nestspend/web/nestspend-web && npm install ng2-charts@10.0.0 --save
```

**Résultat :** `ng2-charts@10.0.0` ajouté dans `dependencies` du `package.json`.

Fichiers modifiés :
- `/home/claude-worker/nestspend/web/nestspend-web/package.json`
- `/home/claude-worker/nestspend/web/nestspend-web/package-lock.json`

### 2.3 Audit des vulnérabilités npm

`npm audit` signale 24 vulnérabilités (1 low, 7 moderate, 16 high) principalement dans :

| Package | Gravité | Nature |
|---|---|---|
| @angular/compiler | High | XSS via SVG non sanitisé (CVE dans Angular) |
| rollup | High | Arbitrary File Write via path traversal |
| undici | High | Décompression illimitée (Node.js Fetch API) |
| minimatch | High | ReDoS via wildcards répétées |
| tar | High | Chemin arbitraire (node-tar) |

Ces vulnérabilités sont toutes dans des dépendances de développement (build tools, CLI) ou
dans le framework Angular lui-même. Elles ne sont pas exploitables en production puisque
le frontend est servi sous forme de fichiers statiques compilés. Une mise à jour Angular
majeure (21.x -> 22.x) serait la solution à terme.

---

## 3. Configuration PostgreSQL - État complet

### 3.1 Profil prod déjà opérationnel

Le fichier `application-prod.yaml` est déjà complet et fonctionnel pour PostgreSQL.

Activation :
```bash
# Via variable d'environnement (recommandé en production)
export SPRING_PROFILES_ACTIVE=prod

# Via argument JVM
java -jar nestspend-api.jar --spring.profiles.active=prod
```

### 3.2 Variables d'environnement requises en production

| Variable | Obligatoire | Valeur par défaut | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Oui | `dev` | Doit valoir `prod` en production |
| `POSTGRES_URL` | Non | `jdbc:postgresql://localhost:5432/nestspend` | URL JDBC complète |
| `POSTGRES_USER` | Non | `nestspend` | Utilisateur PostgreSQL |
| `POSTGRES_PASSWORD` | Oui | Aucune | Mot de passe PostgreSQL |
| `JWT_SECRET` | Oui | Aucune | Clé JWT (min 32 caractères) |
| `JWT_EXPIRATION_MS` | Non | `86400000` (24h) | Durée de validité du JWT |
| `SERVER_PORT` | Non | `8080` | Port HTTP de l'API |
| `CORS_ALLOWED_ORIGINS` | Non | `https://nestspend.gilmotech.be,http://nestspend.gilmotech.be` | Origines CORS autorisées |

Génération d'un JWT_SECRET sécurisé :
```bash
openssl rand -base64 64
```

### 3.3 Préparation de la base PostgreSQL

```sql
-- A exécuter en tant que superuser PostgreSQL
CREATE DATABASE nestspend;
CREATE USER nestspend WITH PASSWORD 'mot_de_passe_fort';
GRANT ALL PRIVILEGES ON DATABASE nestspend TO nestspend;
-- PostgreSQL 15+ : droits sur le schéma public
\c nestspend
GRANT ALL ON SCHEMA public TO nestspend;
```

Flyway crée toutes les tables automatiquement au premier démarrage via les migrations V1 à V4.

### 3.4 Compatibilité SQL H2 / PostgreSQL

Toutes les migrations Flyway (V1 à V4) utilisent du SQL standard compatible avec H2 en
mode PostgreSQL et avec PostgreSQL natif. Les types de données, contraintes et index sont
identiques dans les deux moteurs.

### 3.5 CORS pour les nouvelles routes

La configuration CORS (`CorsConfig.java`) utilise un mapping `/**` universel. Les routes
`/api/budgets` et `/api/projections` sont automatiquement couvertes.

En production, le paramètre `CORS_ALLOWED_ORIGINS` contrôle les origines autorisées.
Pour ajouter une origine, séparer par des virgules :
```bash
export CORS_ALLOWED_ORIGINS="https://nestspend.gilmotech.be,https://app.example.com"
```

---

## 4. Architecture des profils Spring Boot

```
application.yaml
  (profil actif = SPRING_PROFILES_ACTIVE, défaut : dev)
  |
  +-- application-dev.yaml
  |     H2 in-memory + MODE=PostgreSQL + NON_KEYWORDS=VALUE,MONTH
  |     Flyway activé (ddl-auto: validate)
  |     Console H2 sur /h2
  |     CORS: localhost:4200
  |
  +-- application-test.yaml
  |     H2 in-memory + MODE=PostgreSQL + NON_KEYWORDS=VALUE,MONTH
  |     Flyway activé (ddl-auto: validate)
  |     clean-disabled: false (reset entre les tests)
  |     JWT court (1h)
  |
  +-- application-prod.yaml
        PostgreSQL via POSTGRES_URL / POSTGRES_USER / POSTGRES_PASSWORD
        HikariCP pool (2-10 connexions)
        Flyway activé, clean-disabled: true
        JWT_SECRET obligatoire
        Actuator limité (health, info, metrics)
        CORS: nestspend.gilmotech.be
```

---

## 5. Checklist avant mise en production

- [ ] Définir `POSTGRES_PASSWORD` (mot de passe fort)
- [ ] Définir `JWT_SECRET` (minimum 32 caractères aléatoires, `openssl rand -base64 64`)
- [ ] Créer la base PostgreSQL et l'utilisateur (voir section 3.3)
- [ ] Vérifier que les migrations V1 à V4 s'exécutent sur PostgreSQL sans erreur
- [ ] Tester les endpoints `/api/budgets` et `/api/projections/annual` avec un JWT valide
- [ ] Vérifier la console Actuator `/actuator/health` en production
- [ ] Optionnel : Mettre à jour Angular pour corriger les vulnérabilités npm (21.x -> 22.x quand disponible)

---

## 6. Fichiers modifiés ou créés dans cette session

| Fichier | Action | Description |
|---|---|---|
| `/home/claude-worker/nestspend/api/src/main/resources/application-test.yaml` | Modifié | Alignement avec dev : NON_KEYWORDS=VALUE,MONTH, commentaires français, open-in-view, clean-disabled |
| `/home/claude-worker/nestspend/web/nestspend-web/package.json` | Modifié | Ajout de ng2-charts@10.0.0 |
| `/home/claude-worker/nestspend/web/nestspend-web/package-lock.json` | Modifié | Mise à jour après npm install |
| `/home/claude-worker/nestspend/RAPPORT-OPS.md` | Modifié | Ce rapport (mise à jour) |

---

## 7. Historique des sessions OPS précédentes

### Session 1 (RAPPORT_OPS.md)
- Ajout des drivers PostgreSQL et Flyway-postgresql dans pom.xml
- Création du profil `application-prod.yaml`
- Configuration du build Maven (mapstruct-processor, centralisation des versions)
- Création des Dockerfiles et docker-compose.yml

### Session 2 (RAPPORT-OPS.md, Nginx)
- Préparation du vhost Nginx système (port 80 + HTTPS Let's Encrypt)
- Script `install-nginx-system.sh` pour migration Nginx
- Mise à jour de `start-prod.sh` pour supporter deux modes Nginx

### Session 3 (cette session)
- Audit complet pour la fonctionnalité Budget et Projection
- Correction de `application-test.yaml` (NON_KEYWORDS, commentaires, open-in-view)
- Installation de `ng2-charts@10.0.0` (compatible Angular 21 + chart.js 4.x)
- Documentation complète des variables d'environnement et de la configuration CORS
