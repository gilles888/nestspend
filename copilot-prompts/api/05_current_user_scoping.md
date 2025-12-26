# NestSpend API — Feature 05: CurrentUserService + scoping household

## Contexte
Toutes les données doivent être scoppées par household_id du user.

## Objectif
Créer un service utilitaire pour récupérer userId + householdId depuis le contexte security
et faciliter le filtrage dans les services CRUD.

## Tâches
1) Créer UserPrincipal (userId) et l’injecter dans Authentication
2) Créer CurrentUserService:
    - UUID getUserId()
    - UUID getHouseholdId()
3) Ajouter endpoint:
    - GET /api/me -> user + householdId + role + displayName
4) Tests:
    - login puis /api/me renvoie householdId correct

## DoD
- scoping prêt pour CRUD
