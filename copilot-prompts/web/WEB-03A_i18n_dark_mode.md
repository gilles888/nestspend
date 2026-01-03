# WEB-03A — Multilangue (i18n) + Dark Mode

## Contexte
Projet NestSpend Web.
Stack:
- Angular standalone + routing
- Tailwind CSS
- PrimeNG (theme Aura)
- AppShell déjà en place
- Auth et OpenAPI déjà configurés ou en cours

Cette feature doit être ajoutée SANS casser les features existantes.

---

## Objectifs

### 🌍 Multilangue
- Support minimum:
    - 🇫🇷 Français (par défaut)
    - 🇬🇧 Anglais
- Architecture extensible (NL plus tard)
- Texte UI centralisé (pas de strings hardcodées)

### 🌙 Dark Mode
- Toggle clair / sombre
- Persistance du choix utilisateur (localStorage)
- Compatible Tailwind + PrimeNG
- Pas de duplication CSS inutile

---

## Partie 1 — Multilangue (i18n)

### Choix technique
- Utiliser `@ngx-translate/core`
- Loader JSON via HttpClient

### Tâches
1) Installer dépendances:
    - @ngx-translate/core
    - @ngx-translate/http-loader
2) Configurer TranslateModule en standalone:
    - defaultLang = 'fr'
    - fallback = 'fr'
3) Créer fichiers:
    - src/assets/i18n/fr.json
    - src/assets/i18n/en.json
4) Ajouter un `LanguageService`:
    - getCurrentLang()
    - setLang(lang)
    - persister dans localStorage
5) Ajouter un sélecteur de langue dans la Topbar:
    - FR / EN
6) Remplacer les textes statiques principaux par des clés i18n:
    - navigation (Dashboard, Transactions, Categories, Accounts)
    - titres de pages placeholders

---

## Partie 2 — Dark Mode

### Choix technique
- Utiliser le mode `class` de Tailwind
- Classe `dark` appliquée sur `<html>` ou `<body>`

### Tâches
1) Activer darkMode dans tailwind.config.js:
    - darkMode: 'class'
2) Créer un `ThemeService`:
    - isDark()
    - toggle()
    - persister dans localStorage
3) Appliquer la classe `dark` au démarrage selon préférence
4) Adapter le layout AppShell:
    - background clair / sombre
    - sidebar / topbar compatibles dark
5) Ajouter un bouton toggle dark/light dans la Topbar:
    - icône soleil / lune (PrimeIcons)

---

## Contraintes UI / UX
- Changement de langue et de thème instantané (sans reload)
- Accessible (aria-label sur boutons)
- UX simple, pas de surcharge visuelle
- Dark mode lisible (pas noir pur)

---

## Définition of Done (DoD)

- Langue FR par défaut
- Switch EN fonctionnel
- Textes UI traduits
- Dark mode ON/OFF fonctionnel
- Choix persisté après refresh
- Aucune régression sur AppShell et routing

---

## Livrables attendus
- Services: LanguageService, ThemeService
- Config Tailwind mise à jour
- Fichiers i18n JSON
- Modifications AppShell (Topbar)
- Code propre, typé, lisible
