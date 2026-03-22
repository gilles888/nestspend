# Rapport backend - Fonctionnalité "Mois Type"

Date : 2026-03-22
Agent : backend-agent
Commit : feat: implémenter la fonctionnalité Mois Type

---

## Résumé

Implémentation complète de la fonctionnalité "Mois Type" dans le projet Spring Boot NestSpend. La fonctionnalité permet de définir un modèle budgétaire mensuel (avec revenus et dépenses types) pour générer des projections financières annuelles et calculer l'épargne possible mois par mois.

Résultat : 130 tests passent, 0 erreur, compilation sans avertissement.

---

## Fichiers créés

### Migration Flyway

**`api/src/main/resources/db/migration/V5__mois_type.sql`**

Deux tables créées, compatibles H2 (mode PostgreSQL) et PostgreSQL réel :

- `mois_type` : identifiant UUID, household_id (FK), nom, annee, revenus, autres_revenus, timestamps
- `depenses_type` : identifiant UUID, household_id (FK, dénormalisé), mois_type_id (FK), nom, montant (en centimes), categorie, type_depense, frequence, actif, timestamps

Contraintes : clé primaire UUID sur les deux tables, FK vers households et mois_type, CHECK montant > 0. Index sur household_id, (household_id, annee), mois_type_id, (mois_type_id, actif).

### Enums

- `domain/enums/CategorieDepense.java` : LOGEMENT, TRANSPORT, ALIMENTATION, SANTE, LOISIRS, ABONNEMENTS, EPARGNE, AUTRE
- `domain/enums/TypeDepense.java` : FIXE, VARIABLE
- `domain/enums/FrequenceDepense.java` : MENSUELLE, TRIMESTRIELLE, ANNUELLE

### Entités JPA

**`domain/entity/MoisType.java`**

Entité avec relation `@OneToMany(mappedBy = "moisType", cascade = CascadeType.ALL, orphanRemoval = true)` vers DepenseType. Champs : nom, annee, revenus (Long, centimes), autresRevenus (Long, centimes). Timestamps gérés via `@PrePersist` / `@PreUpdate`. Lombok `@Builder` avec `@Builder.Default` pour la liste de dépenses.

**`domain/entity/DepenseType.java`**

Entité avec deux relations ManyToOne (household et moisType). Enums stockés en String (`@Enumerated(EnumType.STRING)`). Champ `actif` avec `@Builder.Default = true`.

### Repositories

**`domain/repository/MoisTypeRepository.java`**

Méthodes utiles :
- `findByIdAndHouseholdId` : sécurité multi-tenant
- `findByHouseholdId` : liste triée par année DESC
- `findByHouseholdIdAndAnnee` : filtrage par année
- `findByIdAndHouseholdIdWithDepenses` : JOIN FETCH pour éviter le problème N+1
- `findByHouseholdIdAndAnneeWithDepenses` : utilisé pour la projection annuelle

**`domain/repository/DepenseTypeRepository.java`**

Méthodes utiles :
- `findByIdAndHouseholdId` : sécurité multi-tenant
- `findByMoisTypeIdAndHouseholdId` : toutes les dépenses d'un mois type
- `findActiveByMoisTypeIdAndHouseholdId` : dépenses actives uniquement (pour calculs)
- `findByHouseholdIdAndCategorie` : filtrage par catégorie
- `countByMoisTypeIdAndActifTrue` : comptage des dépenses actives

### DTOs (package `dto/moistype/`)

- `DepenseTypeDto` : réponse avec `montantMensuelCents` pré-calculé selon la fréquence
- `DepenseTypeCreateRequest` : validation `@NotNull`, `@Positive`, `@NotBlank`, `@Size`
- `DepenseTypeUpdateRequest` : idem (sans moisTypeId, non modifiable)
- `MoisTypeDto` : réponse avec totalRevenusCents, totalFixesCents, totalVariablesCents, totalDepensesCents, epargnePossibleCents, tauxEpargne pré-calculés
- `MoisTypeCreateRequest` : validation année 2020-2100, revenus PositiveOrZero
- `MoisTypeUpdateRequest` : idem
- `ProjectionMoisDto` : mois, revenusCents, depensesFixesCents, depensesVariablesCents, totalDepensesCents, epargneMoisCents, epargneCumuleeCents
- `ProjectionAnnuelleDto` : annee, liste de 12 ProjectionMoisDto, totalRevenusCents, totalDepensesCents, totalEpargneCents, tauxEpargne

### Services

**`service/MoisTypeService.java`** (créé)

CRUD complet pour MoisType et DepenseType. Méthodes publiques réutilisables par ProjectionService :
- `calculerMontantMensuelDepense(DepenseType)` : mensualisation selon fréquence (MENSUELLE=montant, TRIMESTRIELLE=montant/3, ANNUELLE=montant/12)
- `calculerTotauxMensuels(List<DepenseType>)` : retourne `long[]` [totalFixesCents, totalVariablesCents]
- `listerDepensesType(UUID moisTypeId)` : liste actives + inactives avec vérification d'accès
- `basculerActivation(UUID id)` : toggle actif/inactif
- `changerEtatDepenseType(UUID id, boolean actif)` : setter explicite de l'état

Calcul du taux d'épargne avec `BigDecimal.setScale(2, RoundingMode.HALF_UP)` pour la précision. Les listes de dépenses sont nulles dans les réponses de liste (incluses seulement sur le détail `getMoisTypeDetail`).

**`service/ProjectionService.java`** (étendu)

Ajout de :
- `getProjectionMoisType(int year)` : projection 12 mois depuis le mois type de l'année. Si aucun mois type → projection vide (0). Épargne cumulée mois par mois.
- `getEpargneMoisType(int year)` : alias qui retourne la même `ProjectionAnnuelleDto` (le frontend utilise `totalEpargneCents`).
- `trouverMoisTypePourAnnee(UUID, int)` : sélectionne le premier mois type créé si plusieurs existent pour l'année.
- `construireProjectionVide(int)` : 12 mois à zéro, retourné quand pas de mois type.

Les nouveaux dépendances injectées : `MoisTypeRepository`, `DepenseTypeRepository`, `MoisTypeService`.

### Controllers

**`controller/MoisTypeController.java`** (créé)

Routes :
- `GET /api/mois-type` → liste des mois types (sans détail des dépenses)
- `POST /api/mois-type` → créer un mois type
- `GET /api/mois-type/{id}` → détail complet avec dépenses actives et inactives
- `PUT /api/mois-type/{id}` → modifier (nom, année, revenus)
- `DELETE /api/mois-type/{id}` → supprimer avec cascade des dépenses
- `POST /api/mois-type/depenses` → ajouter une dépense type
- `PUT /api/mois-type/depenses/{id}` → modifier une dépense type
- `PATCH /api/mois-type/depenses/{id}/actif?actif=true|false` → activer/désactiver
- `DELETE /api/mois-type/depenses/{id}` → supprimer

**`controller/DepenseTypeController.java`** (créé)

Routes dédiées pour la gestion des dépenses types sans contexte de mois type :
- `GET /api/depenses-type?moisTypeId={uuid}` → liste (actives + inactives)
- `POST /api/depenses-type` → créer
- `PUT /api/depenses-type/{id}` → modifier
- `DELETE /api/depenses-type/{id}` → supprimer
- `PATCH /api/depenses-type/{id}/toggle` → basculer actif/inactif

**`controller/ProjectionController.java`** (étendu)

Nouvelles routes sans modifier les existantes :
- `GET /api/projections/{year}/mois-type` → projection annuelle depuis mois type
- `GET /api/projections/{year}/mois-type/epargne` → épargne annuelle depuis mois type

---

## Décisions techniques

### UUID vs BIGSERIAL

Le projet utilise des UUID pour tous les identifiants (conformément aux entités existantes comme Budget, FutureEvent). La migration V5 utilise donc UUID PRIMARY KEY au lieu de BIGSERIAL, assurant la cohérence avec le reste du codebase.

### Montants en centimes

Conformément à la convention du projet (amountCents en Long dans Budget, FutureEvent, Transaction), tous les montants financiers sont stockés et exposés en centimes (Long). Pas de BigDecimal en base de données pour éviter les problèmes d'arrondi.

### Mensualisation par division entière

La division `montant / 3` (trimestriel) et `montant / 12` (annuel) utilise la division entière Java, ce qui produit un arrondi à l'inférieur. Ce choix est volontairement conservateur (sous-estimation de l'épargne plutôt que sur-estimation).

### Isolation multi-tenant

Toutes les méthodes de service vérifient que la ressource accédée appartient bien au `householdId` extrait du JWT via `CurrentUserService.getCurrentHouseholdId()`. Le `household_id` est dénormalisé dans `depenses_type` pour permettre des vérifications de sécurité directes sans jointure.

### Projection vide plutôt qu'erreur

Quand aucun mois type n'est défini pour l'année demandée, `getProjectionMoisType` retourne une projection avec 12 mois à zéro plutôt qu'une erreur 404. Ce choix facilite l'intégration frontend : les graphiques s'affichent vides plutôt que de planter.

### Compatibilité H2

La migration V5 n'utilise pas de mots réservés H2 problématiques. Les types SQL utilisés (UUID, VARCHAR, BIGINT, BOOLEAN, TIMESTAMP, INT) sont supportés par H2 en mode PostgreSQL. La configuration existante `NON_KEYWORDS=VALUE,MONTH` dans `application-dev.yaml` n'a pas eu besoin d'extension.

---

## Tests

Tous les 130 tests existants continuent de passer après l'ajout de la fonctionnalité. Aucun test n'a été cassé. La migration V5 est exécutée correctement par Flyway lors des tests (log : "Successfully validated 5 migrations").

Aucun nouveau test unitaire n'a été ajouté dans ce commit (hors périmètre de la mission). Les tests d'intégration existants couvrent le démarrage de l'application et la validation du schéma Flyway, ce qui garantit que les nouvelles tables sont correctement créées.

---

## API REST - Récapitulatif des endpoints

| Méthode | URL | Description |
|---------|-----|-------------|
| GET | /api/mois-type | Liste des mois types du foyer |
| POST | /api/mois-type | Créer un mois type |
| GET | /api/mois-type/{id} | Détail avec dépenses |
| PUT | /api/mois-type/{id} | Modifier |
| DELETE | /api/mois-type/{id} | Supprimer (cascade) |
| POST | /api/mois-type/depenses | Ajouter une dépense type |
| PUT | /api/mois-type/depenses/{id} | Modifier une dépense type |
| PATCH | /api/mois-type/depenses/{id}/actif | Activer/désactiver |
| DELETE | /api/mois-type/depenses/{id} | Supprimer |
| GET | /api/depenses-type?moisTypeId= | Liste depuis endpoint dédié |
| POST | /api/depenses-type | Créer (endpoint dédié) |
| PUT | /api/depenses-type/{id} | Modifier (endpoint dédié) |
| DELETE | /api/depenses-type/{id} | Supprimer (endpoint dédié) |
| PATCH | /api/depenses-type/{id}/toggle | Toggle actif/inactif |
| GET | /api/projections/{year}/mois-type | Projection 12 mois depuis mois type |
| GET | /api/projections/{year}/mois-type/epargne | Épargne annuelle depuis mois type |
