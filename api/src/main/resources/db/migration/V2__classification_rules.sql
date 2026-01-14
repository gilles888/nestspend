-- Classification rules for auto-categorization (WEB-10A)
CREATE TABLE classification_rules (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,
    field VARCHAR(20) NOT NULL,
    match_type VARCHAR(20) NOT NULL,
    pattern VARCHAR(255) NOT NULL,
    category_id UUID NOT NULL,
    confidence INT NOT NULL DEFAULT 80,
    source VARCHAR(10) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_rule_household
        FOREIGN KEY (household_id)
            REFERENCES households(id),

    CONSTRAINT fk_rule_category
        FOREIGN KEY (category_id)
            REFERENCES categories(id),

    CONSTRAINT chk_confidence_range
        CHECK (confidence >= 0 AND confidence <= 100)
);

CREATE INDEX idx_rule_household ON classification_rules(household_id);
CREATE INDEX idx_rule_enabled ON classification_rules(household_id, enabled);
