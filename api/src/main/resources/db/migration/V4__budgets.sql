-- Table des budgets mensuels par catégorie
-- Permet de définir un plafond de dépenses par catégorie pour un mois donné
CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    category_id UUID NOT NULL,
    -- Mois au format YYYY-MM (ex : '2025-03'), tri lexicographique correct
    month VARCHAR(7) NOT NULL,
    amount_cents BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_budget_household
        FOREIGN KEY (household_id)
            REFERENCES households(id),

    CONSTRAINT fk_budget_category
        FOREIGN KEY (category_id)
            REFERENCES categories(id),

    -- Un seul budget par catégorie et par mois pour un foyer donné
    CONSTRAINT uq_budget_household_category_month
        UNIQUE (household_id, category_id, month),

    -- Le montant doit être strictement positif
    CONSTRAINT chk_budget_amount_positive
        CHECK (amount_cents > 0)
);

-- Index pour les requêtes par foyer et mois (cas d'usage le plus fréquent)
CREATE INDEX idx_budget_household_month ON budgets(household_id, month);
-- Index pour les requêtes par catégorie
CREATE INDEX idx_budget_category ON budgets(category_id);
