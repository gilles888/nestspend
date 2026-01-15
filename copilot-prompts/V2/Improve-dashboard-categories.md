# Ajout de fonctionnalités pour NestSpend

Ce document décrit deux nouvelles fonctionnalités à implémenter dans le projet **NestSpend**. Ces fonctionnalités visent à améliorer la vue du tableau de bord ainsi que la gestion des finances utilisateur.

## 1. Amélioration du tableau de bord principal : Totaux par catégorie
### Contexte
Actuellement, le tableau de bord principal permet uniquement de visualiser les graphiques des transactions **par mois**. Nous souhaitons étendre cette fonctionnalité en offrant une vue **totale par catégorie**.

### Objectifs
- Ajouter la possibilité de visualiser les **totaux par catégorie** (exemple : alimentation, logement, loisirs) via des graphiques additionnels.
- Conserver les graphiques mensuels existants et permettre de basculer facilement entre les deux vues ("Vue par mois" et "Vue par catégorie").

### Spécifications techniques
#### Front-End
- **Technologies utilisées : TypeScript, HTML, PrimeNG, Tailwind CSS**
    - Ajouter un nouveau composant graphique utilisant **PrimeNG** (`p-chart`).
    - Styliser l’interface avec **Tailwind CSS** pour garantir une expérience utilisateur moderne et responsive.
    - Ajouter un bouton ou un filtre interactif permettant de basculer entre les graphiques (vue mensuelle ou catégorielle).

#### Back-End
- **Technologies utilisées : Java**
    - Étendre ou ajouter un endpoint pour retourner les totaux des transactions par catégorie :
        - Requête regroupant les montants des transactions via leurs catégories respectives.
        - Données nécessaires : `nom_catégorie`, `montant_total`.

#### Tests
- Tests unitaires pour valider que le graphique affiche correctement les données.
- Tests d'intégration pour s'assurer que le back-end répond correctement.

### UX/UI
- L'expérience utilisateur doit être fluide et intuitive :
    - L’intégration graphique doit maintenir la cohérence visuelle de l'application.
    - Ajouter des animations légères pour les transitions entre différentes vues.

---

## 2. Module de projections budgétaires (Entrées, Dépenses et Événements futurs)
### Contexte
Les utilisateurs ont besoin d’un outil de prévision pour leur budget, qui prend en compte les entrées et dépenses actuelles, mais aussi des événements futurs tels que des abonnements ou paiements réguliers.

### Objectifs
- Permettre aux utilisateurs de visualiser leurs soldes projetés sur une période donnée (mois, trimestre, année).
- Ajouter un formulaire pour gérer des **événements futurs** (comme les abonnements, assurances, ou revenus).

### Spécifications techniques
#### Front-End
- **Technologies utilisées : TypeScript, HTML, PrimeNG, Tailwind CSS**
    - Ajouter un graphique de type courbe (PrimeNG `p-chart`) affichant les projections.
    - Inclure un tableau pour répertorier les événements futurs avec des options d’ajout/suppression/modification (exemple : abonnements Netflix, ChatGPT, etc.).
    - Assurer que l’ensemble du module soit responsive (compatible desktop et mobile).

#### Back-End
- **Technologies utilisées : Java**
    1. **Gestion des projections** :
        - Implémenter un endpoint qui calcule les soldes projetés en combinant données passées et événements futurs.
    2. **Gestion des événements futurs** :
        - Créer une table dans la base de données pour stocker les événements récurrents :
            - Champs nécessaires : `nom_event`, `montant`, `périodicité`, `date_début`, `date_fin`.
        - Ajouter un endpoint pour gérer ces événements (ajout, modification, suppression).

#### Tests
- Tests unitaires pour vérifier les calculs des projections.
- Tests d’intégration entre le formulaire front-end et l’API.

### UX/UI
- Simplifier l'ajout des événements via un formulaire intuitif :
    - Exemple : un formulaire comportant les champs "Nom", "Montant", "Type" (entrée ou dépense), "Périodicité" (mensuel, trimestriel, annuel) et "Dates".
- Les projections budgétaires doivent être aisément lisibles et esthétiquement agréables.

---

## Résumé des livrables
1. **Graphique des totaux par catégorie** :
    - Backend : Endpoint pour calculer les totaux.
    - Frontend : Graphique interactif avec basculement entre vue mensuelle et catégorielle.
2. **Module de projections budgétaires** :
    - Gestion des événements futurs (front et back-end).
    - Graphique des projections et tableau des entrées/dépenses récurrentes.

Ces fonctionnalités doivent respecter la stack technologique du projet :
- **Java** pour le back-end.
- **TypeScript**, **HTML**, **PrimeNG** et **Tailwind CSS** pour le front-end.