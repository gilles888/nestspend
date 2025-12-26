# NestSpend API — Feature 01: Flyway V1 schema (H2 + Postgres compatible)

## Contexte
On veut un modèle "famille" :
- households
- users
- categories
- accounts
- transactions

IMPORTANT: 1 seul set de migrations (pas de dossier h2/postgres).
SQL compatible H2 + PostgreSQL.
Pas de fonctions spécifiques (ex: gen_random_uuid).

## Objectif
Créer V1__init.sql complet + indexes + contraintes.

## Tâches
1) Créer/compléter src/main/resources/db/migration/V1__init.sql
2) Tables:
    - households(id UUID PK, name, created_at)
    - users(id UUID PK, household_id FK, email UNIQUE, password_hash, display_name, role, created_at)
    - categories(id UUID PK, household_id FK, name, color, icon, created_at, UNIQUE(household_id,name))
    - accounts(id UUID PK, household_id FK, name, type, created_at, UNIQUE(household_id,name))
    - transactions(id UUID PK, household_id FK, created_by FK, tx_date DATE, type, amount_cents BIGINT,
      category_id FK, account_id FK, merchant, note TEXT, created_at, updated_at)
3) Indexes:
    - transactions(household_id, tx_date)
    - transactions(category_id)
    - transactions(account_id)

## DoD
- Flyway s’exécute au démarrage sans erreur
- tables visibles dans console H2
