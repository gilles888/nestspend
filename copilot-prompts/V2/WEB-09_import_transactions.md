# WEB-09 — Import de transactions bancaires (Belfius / Keytrade / ING) + Assistant d’encodage

## Contexte
NestSpend Web (Angular + Tailwind + PrimeNG) consomme l’API via client OpenAPI généré.
Objectif: faciliter l’encodage des transactions via import de fichiers bancaires:
- Belfius: CSV, TXT
- Keytrade: CSV
- ING: CSV, TXT
  L’utilisateur doit pouvoir importer, prévisualiser, associer au bon compte, puis confirmer.

## Objectif fonctionnel
Ajouter sur la page Transactions un bouton "Importer" qui:
1) ouvre un wizard d’import
2) permet de choisir:
    - la banque (auto-détection si possible)
    - le compte NestSpend cible (Account) sur lequel importer
3) charge un fichier (CSV/TXT)
4) parse le fichier, normalise les lignes en Transactions candidates
5) affiche une prévisualisation + contrôle erreurs
6) évite les doublons
7) crée les transactions en batch via API (bulk) ou fallback en POST unitaire

## UX attendue (Wizard)
- Étape 1: Choisir banque (auto) + choisir Account (obligatoire)
- Étape 2: Upload fichier (CSV/TXT) + aperçu brut (10 lignes)
- Étape 3: Mapping/normalisation (auto) + tableau preview "Ce qui sera importé"
- Étape 4: Déduplication + Validation + Résumé
- Étape 5: Import final + écran résultat (créées / ignorées / erreurs)

UI: PrimeNG (Dialog / Steps / Table / FileUpload / Messages), Tailwind pour layout.

## Contraintes techniques
- Ne pas bloquer le thread UI (gérer parsing proprement, idéalement via Web Worker si volumineux > 5k lignes)
- Aucune DTO manuelle: utiliser les models OpenAPI générés
- L’import doit générer des transactions "propres":
    - tx_date (DATE)
    - type EXPENSE/INCOME
    - amount_cents (long)
    - merchant/libellé
    - note/communication
    - accountId choisi
    - categoryId: peut rester vide si ton API l’autorise, sinon utiliser une catégorie "Uncategorized" (voir Feature WEB-10)

## Parsing: stratégie par banque
Implémenter un module `src/app/features/transactions/import/` avec pattern Strategy:
- BankImportParser interface:
    - canHandle(fileName, mime, firstLines)
    - parse(fileText | csvRows): NormalizedImportedTransaction[]
- Parsers:
    - BelfiusCsvParser
    - BelfiusTxtParser
    - KeytradeCsvParser
    - IngCsvParser
    - IngTxtParser

La normalisation doit retourner une structure interne:
- importedDate (Date)
- signedAmountCents (positive income / positive expense ou bien sign + type)
- counterparty/merchant
- description/communication
- iban (si présent)
- externalId (si présent: ref bancaire)
- rawLine (debug)

Auto-détection:
- lire les 3-5 premières lignes/headers
- déduire la banque via colonnes connues (ou patterns)
- sinon demander à l’utilisateur

Lib parsing CSV: utiliser `papaparse` (ajouter dependency) et gérer:
- séparateurs `;` et `,`
- guillemets
- encodage UTF-8

TXT:
- parser lignes (souvent fixed-width ou séparateur)
- ignorer lignes vides/headers

## Déduplication (important)
Avant import final:
- Calculer une clé de déduplication par transaction:
    - date + amount + merchant + accountId (+ maybe communication)
- Appeler l’API pour checker existence sur une période:
    - soit endpoint dédié: POST /api/transactions/import/check
    - soit GET /api/transactions?from&to et comparer côté front (moins optimal)

Préféré:
- Ajouter côté API un endpoint bulk import:
    - POST /api/transactions/import
    - body: { accountId, items: [...] }
    - réponse: { createdCount, skippedCount, errors[] }
- Et un endpoint check:
    - POST /api/transactions/import/check
    - body: { accountId, from, to, keys[] }
    - réponse: { existingKeys[] }

⚠️ Si ces endpoints n’existent pas, les implémenter dans le backend + exposer dans OpenAPI pour génération.

## Modifications UI
- Ajouter un bouton "Importer" sur la page Transactions (toolbar)
- Créer ImportTransactionsDialogComponent + wizard
- Étape choix du compte:
    - lister accounts depuis API (OpenAPI)
- Preview table:
    - colonnes: date, type, amount, merchant, communication, statut (OK/Warning/Error)
- Afficher erreurs lignes (date invalide, montant invalide, colonne manquante)

## Livrables attendus
Frontend:
- components import wizard
- parsers par banque
- service ImportTransactionsService
- ajout dépendance papaparse
  Backend (si nécessaire):
- endpoints bulk import + check
- DTOs d’import
- logique de dédup + création
- tests basiques
  OpenAPI:
- endpoints visibles dans /v3/api-docs

## DoD
- L’utilisateur peut importer un fichier Belfius CSV (au minimum) et confirmer l’import
- Le compte cible est obligatoire
- Preview + validation + dédup fonctionnent
- L’import crée les transactions (bulk ou fallback)
- Aucun crash si fichier inattendu: message d’erreur clair
