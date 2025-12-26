# NestSpend API — Feature 10: OpenAPI polish (pour génération Angular)

## Contexte
Le frontend génère ses services/models via OpenAPI.
Le contrat doit être stable, clair, bien typé.

## Objectif
Améliorer OpenAPI:
- tags
- schemas propres
- erreurs documentées
- sécurité bearer

## Tâches
1) Ajouter config springdoc:
    - title, version, contact
2) Ajouter SecurityScheme bearer-jwt (même si JWT déjà là)
3) Ajouter @Tag sur controllers
4) Documenter erreurs 400/401/404/409 sur endpoints clés

## DoD
- /v3/api-docs contient tags + schemas clairs
- Angular peut générer sans warnings majeurs
