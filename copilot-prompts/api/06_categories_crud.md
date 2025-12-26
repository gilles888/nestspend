# NestSpend API — Feature 06: CRUD Categories (household scoped)

## Endpoints
- GET /api/categories
- POST /api/categories
- PUT /api/categories/{id}
- DELETE /api/categories/{id}

## Règles
- Unicité (household_id, name)
- Si id appartient à un autre household -> 404
- Validation: name non vide

## Tâches
1) DTOs CategoryCreate/Update/Response
2) Service qui utilise CurrentUserService.getHouseholdId()
3) Repository queries filtrées household
4) Controller REST
5) Tests intégration:
    - create/list/update/delete
    - scoping household
    - conflict sur name unique

## DoD
- CRUD OK + scoping OK
