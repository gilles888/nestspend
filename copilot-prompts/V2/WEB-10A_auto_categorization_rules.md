# WEB-10A — Auto-catégorisation des transactions (V1)
## BACKEND-first · OpenAPI-driven · Règles explicites

---

## 🎯 Objectif

Mettre en place un **moteur d’auto-catégorisation basé sur des règles**, entièrement côté BACKEND.

Cette version doit :
- fonctionner dès le premier import
- être explicable et contrôlable
- réduire fortement l’encodage manuel
- servir de base solide pour une évolution future (WEB-10B)

⚠️ Cette feature implémente UNIQUEMENT la V1.  
Aucune logique d’apprentissage automatique ne doit être codée ici.

---

## 🧩 Architecture

### Backend (Spring Boot)
- moteur de règles
- scoring
- stockage des règles
- endpoints exposés via OpenAPI

### Frontend (Angular)
- utilise UNIQUEMENT les services générés via `ng-openapi-gen`
- affiche suggestions + niveaux de confiance
- permet acceptation / correction / création de règle

❌ aucune logique de matching côté frontend

---

## Objectifs fonctionnels (V1)

1) Suggérer une catégorie automatique :
    - lors d’un import bancaire (WEB-09)
    - lors de la création ou édition d’une transaction

2) Fournir un niveau de confiance :
    - score numérique (0–100)
    - label HIGH / MEDIUM / LOW

3) Permettre la création de règles utilisateur à partir des corrections

4) Partager les règles entre les membres d’un même household

---

## Modèle de données (Backend)

### Table `classification_rules` (Flyway)

- id (UUID)
- household_id (UUID)
- enabled (boolean)
- priority (int)
- field (ENUM) :
    - MERCHANT
    - COMMUNICATION
    - IBAN
- match_type (ENUM) :
    - CONTAINS
    - STARTS_WITH
    - REGEX
- pattern (varchar)
- category_id (UUID)
- confidence (int 0–100)
- source (ENUM) :
    - USER
    - AUTO
- created_at
- updated_at

---

## Algorithme de suggestion (V1)

Pour chaque transaction candidate :

### 1️⃣ Normalisation
- uppercase
- trim
- suppression des accents
- suppression des caractères spéciaux non pertinents

### 2️⃣ Sélection des règles
- récupérer les règles du household
- filtrer `enabled = true`
- trier par `priority DESC`

### 3️⃣ Matching & scoring
- REGEX → score de base 80
- STARTS_WITH → score de base 70
- CONTAINS → score de base 60
- bonus :
    - match exact
    - longueur du pattern

### 4️⃣ Résultat
Retourner :
- categoryId
- confidenceScore
- confidenceLabel :
    - HIGH ≥ 80
    - MEDIUM ≥ 60
    - LOW < 60
- ruleId (optionnel)

### 5️⃣ Fallback
Si aucune règle ne correspond :
- categoryId = null ou catégorie "Uncategorized"

---

## Apprentissage utilisateur (V1.1)

Quand l’utilisateur modifie une transaction :
- comparer catégorie suggérée vs catégorie finale
- si différente :
    - proposer la création d’une règle
    - si acceptée :
        - créer une règle USER
        - field = MERCHANT (par défaut)
        - match_type = CONTAINS
        - priority = 100
        - confidence = 80

---

## 🔌 API À EXPOSER (OpenAPI obligatoire)

### Suggestion de catégories
POST `/api/classification/suggest`

Request :
```json
{
  "transactions": [
    {
      "merchant": "DELHAIZE",
      "communication": "CB 12345",
      "iban": "BE92...",
      "amount": 12345,
      "date": "2026-01-01"
    }
  ]
}
Response :

{
  "suggestions": [
    {
      "categoryId": "uuid",
      "confidence": 85,
      "confidenceLabel": "HIGH",
      "ruleId": "uuid"
    }
  ]
}

CRUD règles

GET /api/classification-rules

POST /api/classification-rules

PUT /api/classification-rules/{id}

DELETE /api/classification-rules/{id}

Tous les endpoints doivent être visibles dans OpenAPI 3.

Contraintes techniques

Toute la logique est backend

Frontend = affichage + orchestration

OpenAPI = source de vérité

Code testable, lisible, maintenable

Definition of Done (DoD)

Suggestions fonctionnelles à l’import

CRUD règles backend opérationnel

Frontend branché uniquement via clients OpenAPI générés

Règles partagées entre users du même household