# 🏠 NestSpend – Web

Frontend de l’application **NestSpend**, une application de gestion des dépenses familiales.  
Cette application Angular consomme l’API NestSpend via un **client TypeScript généré automatiquement depuis OpenAPI**.

---

## 🎯 Objectifs du frontend

- Interface claire et rapide pour un usage quotidien
- Gestion des **dépenses et revenus**
- Vue dashboard mensuelle
- UX moderne et lisible (mobile + desktop)
- Aucun code API écrit à la main (OpenAPI first)

---

## 🧱 Stack technique

- **Angular** (standalone + routing)
- **TypeScript**
- **Tailwind CSS** (layout, responsive, spacing)
- **PrimeNG** + **PrimeIcons** (composants UI)
- **OpenAPI** + `ng-openapi-gen`
- **curl** pour synchroniser le contrat API

---

## 📁 Structure du projet

nestspend-web/
├── src/
│ ├── app/
│ │ ├── core/
│ │ │ ├── api/
│ │ │ │ ├── generated/ # Client OpenAPI généré (NE PAS MODIFIER)
│ │ │ │ └── api-config.ts
│ │ │ ├── auth/ # Auth, interceptor, guard
│ │ │ └── layout/ # AppShell (sidebar / topbar)
│ │ ├── features/
│ │ │ ├── dashboard/
│ │ │ ├── transactions/
│ │ │ ├── categories/
│ │ │ └── accounts/
│ │ ├── shared/ # composants et utilitaires partagés
│ │ ├── app.routes.ts
│ │ └── app.config.ts
│ ├── environments/
│ ├── styles/
│ │ ├── _variables.scss # (vide au départ)
│ │ └── _primeng-overrides.scss # (vide au départ)
│ └── styles.scss
├── openapi/
│ └── openapi.json # Contrat OpenAPI (source de vérité)
├── ng-openapi-gen.json
├── tailwind.config.js
├── package.json
└── README.md


---

## 🚀 Démarrage en local

### Prérequis
- Node.js **18+**
- npm
- curl
- L’API NestSpend en cours d’exécution

### Installation
```bash
npm install

Lancer l’application

npm start

👉 Application disponible sur :
http://localhost:4200
🔁 OpenAPI → Client Angular (IMPORTANT)

⚠️ Aucun service Angular n’est codé à la main.
Tous les services et modèles sont générés depuis le contrat OpenAPI exposé par l’API Spring Boot.
🔄 Workflow OpenAPI standard
1️⃣ Démarrer l’API

cd nestspend/api
./mvnw spring-boot:run

2️⃣ Synchroniser OpenAPI + générer le client

cd nestspend/web/nestspend-web
npm run api:update

Cela effectue :

    curl http://localhost:8080/v3/api-docs

    Écriture dans openapi/openapi.json

    Génération automatique dans :

    src/app/core/api/generated/

📜 Scripts OpenAPI disponibles

npm run api:sync     # Télécharge openapi.json via curl
npm run api:gen      # Génère le client Angular
npm run api:update   # sync + gen (commande recommandée)

Changer l’URL de l’API :

OPENAPI_URL="http://localhost:8080/v3/api-docs" npm run api:update

❗ Règles importantes OpenAPI

    ❌ Ne jamais modifier à la main :

    src/app/core/api/generated/**

    ✅ Toujours régénérer via npm run api:update

    ✅ openapi/openapi.json est la source de vérité

    Le frontend dépend uniquement de ce contrat

🎨 UI / UX
Tailwind CSS

    Utilisé pour le layout, le responsive et la typographie

    Palette personnalisée définie dans tailwind.config.js

PrimeNG

    Utilisé pour les composants (Button, Table, Dialog, Calendar, etc.)

    Thème utilisé : Aura

    Overrides légers possibles dans _primeng-overrides.scss

🔐 Authentification

    Authentification JWT via l’API

    Token stocké côté frontend

    HttpInterceptor ajoute automatiquement :

    Authorization: Bearer <token>

    AuthGuard protège les routes privées

🧭 Conventions frontend

    OpenAPI first (pas de DTOs manuels)

    Pas de logique métier complexe côté frontend

    Pages = orchestration + UI

    Layout réutilisable (AppShell)

    Code lisible, simple, maintenable

🛣️ Roadmap frontend
V1

    Auth (login / register)

    Dashboard mensuel

    Transactions (CRUD)

    Categories / Accounts

V2

    Récurrences

    Import CSV bancaire

    Dark mode

    UX mobile améliorée

👤 Projet

NestSpend – projet personnel
Frontend Angular moderne, propre et orienté long terme.
