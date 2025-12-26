# NestSpend API — Feature 04: Auth JWT (register/login) + Spring Security

## Contexte
Auth email/password pour 2 users d'un même household.
JWT stateless. Password hashing BCrypt.

Endpoints publics:
- POST /api/auth/register
- POST /api/auth/login
- GET /api/health
- OpenAPI endpoints (v3/api-docs, swagger-ui) en dev

## Objectif
Auth fonctionnelle + filtres security.

## Tâches
1) Ajouter PasswordEncoder BCrypt
2) Implémenter JwtService:
    - generateToken(userId)
    - validateToken()
    - extractUserId()
    - config: secret + expiration
3) Implémenter JwtAuthFilter (OncePerRequestFilter)
4) SecurityConfig:
    - endpoints publics
    - reste authentifié
    - stateless session
5) AuthController + DTOs:
    - RegisterRequest(email, password, displayName, householdName)
    - LoginRequest(email, password)
    - AuthResponse(token, userId, householdId, displayName, role)
6) Lors du register:
    - créer Household
    - créer User ADMIN
7) Tests:
    - register OK
    - login OK
    - endpoint protégé => 401 sans token

## DoD
- JWT ok
- endpoints sécurisés
- tests passent
