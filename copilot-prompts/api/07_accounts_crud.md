# NestSpend API — Feature 07: CRUD Accounts (household scoped)

## Endpoints
- GET /api/accounts
- POST /api/accounts
- PUT /api/accounts/{id}
- DELETE /api/accounts/{id}

## Règles
- Unicité (household_id, name)
- type = CASH/BANK/CARD
- scoping household obligatoire

## Tâches
Comme categories: DTOs + service + repo + controller + tests.

## DoD
- CRUD OK + scoping OK
