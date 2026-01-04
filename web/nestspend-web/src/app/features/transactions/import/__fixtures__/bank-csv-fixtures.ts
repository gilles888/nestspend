/**
 * Test fixtures for bank import parsers.
 * These are anonymized samples derived from real Belgian bank exports.
 */

/**
 * Belfius CSV with preamble (BE92 style export).
 * Features:
 * - Preamble lines before header
 * - Semicolon delimiter
 * - European date format (DD/MM/YYYY)
 * - European amount format with comma decimals
 */
export const BELFIUS_CSV_WITH_PREAMBLE = `Date de comptabilisation à partir de;01/12/2025
Date de comptabilisation jusqu'au;02/01/2026
Dernier solde;236,75 EUR
;
Compte;Date de comptabilisation;Numéro d'extrait;Numéro de transaction;Compte de la contrepartie;Nom contrepartie contient;Rue et numéro;Code postal et localité;Transaction;Date valeur;Montant;Devise;BIC;Code pays;Communications
BE92 0639 0253 5323;02/01/2026;1;1;;;;;;;02/01/2026;-5000,00;EUR;;;Virement vers compte épargne
BE92 0639 0253 5323;03/01/2026;1;2;BE68 5390 0754 7034;SUPERMARCHE SA;Rue Commerce 15;1000 Bruxelles;Paiement Bancontact;03/01/2026;-44,97;EUR;KREDBEBB;BE;Achat alimentaire
BE92 0639 0253 5323;03/01/2026;1;3;BE12 3456 7890 1234;EMPLOYEUR SPRL;Avenue Travail 1;1050 Ixelles;Virement entrant;03/01/2026;2500,00;EUR;GKCCBEBB;BE;Salaire janvier 2026
BE92 0639 0253 5323;02/01/2026;1;4;;;;;;;02/01/2026;- 12,50;EUR;;;Frais bancaires`;

/**
 * Belfius CSV with TAB delimiter and split amounts.
 * This simulates the case where amount "100,00" is split into "100" and "0"
 * due to delimiter/encoding issues.
 * Features:
 * - Tab delimiter
 * - Split amount columns (100 and 0 instead of 100,00)
 * - Preamble with corrupted characters
 */
export const BELFIUS_TAB_SPLIT_AMOUNT = `Date de comptabilisation à partir de\t01/12/2025\t\t\t\t\t\t\t\t\t\t\t\t\t
Date de comptabilisation jusqu'au\t31/12/2025\t\t\t\t\t\t\t\t\t\t\t\t\t
Dernier solde\t236\t75 EUR\t\t\t\t\t\t\t\t\t\t\t\t
\t\t\t\t\t\t\t\t\t\t\t\t\t\t
Compte\tDate de comptabilisation\tNuméro d'extrait\tNuméro de transaction\tCompte contrepartie\tNom contrepartie contient\tRue et numéro\tCode postal et localité\tTransaction\tDate valeur\tMontant\tDevise\tBIC\tCode pays\tCommunications
BE92 0639 0253 5323\t30/12/2025\t\t\tBE39 0835 7905 2819\tTest User\tAV TEST 54\t1030 BRUXELLES\tVIREMENT\t30/12/2025\t100\t0\tEUR\tGKCCBEBB\tBE
BE92 0639 0253 5323\t29/12/2025\t\t\t\t\t\t\tFRAIS BANCAIRES\t29/12/2025\t-25\t50\tEUR\t\t\tFrais mensuels`;

/**
 * ING CSV export (BE74 style).
 * Features:
 * - Header on first line
 * - Semicolon delimiter
 * - European date format (DD/MM/YYYY)
 * - Negative amounts with comma decimals
 * - Multiple description columns
 */
export const ING_CSV_EXPORT = `Numéro de compte;Nom du compte;Compte contrepartie;Numéro de mouvement;Date comptable;Date valeur;Montant;Devise;Libellés;Détails du mouvement;Message
BE74 3770 5469 5307;Compte courant;;1;02/01/2026;02/01/2026;-2,90;EUR;Frais de carte;;Frais mensuels
BE74 3770 5469 5307;Compte courant;BE68 5390 0754 7034;2;03/01/2026;03/01/2026;-125,50;EUR;Paiement;SUPERMARCHE SA;Courses hebdomadaires
BE74 3770 5469 5307;Compte courant;BE12 3456 7890 1234;3;03/01/2026;03/01/2026;1500,00;EUR;Virement reçu;SOCIETE ABC;Remboursement
BE74 3770 5469 5307;Compte courant;;4;04/01/2026;04/01/2026;-8,99;EUR;Domiciliation;STREAMING SERVICE;Abonnement mensuel`;

/**
 * Simple Transactions.csv format (already working).
 * Features:
 * - Standard CSV with semicolon
 * - European date format
 * - Simple column structure
 */
export const SIMPLE_TRANSACTIONS_CSV = `Compte;Date de comptabilisation;Montant;Devise;Transaction;Communications
BE92 0639 0253 5323;03/01/2026;-44,97;EUR;Paiement Bancontact;Supermarché
BE92 0639 0253 5323;03/01/2026;2500,00;EUR;Virement entrant;Salaire
BE92 0639 0253 5323;02/01/2026;-12,50;EUR;Frais;Frais mensuels`;

/**
 * Expected parsed results for BELFIUS_CSV_WITH_PREAMBLE.
 */
export const EXPECTED_BELFIUS_RESULTS = [
  {
    date: new Date(2026, 0, 2), // 02/01/2026
    amountCents: 500000,
    type: 'EXPENSE',
    counterparty: '',
    description: 'Virement vers compte épargne',
  },
  {
    date: new Date(2026, 0, 3), // 03/01/2026
    amountCents: 4497,
    type: 'EXPENSE',
    counterparty: 'SUPERMARCHE SA',
    description: 'Achat alimentaire',
  },
  {
    date: new Date(2026, 0, 3), // 03/01/2026
    amountCents: 250000,
    type: 'INCOME',
    counterparty: 'EMPLOYEUR SPRL',
    description: 'Salaire janvier 2026',
  },
  {
    date: new Date(2026, 0, 2), // 02/01/2026
    amountCents: 1250,
    type: 'EXPENSE',
    counterparty: '',
    description: 'Frais bancaires',
  },
];

/**
 * Expected parsed results for BELFIUS_TAB_SPLIT_AMOUNT.
 * Tests the split amount recombination logic.
 */
export const EXPECTED_BELFIUS_TAB_RESULTS = [
  {
    date: new Date(2025, 11, 30), // 30/12/2025
    amountCents: 10000, // 100,0 → 100.00 → 10000 cents
    type: 'INCOME',
    counterparty: 'Test User',
  },
  {
    date: new Date(2025, 11, 29), // 29/12/2025
    amountCents: 2550, // -25,50 → -25.50 → 2550 cents
    type: 'EXPENSE',
    counterparty: '',
  },
];

/**
 * Expected parsed results for ING_CSV_EXPORT.
 */
export const EXPECTED_ING_RESULTS = [
  {
    date: new Date(2026, 0, 2), // 02/01/2026
    amountCents: 290,
    type: 'EXPENSE',
    counterparty: 'Frais de carte',
    description: 'Frais de carte - Frais mensuels',
  },
  {
    date: new Date(2026, 0, 3), // 03/01/2026
    amountCents: 12550,
    type: 'EXPENSE',
    counterparty: 'Paiement',
    description: 'Paiement - SUPERMARCHE SA - Courses hebdomadaires',
  },
  {
    date: new Date(2026, 0, 3), // 03/01/2026
    amountCents: 150000,
    type: 'INCOME',
    counterparty: 'Virement reçu',
    description: 'Virement reçu - SOCIETE ABC - Remboursement',
  },
  {
    date: new Date(2026, 0, 4), // 04/01/2026
    amountCents: 899,
    type: 'EXPENSE',
    counterparty: 'Domiciliation',
    description: 'Domiciliation - STREAMING SERVICE - Abonnement mensuel',
  },
];
