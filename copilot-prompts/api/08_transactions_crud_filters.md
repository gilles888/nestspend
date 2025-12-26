# NestSpend API — Feature 08: Transactions CRUD + filtres

## Endpoints
- GET /api/transactions?from=YYYY-MM-DD&to=YYYY-MM-DD&categoryId=&accountId=&type=
- POST /api/transactions
- PUT /api/transactions/{id}
- DELETE /api/transactions/{id}

## Règles
- scoping household obligatoire
- created_by = user courant à la création
- amount_cents > 0
- tx_date obligatoire
- category/account doivent appartenir au même household
- Si id autre household -> 404

## Tâches
1) DTOs:
    - TransactionCreateRequest
    - TransactionUpdateRequest
    - TransactionResponse
2) Service:
    - validate ownership category/account
    - set createdBy + createdAt/updatedAt
3) Repository:
    - query filtrée household + filtres params (JPQL ou Specification)
4) Tests:
    - create puis list période
    - filtres category/account/type
    - scoping household

## DoD
- CRUD OK + filtres OK
