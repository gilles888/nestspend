# Rapport d'analyse et de développement backend NestSpend

Date : 2026-03-22
Agent : backend-agent

---

## Contexte

Analyse complète du backend Spring Boot / Java du projet NestSpend. Le backend gère une application de
dépenses personnelles avec import de fichiers bancaires, catégorisation, projections et budgets.
La base de données est H2 en développement/test et PostgreSQL en production.

---

## 1. Architecture du projet

### Structure identifiée

```
api/src/main/java/be/gilmotech/nestspend/
  config/          CorsConfig, SecurityConfig, JwtProperties, OpenApiConfig, DevDataSeeder
  controller/      AccountController, AuthController, BudgetController, CategoryController,
                   ClassificationController, ClassificationRuleController, DashboardController,
                   FutureEventController, HealthController, ProjectionController,
                   TransactionController, UserController
  domain/entity/   Account, Budget, Category, ClassificationRule, FutureEvent, Household,
                   Transaction, User
  domain/enums/    AccountType, MatchType, Periodicity, RuleField, RuleSource,
                   TransactionType, UserRole
  domain/repository/  (tous les repositories JPA)
  dto/             (tous les DTOs par domaine)
  exception/       GlobalExceptionHandler + exceptions métier
  security/        JwtAuthFilter, JwtService, UserPrincipal, CurrentUserService
  service/         (tous les services + ProjectionHelper)
```

### Points forts identifiés

- Isolation foyer (household) appliquée systématiquement dans chaque service via `CurrentUserService`
- JWT stateless correctement configuré (BCrypt + HMAC-SHA256 avec clé minimum 32 chars)
- Gestion des erreurs centralisée et complète dans `GlobalExceptionHandler`
- Flyway pour les migrations DB (4 scripts V1 a V4)
- OpenAPI/Swagger documenté sur tous les endpoints
- `@Transactional(readOnly = true)` correctement appliqué sur les lectures
- Montants en centimes (Long) pour éviter les problèmes d'arrondi flottant
- Déduplication à l'import des transactions bancaires

---

## 2. Bugs corrigés

### Bug critique 1 : `month` est un mot réservé H2

**Fichier :** `api/src/main/resources/application-dev.yaml` et `application-test.yaml`

**Symptome :** Tous les tests échouaient avec l'erreur Flyway :
```
Migration of schema "PUBLIC" to version "4 - budgets" failed!
Message: Syntax error in SQL statement ... [*]month VARCHAR(7) NOT NULL
```

**Cause :** Dans H2 en mode PostgreSQL, `MONTH` est un mot-clé réservé utilisé par les fonctions de
date (EXTRACT, etc.). La colonne `month` dans la table `budgets` provoquait une erreur de syntaxe SQL.

**Correction :**
```yaml
# Avant
url: jdbc:h2:mem:nestspend;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE

# Apres
url: jdbc:h2:mem:nestspend;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE,MONTH
```

Cela desactive la reservation du mot `MONTH` dans H2 sans impacter PostgreSQL en production
(qui accepte `month` comme identifiant sans restriction).

### Bug 2 : JPQL LIKE avec parametre positif invalide

**Fichier :** `api/src/main/java/be/gilmotech/nestspend/domain/repository/BudgetRepository.java`

**Cause :** La syntaxe `LIKE :yearPrefix%` est invalide en JPQL. Le `%` doit etre inclus dans la
valeur du parametre ou utiliser `CONCAT`.

**Correction :**
```java
// Avant (invalide)
"WHERE b.household.id = :householdId AND b.month LIKE :yearPrefix% "

// Apres (compatible H2 et PostgreSQL)
"WHERE b.household.id = :householdId AND b.month LIKE CONCAT(:yearPrefix, '%') "
```

### Bug 3 : Calcul trimestriel incorrect sur annees croisees (ProjectionHelper)

**Fichier :** `api/src/main/java/be/gilmotech/nestspend/service/ProjectionHelper.java`

**Cause :** L'algorithme utilisait un calcul `(currentMonth - eventMonth + 12) % 12` modulo 12 pour
detecter les occurrences trimestrielles. Ce calcul est incorrect quand la periodicite
trimestrielle franchit une annee. Exemple : event debutant en septembre, occurrence attendue
en mars de l'annee suivante : `(3 - 9 + 12) % 12 = 6`, non divisible par 3 alors que
septembre + 6 mois = mars (correct).

**Correction :** Utilisation de `ChronoUnit.MONTHS` pour calculer la difference en mois absolus
entre le `YearMonth` de debut de l'evenement et le mois analyse :
```java
YearMonth startYearMonth = YearMonth.from(event.getStartDate());
long totalMonths = startYearMonth.until(month, ChronoUnit.MONTHS);
yield (totalMonths >= 0 && totalMonths % 3 == 0) ? 1 : 0;
```

### Bug 4 : Suppression de categorie sans verification des budgets lies

**Fichier :** `api/src/main/java/be/gilmotech/nestspend/service/CategoryService.java`

**Cause :** La methode `deleteCategory()` verifiait si des transactions referençaient la categorie,
mais pas si des budgets y etaient lies. Supprimer une categorie referencee par un budget
aurait provoque une violation de contrainte FK au niveau base de donnees.

**Correction :** Ajout de la verification sur `BudgetRepository.countByCategoryId()` avant la
suppression, avec un message d'erreur explicite guident l'utilisateur.

---

## 3. Nouvelles APIs developpees

### GET /api/projections/monthly

**Parametres :** `year` (optionnel, defaut = annee courante)

Retourne la projection mensuelle detaillee pour une annee donnee (12 entrees).
C'est un alias de l'endpoint `/api/projections/annual` expose sous une URL dediee
pour la clarte semantique de l'API cote consommateurs.

**Reponse :** `AnnualProjectionResponse` avec 12 `MonthlyExpenseProjection`

### GET /api/projections/savings

**Parametres :** `year` (optionnel, defaut = annee courante)

Calcule l'epargne possible mois par mois (revenus - depenses) et le cumul annuel.
Permet d'estimer la capacite d'epargne sur une annee entiere, en combinant
donnees reelles (mois passes) et projections (evenements futurs planifies).

**Reponse :** `AnnualSavingsResponse` avec 12 `MonthlySavingsProjection` incluant :
- `incomeCents` : revenus du mois
- `expenseCents` : depenses du mois
- `savingsCents` : epargne du mois (revenus - depenses)
- `cumulativeSavingsCents` : epargne cumulee depuis janvier
- `isActual` : true si donnees reelles, false si projetees

### GET /api/budgets (existant, complete)

**Parametres :** `month` (obligatoire, format YYYY-MM)

Retourne le resume des budgets du foyer pour le mois donne, avec les depenses
reelles de chaque categorie et le taux de consommation calcule.

### POST /api/budgets (existant, complete)

Crée un budget mensuel pour une categorie. Un seul budget par categorie et par mois.

### POST /api/budgets/copy (existant, complete)

Copie tous les budgets d'un mois source vers un mois cible. Les budgets deja
existants dans le mois cible sont ignores (pas de doublon).

---

## 4. Ameliorations apportees

### BudgetRepository : ajout countByCategoryId

Methode `countByCategoryId(UUID categoryId)` ajoutee pour permettre a
`CategoryService` de verifier les references avant suppression.

### Documentation Javadoc dupliquee supprimee

La methode `findByHouseholdIdAndYear` dans `BudgetRepository` avait un doublon
de Javadoc issu d'un merge. Nettoye.

### BudgetController : endpoint /copy correctement expose

L'endpoint `POST /api/budgets/copy` utilise des parametres query string
(`fromMonth`, `toMonth`) avec la documentation OpenAPI complete.

---

## 5. Analyse de securite

### Points conformes

- JWT avec HMAC-SHA256, cle minimum 32 caracteres valide en dev/test/prod
- BCryptPasswordEncoder pour les mots de passe
- Isolation foyer (household) appliquee sur CHAQUE acces aux donnees
- CSRF desactive intentionnellement (API stateless JWT, commentaire explicatif present)
- Console H2 exposee uniquement en profil `dev` via `SecurityConfig`
- Secrets produit injectes via variables d'environnement (JWT_SECRET, POSTGRES_PASSWORD)
- CORS configure via `cors.allowed-origins` (restrictif en production)
- Gestion generique des erreurs 500 sans fuite de details systeme

### Points a surveiller (non corriges, hors perimetre imminent)

- Le `GlobalExceptionHandler` renvoie le message brut des exceptions `IllegalArgumentException`.
  En production, s'assurer que ces messages ne contiennent pas d'informations sensibles.
- `application-dev.yaml` expose tous les endpoints Actuator (`include: "*"`). Ce fichier ne
  doit jamais etre deploye en production.
- `DevDataSeeder` cree des comptes de test avec des mots de passe en clair dans le code source.
  Ce composant est annote `@Profile("dev")` et n'est actif qu'en developpement.

---

## 6. Etat final

### Tests

- **130 tests** (tous les tests existants) : tous passent
- Aucune regression introduite

### Compilation

```
[INFO] BUILD SUCCESS
[INFO] Compiling 102 source files
```

### APIs disponibles

| Methode | Endpoint                         | Description                                    |
|---------|----------------------------------|------------------------------------------------|
| GET     | /api/projections/annual          | Projection annuelle (12 mois)                  |
| GET     | /api/projections/monthly         | Projection mensuelle (alias de annual)         |
| GET     | /api/projections/savings         | Epargne mois par mois + cumul                  |
| GET     | /api/budgets?month=YYYY-MM       | Resume des budgets du mois                     |
| GET     | /api/budgets/{id}                | Detail d'un budget                             |
| POST    | /api/budgets                     | Creer un budget                                |
| PUT     | /api/budgets/{id}                | Mettre a jour un budget                        |
| DELETE  | /api/budgets/{id}                | Supprimer un budget                            |
| POST    | /api/budgets/copy                | Copier les budgets d'un mois vers un autre     |

---

## 7. Fichiers modifies

| Fichier | Type de modification |
|---------|----------------------|
| `api/src/main/resources/application-dev.yaml` | Correction bug H2 NON_KEYWORDS |
| `api/src/main/resources/application-test.yaml` | Correction bug H2 NON_KEYWORDS |
| `api/src/main/java/.../domain/repository/BudgetRepository.java` | Fix LIKE CONCAT + ajout countByCategoryId |
| `api/src/main/java/.../service/CategoryService.java` | Ajout verification budgets a la suppression |
| `api/src/main/java/.../service/ProjectionHelper.java` | Correction calcul trimestriel |
| `api/src/main/java/.../controller/ProjectionController.java` | Ajout endpoints /monthly et /savings |
| `api/src/main/java/.../dto/projection/AnnualSavingsResponse.java` | Nouveau DTO (existait comme untracked) |
| `api/src/main/java/.../dto/projection/MonthlySavingsProjection.java` | Nouveau DTO (existait comme untracked) |
| `api/src/main/java/.../dto/budget/BudgetCopyResponse.java` | Nouveau DTO (existait comme untracked) |
| `api/src/main/java/.../service/ProjectionHelper.java` | Nouveau composant (existait comme untracked) |
| `api/src/main/java/.../service/BudgetService.java` | Confirme complet |
| `api/src/main/java/.../controller/BudgetController.java` | Confirme complet |
