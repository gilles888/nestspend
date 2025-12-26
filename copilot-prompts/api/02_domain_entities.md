# NestSpend API — Feature 02: Entités JPA + enums + mappings

## Contexte
DB créée via Flyway.
On veut des entités JPA propres et portables.

## Objectif
Créer les entities + enums correspondants au schéma V1.

## Tâches
1) Créer enums:
    - AccountType: CASH, BANK, CARD
    - TransactionType: EXPENSE, INCOME
    - UserRole: USER, ADMIN
2) Créer entities dans package domain:
    - Household
    - User
    - Category
    - Account
    - Transaction
3) Règles:
    - UUID généré côté Java: @Id @GeneratedValue
    - createdAt/updatedAt gérés côté Java (Instant)
    - relations:
        - User -> Household (ManyToOne)
        - Category/Account/Transaction -> Household (ManyToOne)
        - Transaction -> Category/Account (ManyToOne)
        - Transaction.createdBy -> User (ManyToOne, nullable)
4) Ajouter @Table avec constraints uniques (si utile côté JPA)
5) Ajouter repositories Spring Data pour chaque entité.

## DoD
- application démarre avec ddl-auto=validate
- aucun mismatch entre entities et Flyway
