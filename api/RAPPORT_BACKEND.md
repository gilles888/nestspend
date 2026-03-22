# Rapport Backend - NestSpend

Date : 2026-03-22

## 1. Analyse du code existant

### Architecture generale

Le backend NestSpend est une API REST Spring Boot 3.5.9 / Java 17 avec :
- Base de donnees H2 in-memory (mode PostgreSQL active pour la compatibilite)
- Migration de schema via Flyway (V1, V2, V3 existants)
- Securite JWT stateless via JJWT 0.12.6
- Documentation OpenAPI via springdoc-openapi 2.8.14
- Lombok et MapStruct pour la reduction de boilerplate

### Entites JPA existantes

| Entite | Table | Description |
|---|---|---|
| Household | households | Foyer regroupant plusieurs utilisateurs |
| User | users | Utilisateur avec role (ADMIN/MEMBER) |
| Account | accounts | Compte bancaire appartenant au foyer |
| Category | categories | Categorie de transaction du foyer |
| Transaction | transactions | Transaction financiere |
| ClassificationRule | classification_rules | Regle de categorisation automatique |
| FutureEvent | future_events | Evenement futur recurrent (abonnement, etc.) |

### Controllers et services existants

- `AuthController / AuthService` : inscription, connexion JWT
- `TransactionController / TransactionService` : CRUD + import en masse + deduplication
- `CategoryController / CategoryService` : CRUD + categories par defaut
- `AccountController / AccountService` : CRUD comptes bancaires
- `ClassificationController / ClassificationRuleController / ClassificationService` : regles et suggestions de categorisation automatique
- `DashboardController / DashboardService` : agregations mensuelles (revenus, depenses, breakdown par categorie)
- `FutureEventController / FutureEventService` : CRUD evenements futurs + projection 1-12 mois
- `UserController / UserService` : informations de l'utilisateur connecte
- `HealthController` : endpoint de sante

---

## 2. Bugs corriges

### 2.1 NullPointerException dans DashboardService (critique)

**Fichier** : `service/DashboardService.java` ligne 61

**Probleme** : Le calcul `Long netCents = totalIncomeCents - totalExpenseCents` pouvait lever une `NullPointerException`. Meme si la requete JPQL utilise `COALESCE`, le type `Long` Java (objet, pas primitif `long`) peut etre `null` si la JVM ne fait pas l'auto-unboxing correctement ou si la requete ne retourne aucune ligne.

**Correction** :
```java
long safeIncome = totalIncomeCents != null ? totalIncomeCents : 0L;
long safeExpenses = totalExpenseCents != null ? totalExpenseCents : 0L;
Long netCents = safeIncome - safeExpenses;
```

### 2.2 Calcul inexact des occurrences hebdomadaires (FutureEventService)

**Fichier** : `service/FutureEventService.java`

**Probleme** : La methode `countOccurrencesInMonth` utilisait une approximation fixe de 4 semaines par mois pour la periodicite `WEEKLY`, ce qui est incorrect (certains mois ont 4,3 semaines). Cela pouvait provoquer des projections financieres incorrectes.

**Correction** : Calcul precis base sur `ChronoUnit.DAYS` entre le debut et la fin effectifs de l'evenement dans le mois :
```java
long days = effectiveStart.until(effectiveEnd, ChronoUnit.DAYS) + 1;
yield (days + 6) / 7; // equivalent a ceil(days / 7)
```

### 2.3 Validation manquante sur la coherence des dates (FutureEvent)

**Fichier** : `service/FutureEventService.java`

**Probleme** : Il etait possible de creer un evenement futur avec `endDate <= startDate` sans erreur metier.

**Correction** : Ajout de `validerCoherenceDates()` appelee dans `createFutureEvent` et `updateFutureEvent`.

### 2.4 Messages d'erreur opaques sur les enums invalides (FutureEventService)

**Fichier** : `service/FutureEventService.java`

**Probleme** : `TransactionType.valueOf(request.type())` et `Periodicity.valueOf(request.periodicity())` levaient une `IllegalArgumentException` avec un message peu explicite.

**Correction** : Ajout de methodes `parseTransactionType()` et `parsePeriodicity()` avec messages clairs :
```
Type de transaction invalide : FOO. Valeurs acceptees : INCOME, EXPENSE
```

### 2.5 Suppressions sans verification des cles etrangeres

**Fichiers** : `service/CategoryService.java`, `service/AccountService.java`

**Probleme** : La suppression d'une categorie ou d'un compte avec des transactions associees levait une `DataIntegrityViolationException` (500 interne) sans message utilisateur comprehensible.

**Correction** :
- Ajout de `transactionRepository.countByCategoryId(id)` avant suppression d'une categorie
- Ajout de `transactionRepository.countByAccountId(id)` avant suppression d'un compte
- Leve une `ResourceConflictException` (409) avec un message metier explicite

### 2.6 Console H2 exposee en production

**Fichier** : `config/SecurityConfig.java`

**Probleme** : La route `/h2/**` etait permise sans condition de profil, exposant la console H2 meme si l'application etait mal configuree en production.

**Correction** : La route `/h2/**` n'est desormais ouverte que lorsque le profil `dev` est actif :
```java
boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
if (isDevProfile) {
    auth.requestMatchers("/h2/**", "/actuator/**").permitAll();
}
```

### 2.7 CORS non configurable

**Fichier** : `config/CorsConfig.java`

**Probleme** : L'origine CORS etait codee en dur (`http://localhost:4200`), impossible a changer sans recompilation.

**Correction** : La propriete `cors.allowed-origins` (liste separee par des virgules) est maintenant lisible depuis `application.yaml` / variables d'environnement. Ajout du `maxAge` de 3600s pour les reponses preflight.

### 2.8 Secret JWT non valide en production

**Fichier** : `config/JwtProperties.java`

**Probleme** : Le secret JWT pouvait etre vide en production si `JWT_SECRET` n'etait pas defini, conduisant a des tokens signables avec une cle vide.

**Correction** : Ajout de validation Bean Validation :
- `@NotBlank` : le secret ne peut pas etre vide
- `@Size(min = 32)` : minimum 32 caracteres pour HMAC-SHA256
- L'application refuse de demarrer si le secret est absent ou trop court

### 2.9 Reponses d'erreur 401/403 non JSON

**Fichier** : `config/SecurityConfig.java`

**Probleme** : Les erreurs d'authentification et d'autorisation renvoyaient une page HTML Spring par defaut au lieu d'un JSON structuré.

**Correction** : Ajout d'`authenticationEntryPoint` et `accessDeniedHandler` renvoyant du JSON.

### 2.10 Gestion des violations d'integrite non structuree

**Fichier** : `exception/GlobalExceptionHandler.java`

**Probleme** : Les `DataIntegrityViolationException` non interceptees renvoyaient une erreur 500 generique.

**Correction** : Ajout d'un handler specifique renvoyant un 409 avec message comprehensible. Ajout egalement d'un handler pour `MethodArgumentTypeMismatchException` (parametre de type invalide dans l'URL).

---

## 3. Nouvelles fonctionnalites

### 3.1 API de gestion de budgets mensuels

**Endpoint de base** : `/api/budgets`

**Entite** : `Budget` (table `budgets`, migration V4)
- Lie a un `Household` et une `Category`
- Cle metier unique : `(household_id, category_id, month)`
- Mois au format `YYYY-MM` pour un tri lexicographique correct
- Contrainte SQL `CHECK (amount_cents > 0)`

**DTOs** :
- `BudgetCreateRequest` : categoryId, month (YYYY-MM, validation regex), amountCents
- `BudgetUpdateRequest` : amountCents (la categorie et le mois sont immuables)
- `BudgetResponse` : inclut les depenses reelles, le montant restant et le taux de consommation
- `BudgetSummaryResponse` : aggregats totaux + liste des budgets du mois

**Endpoints REST** :

| Methode | URL | Description |
|---|---|---|
| GET | `/api/budgets?month=YYYY-MM` | Resume mensuel avec totaux et detail par categorie |
| GET | `/api/budgets/{id}` | Detail d'un budget avec depenses reelles |
| POST | `/api/budgets` | Creation d'un nouveau budget |
| PUT | `/api/budgets/{id}` | Mise a jour du montant |
| DELETE | `/api/budgets/{id}` | Suppression du budget |

**Calculs** : les depenses reelles sont calculees en temps reel via `TransactionRepository.sumAmountByHouseholdAndCategoryAndTypeAndDateRange()`. Le taux de consommation est arrondi a 2 decimales.

### 3.2 API de projection annuelle des depenses

**Endpoint** : `GET /api/projections/annual?year=YYYY`

**Service** : `ProjectionService`

Logique de projection par mois :
- **Mois passes** : donnees reelles des transactions uniquement (`isActual = true`)
- **Mois courant** : donnees reelles + evenements futurs planifies (`isActual = false`)
- **Mois futurs** : calcul base sur les `FutureEvent` actifs (`isActual = false`)

**DTOs** :
- `MonthlyExpenseProjection` : mois, revenus, depenses, solde net, flag isActual
- `AnnualProjectionResponse` : annee, totaux reels, totaux projetes, 12 points mensuels

**Validation** : l'annee doit etre comprise entre 2000 et `annee_courante + 10`.

**Note** : le `ProjectionService` partage la logique de calcul des occurrences avec `FutureEventService` (extraction de methode `countOccurrencesInMonth`). Cette duplication est acceptable pour eviter un couplage fort entre services, mais pourrait etre refactorisee dans un utilitaire partagé.

---

## 4. Nouveaux repositories

### TransactionRepository (methodes ajoutees)

- `countByCategoryId(UUID categoryId)` : pour la protection avant suppression de categorie
- `countByAccountId(UUID accountId)` : pour la protection avant suppression de compte
- `sumAmountByHouseholdAndCategoryAndTypeAndDateRange(...)` : somme par categorie pour le suivi budgetaire

### BudgetRepository (nouveau)

- `findByHouseholdIdAndMonth(UUID, String)` : tous les budgets d'un mois avec JOIN FETCH sur la categorie
- `findByHouseholdIdAndYear(UUID, String)` : tous les budgets d'une annee
- `existsByHouseholdIdAndCategoryIdAndMonth(...)` : verification d'unicite
- `findByHouseholdIdAndCategoryIdAndMonth(...)` : recherche par cle metier

---

## 5. Migration de base de donnees

### V4__budgets.sql (nouveau)

```sql
CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    category_id UUID NOT NULL,
    month VARCHAR(7) NOT NULL,
    amount_cents BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_budget_household FOREIGN KEY (household_id) REFERENCES households(id),
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT uq_budget_household_category_month UNIQUE (household_id, category_id, month),
    CONSTRAINT chk_budget_amount_positive CHECK (amount_cents > 0)
);
CREATE INDEX idx_budget_household_month ON budgets(household_id, month);
CREATE INDEX idx_budget_category ON budgets(category_id);
```

---

## 6. Preparation migration PostgreSQL

L'application est deja bien preparee pour PostgreSQL :
- H2 est lance en mode `MODE=PostgreSQL` avec `NON_KEYWORDS=VALUE`
- Flyway gere le schema (DDL Hibernate en `validate` uniquement)
- Les types `UUID`, `TIMESTAMP`, `BIGINT`, `DATE` sont compatibles PostgreSQL
- Les contraintes SQL (FK, UNIQUE, CHECK) sont compatibles

**Pour migrer vers PostgreSQL**, ajouter une dependance dans `pom.xml` :
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Et creer un `application-prod.yaml` :
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/nestspend}
    driver-class-name: org.postgresql.Driver
    username: ${DATABASE_USER:nestspend}
    password: ${DATABASE_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## 7. Points restants a ameliorer (hors perimetre de cette mission)

1. **Tests unitaires** : ajouter des tests pour `BudgetService`, `ProjectionService` et les nouvelles methodes de `FutureEventService`
2. **Pagination** : les endpoints `/api/transactions` et `/api/future-events` n'ont pas de pagination ; a implementer avec `Pageable` pour les gros volumes
3. **Recherche de budgets annuels** : un endpoint `GET /api/budgets?year=YYYY` pourrait etre utile pour une vue annuelle
4. **Taux de change** : l'application est 100% en centimes EUR, ce qui est correct mais limite a une seule devise
5. **Audit trail** : les modifications de categories et comptes ne sont pas tracees (pas de `updatedAt` sur `Category` et `Account`)
6. **Rate limiting** : pas de protection contre le brute-force sur `/api/auth/login`
7. **Refresh tokens** : les tokens JWT expirent sans possibilite de renouvellement sans re-connexion

---

## 8. Resume des commits effectues

| Commit | Description |
|---|---|
| `af46312` | fix: correction des bugs critiques backend (null, securite, CORS) |
| `394b3e8` | fix: correction du calcul des occurrences hebdomadaires et validation FutureEvents |
| `0cdc766` | feat: ajout de l'API de gestion de budgets mensuels par categorie |
| `2554b03` | feat: ajout de l'API de projection annuelle des depenses |
| `7969854` | fix: protection contre les suppressions avec references de cles etrangeres |
