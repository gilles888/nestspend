# 🏠 NestSpend – API

Backend de l’application **NestSpend**, une application de gestion des dépenses familiales.

Cette API est développée avec **Spring Boot**, utilise **H2 en développement**, **Flyway** pour les migrations de base de données, et expose un **contrat OpenAPI** consommé automatiquement par le frontend Angular.

---

## 🧱 Stack technique

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security (JWT à venir)
- H2 Database (DEV)
- Flyway
- OpenAPI / Swagger (springdoc)

---

## 📁 Structure du projet

api/
├── src/
│ ├── main/
│ │ ├── java/be/gilmotech/nestspend
│ │ │ ├── NestspendApiApplication.java
│ │ │ ├── controller/ # REST controllers
│ │ │ ├── domain/ # JPA entities
│ │ │ ├── repository/ # Spring Data repositories
│ │ │ └── service/ # Business logic
│ │ └── resources/
│ │ ├── db/migration/ # Flyway SQL migrations
│ │ ├── application.yml
│ │ ├── application-dev.yml
│ │ └── application-test.yml
│ └── test/
├── pom.xml
├── mvnw / mvnw.cmd
└── README.md


---

## 🚀 Démarrage en local (DEV)

### Prérequis
- Java 21
- Maven (ou Maven Wrapper)
- Aucun SGBD requis (H2 en mémoire)

### Lancer l’API
```bash
./mvnw spring-boot:run

Ou avec Maven installé :

mvn spring-boot:run

🔍 Vérifications rapides

    Health check
    👉 http://localhost:8080/api/health

    OpenAPI JSON
    👉 http://localhost:8080/v3/api-docs

    Swagger UI
    👉 http://localhost:8080/swagger-ui.html

    Console H2
    👉 http://localhost:8080/h2

    JDBC URL :

    jdbc:h2:mem:nestspend

    User : sa
    Password : (vide)

🗄️ Base de données & Flyway

    Base de données : H2 en mémoire

    Mode : MODE=PostgreSQL (anticipation prod)

    Les migrations sont dans :

    src/main/resources/db/migration/

Règles importantes

    Un seul set de migrations (pas de dossiers h2 / postgres)

    SQL compatible H2 + PostgreSQL

    UUID générés côté Java, pas en SQL

    Pas de fonctions SQL spécifiques à un SGBD

🔁 OpenAPI (contrat API)

L’API expose automatiquement son contrat OpenAPI.

👉 Le frontend Angular :

    récupère le contrat via curl

    génère automatiquement les services et modèles TypeScript

    aucun service Angular n’est codé à la main

URL utilisée en DEV :

http://localhost:8080/v3/api-docs

🧪 Profils Spring

    dev (par défaut)
    → H2, Flyway activé, console H2

    test
    → H2 séparé pour les tests automatisés

Le profil actif est défini dans application.yml :

spring:
  profiles:
    active: dev

🧭 Conventions du projet

    UUID partout

    Montants stockés en cents (long)

    Toutes les données métiers sont scopées par household

    Validation côté backend

    Pas de logique métier dans les controllers

    API REST sous /api/**

🛣️ Prochaines étapes prévues

    Finaliser le schéma Flyway V1

    Créer les entités JPA + repositories

    Authentification JWT (register / login)

    CRUD Categories / Accounts / Transactions

    Dashboard mensuel

👤 Projet

NestSpend – projet personnel
Objectif : application familiale simple, robuste et évolutive.