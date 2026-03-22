-- =============================================================================
-- Migration V5 : Fonctionnalité "Mois Type"
-- Permet de définir un mois type avec des dépenses types (fixes/variables)
-- pour calculer des projections mensuelles et annuelles d'épargne.
-- Compatible H2 (mode PostgreSQL) et PostgreSQL.
-- =============================================================================

-- Table principale représentant un mois type budgétaire pour un foyer.
-- Un mois type regroupe des dépenses types et des informations de revenus.
CREATE TABLE mois_type (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    -- Nom descriptif du mois type (ex : "Mois standard 2026")
    nom VARCHAR(255) NOT NULL,
    -- Année de référence pour ce mois type
    annee INT NOT NULL,
    -- Revenus principaux du foyer en centimes
    revenus BIGINT NOT NULL DEFAULT 0,
    -- Autres revenus (primes, revenus locatifs, etc.) en centimes
    autres_revenus BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_mois_type_household
        FOREIGN KEY (household_id)
            REFERENCES households(id)
);

-- Index pour les requêtes par foyer (cas d'usage principal)
CREATE INDEX idx_mois_type_household ON mois_type(household_id);
-- Index pour les requêtes par foyer et année
CREATE INDEX idx_mois_type_household_annee ON mois_type(household_id, annee);

-- Table des dépenses types rattachées à un mois type.
-- Chaque dépense type représente une charge récurrente avec sa fréquence.
CREATE TABLE depenses_type (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    -- Référence au mois type auquel cette dépense est rattachée
    mois_type_id UUID NOT NULL,
    -- Libellé de la dépense (ex : "Loyer", "Abonnement Netflix")
    nom VARCHAR(255) NOT NULL,
    -- Montant de la dépense en centimes (toujours positif)
    montant BIGINT NOT NULL,
    -- Catégorie de la dépense parmi les valeurs prédéfinies
    categorie VARCHAR(50) NOT NULL,
    -- Nature de la dépense : FIXE (même montant chaque période) ou VARIABLE
    type_depense VARCHAR(20) NOT NULL,
    -- Fréquence de la dépense : MENSUELLE, TRIMESTRIELLE ou ANNUELLE
    frequence VARCHAR(20) NOT NULL,
    -- Indique si la dépense est active (false = exclue des calculs)
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_depense_type_mois_type
        FOREIGN KEY (mois_type_id)
            REFERENCES mois_type(id),

    CONSTRAINT fk_depense_type_household
        FOREIGN KEY (household_id)
            REFERENCES households(id),

    -- Le montant doit être positif
    CONSTRAINT chk_depense_type_montant_positif
        CHECK (montant > 0)
);

-- Index pour les requêtes par mois type (cas d'usage le plus fréquent)
CREATE INDEX idx_depense_type_mois_type ON depenses_type(mois_type_id);
-- Index pour les requêtes par foyer
CREATE INDEX idx_depense_type_household ON depenses_type(household_id);
-- Index pour filtrer les dépenses actives
CREATE INDEX idx_depense_type_actif ON depenses_type(mois_type_id, actif);
