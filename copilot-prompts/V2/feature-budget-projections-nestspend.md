# Nouvelle fonctionnalité : Projections Budgétaires (Entrées, Dépenses et Événements futurs) pour NestSpend

## Contexte
L'application actuelle offre une vue rétrospective des transactions (entrées et dépenses mensuelles), mais les utilisateurs souhaitent davantage planifier leur budget en intégrant des projections financières, incluant des événements futurs comme des abonnements (Netflix, ChatGPT, etc.), des paiements récurrents, ou encore des assurances.

Le but est de permettre aux utilisateurs d’anticiper leur situation financière en tenant compte des transactions passées, des entrées/dépenses régulières actuelles et des éléments planifiés dans le futur.

## Fonctionnalité à développer : Projections Budgétaires
### Objectifs
1. **Visualisation des projections financières** :
    - Offrir une vue prédictive des soldes futurs de l'utilisateur sur des périodes comme 1 mois, 3 mois, ou 1 an.
    - Afficher les soldes sur des graphiques interactifs.
    - Mettre en évidence les mois avec des soldes négatifs ou les prévisions risquant de dépasser les budgets habituels.

2. **Ajout d’événements futurs (manuels et récurrents)** :
    - Permettre l’ajout d'abonnements ou d'événements financiers planifiés (assurances à venir, abonnements, salaires, etc.).
    - Supporter les événements récurrents (p. ex. : mensuel, annuel).
    - Offrir la possibilité de gérer (créer/modifier/supprimer) ces événements.

3. **Design centré sur l’utilisateur :**
    - Une interface intuitive permettant aux utilisateurs de configurer facilement leur budget.
    - Une vue simplifiée et responsive compatible avec mobile et bureau.

---

## Spécifications techniques
### Front-End
#### Outils et technologies :
- **TypeScript**, **HTML**, **PrimeNG**, **Tailwind CSS**

1. **Graphiques projetant l’évolution des soldes** :
    - Utiliser **PrimeNG** avec `p-chart` pour afficher des graphiques linéaires ou courbes montrant les soldes projetés par mois.
    - Compléter avec un tableau listant les événements à venir (abonnements, paiements récurrents) et leurs montants.

2. **Formulaire pour ajouter/modifier des événements futurs** :
    - Créer un composant avec un formulaire pour :
        - Nommer l'événement (ex. : "Netflix").
        - Définir le type (`Entrée` ou `Dépense`).
        - Définir le montant.
        - Spécifier la périodicité (unique, mensuelle, annuelle, etc.).
        - Choisir une date de début et une durée éventuelle.
    - Utiliser **PrimeNG** pour simplifier les champs interactifs (ex. : calendriers, dropdowns, etc.).
    - Styliser le formulaire pour garantir une expérience utilisateur fluide avec **Tailwind CSS**, en maintenant l'esthétique actuelle.

3. **Interaction dynamique** :
    - Synchronisation des données du formulaire avec le graphique pour des mises à jour en temps réel des projections.
    - Filtrer les données affichées sur le graphique (ex. : projections avec ou sans événements futurs).

### Back-End
#### Outils et technologies :
- **Java**

1. **API de projections budgétaires** :
    - Implémenter un endpoint REST pour calculer les soldes projetés :
        - Rassembler toutes les transactions passées enregistrées.
        - Intégrer les événements ajoutés manuellement par l'utilisateur.
        - Retourner les soldes projetés pour chaque mois dans une période choisie (ex. : 6 mois, 1 an).

   Exemple de réponse d'API :
   ```json
   {
      "periode": "2026-01 à 2026-06",
      "projections": [
         {"mois": "Janvier", "solde": 1200.5},
         {"mois": "Février", "solde": 1050.3},
         {"mois": "Mars", "solde": 980.0},
         {"mois": "Avril", "solde": 1120.0}
      ],
      "evenements_futurs": [
         {"nom": "Netflix", "montant": 12.99, "periode": "Mensuel"},
         {"nom": "Assurance Auto", "montant": 300.0, "date": "2026-03-15"}
      ]
   }
   ```

2. **Gestion des événements futurs** :
    - Ajouter une table dans la base de données pour les événements récurrents :
        - Champs nécessaires : `id`, `nom_evenement`, `type`, `montant`, `periode`, `date_debut`, `date_fin`.
    - Création d’endpoints RESTful pour :
        - Ajouter un événement.
        - Modifier un événement.
        - Supprimer un événement.

3. **Calcul optimisé des projections** :
    - Basé sur les données existantes, utiliser des techniques d’optimisation pour minimiser les temps de calcul lors des requêtes.

---

## Étapes de validation
1. **Développement front-end** :
    - Créer un module complet comprenant graphiques, tableaux et formulaires interactifs.
    - Gérer les mises à jour en temps réel des données affichées.

2. **Développement back-end** :
    - Implémenter les endpoints nécessaires pour les projections et la gestion des événements futurs.
    - Tester les performances de chaque requête.

3. **Tests** :
    - **Unitaires** : Les calculs côté back-end doivent être précis et fiables.
    - **Intégration** : Vérifier la cohérence des données entre front et back-end.
    - **UX** : Valider la fluidité du module et sa simplicité d’utilisation via des tests utilisateurs.

4. **Livraison** :
    - Soumettre le code via un pull request, accompagné de documentation mise à jour (README).

---

## UX/UI et Responsive Design
- **Focus UX** : Le design doit être simple et agréable. Chaque action (ajouter un événement, visualiser les projections) doit être rapide et intuitive.
- **Support responsive** : Le module doit bien fonctionner sur toutes les tailles d’écran (mobile, tablette, desktop).

---

## Livrables
1. **Module complet de projections budgétaires** :
    - Inclure graphiques, tableau des événements futurs, et formulaire.
2. **Nouvelles APIs** :
    - Projections financières complètes et gestion des événements futurs.
3. **Documentation** :
    - Instructions sur l’ajout les projections budgétaires.
    - Description des nouveaux endpoints.