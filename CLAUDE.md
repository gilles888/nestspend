# NestSpend

Application de gestion de dépenses personnelles.

## Stack technique
- Frontend : Angular
- Backend : Spring Boot / Java
- BDD actuelle : H2 (migration PostgreSQL prévue)

## Fonctionnalités existantes
- Import de fichiers bancaires
- Visualisation des dépenses

## Fonctionnalités à ajouter
- Projection des dépenses mensuelles et annuelles (tableur)
- Création et suivi de budgets

## Règles pour tous les agents
- Travailler de façon autonome sans demander confirmation
- Ne jamais casser une fonctionnalité existante
- Commenter le code en français
- Faire des commits clairs après chaque correction
- Écrire un rapport Markdown quand la mission est terminée

## Sub-Agent Routing Rules
**Parallel dispatch** :
- backend-agent : tout ce qui touche au Java/Spring Boot
- frontend-agent : tout ce qui touche à Angular
- ops-agent : configuration, dépendances, migration BDD
