
---

# 🔵 `WEB-10B_auto_learning_from_history.md`
*(V2 – évolution future, à lancer plus tard)*

```md
# WEB-10B — Apprentissage automatique depuis l’historique (V2)
## BACKEND-only · Extension de WEB-10A

---

## 🎯 Objectif

Améliorer automatiquement la qualité des suggestions de catégories
en analysant les **transactions déjà catégorisées**.

Cette feature :
- s’appuie sur WEB-10A
- ne modifie pas le frontend (ou très peu)
- améliore progressivement la précision sans action utilisateur

---

## ⚠️ Pré-requis

- WEB-10A implémenté et stable
- transactions réelles présentes
- catégories bien définies
- règles USER déjà utilisées

---

## Principe général (V2)

1) Analyser l’historique des transactions par household
2) Identifier des marchands stables
3) Générer ou ajuster automatiquement des règles AUTO
4) Améliorer le score de confiance

---

## Algorithme d’apprentissage (Backend)

### Étape 1 — Sélection des données
- transactions avec category_id non null
- groupées par merchant normalisé + household

### Étape 2 — Analyse de stabilité
Pour chaque merchant :
- compter le nombre de transactions par catégorie
- calculer le ratio dominant

### Étape 3 — Création / mise à jour de règles AUTO
Créer ou mettre à jour une règle si :
- ratio ≥ 85 %
- nombre de transactions ≥ seuil (ex: 5)

Attributs de la règle :
- source = AUTO
- match_type = CONTAINS
- field = MERCHANT
- confidence = ratio * 100
- priority ajustée automatiquement

---

## Gestion des règles AUTO

- visibles dans l’UI
- modifiables
- désactivables
- jamais silencieuses
- toujours explicables

---

## API À AJOUTER (OpenAPI)

### Déclenchement manuel
POST `/api/classification/learn`

- analyse l’historique
- génère / met à jour les règles AUTO
- retourne un résumé :
  - règles créées
  - règles mises à jour
  - règles ignorées

---

## Déclenchement automatique (optionnel)
- tâche planifiée backend (cron simple)
- uniquement en environnement DEV ou via flag

---

## Contraintes

- Aucun impact sur le frontend requis
- Aucun ML lourd
- Aucun service externe
- Code backend testable (tests unitaires sur apprentissage)

---

## Definition of Done (DoD)

- Règles AUTO générées depuis l’historique
- Amélioration mesurable du taux de catégorisation
- Aucun changement nécessaire côté frontend
- Les règles AUTO respectent les règles USER existantes
