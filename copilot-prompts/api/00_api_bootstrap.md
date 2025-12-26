# NestSpend API — Feature 00: Bootstrap + conventions

## Contexte
Projet Spring Boot (Java 21). DB en DEV: H2 (mode PostgreSQL). Migrations: Flyway.
OpenAPI via springdoc.
Structure packages déjà présente: controller/domain/repository/service.

## Objectif
Solidifier la base: config, health endpoint, conventions, erreurs standard, CORS.

## Tâches
1) Vérifier configs:
    - application.yml (active dev)
    - application-dev.yml (H2 + flyway + H2 console)
    - application-test.yml
2) Créer HealthController:
    - GET /api/health -> { "status": "ok" }
3) Ajouter gestion d’erreurs standard:
    - @RestControllerAdvice (validation errors, not found, conflict)
    - format JSON stable
4) Ajouter CORS (DEV) pour Angular:
    - autoriser http://localhost:4200
5) Vérifier OpenAPI:
    - /v3/api-docs
    - swagger-ui

## Livrables
- HealthController
- GlobalExceptionHandler
- CorsConfig

## DoD
- l’API démarre
- /api/health = 200
- erreurs validation = JSON propre