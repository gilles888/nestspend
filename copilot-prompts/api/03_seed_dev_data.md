# NestSpend API — Feature 03: Seed data DEV (simple)

## Contexte
Pour tester vite, on veut des données en DEV seulement.

## Objectif
Créer un DataSeeder actif uniquement en profil dev:
- 1 household
- 2 users (admin + user)
- catégories de base
- comptes de base
- quelques transactions

## Tâches
1) Créer un composant @Profile("dev") qui s’exécute au démarrage:
    - CommandLineRunner ou ApplicationRunner
2) Insérer si la DB est vide (idempotent)
3) Logguer les emails/passwords dev

## DoD
- En dev: données créées une seule fois
- En test/prod: rien
