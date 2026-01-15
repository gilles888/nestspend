-- Future recurring events for budget projections
CREATE TABLE future_events (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    amount_cents BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL,
    periodicity VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    CONSTRAINT fk_event_household
        FOREIGN KEY (household_id)
            REFERENCES households(id)
);

CREATE INDEX idx_event_household ON future_events(household_id);
CREATE INDEX idx_event_date_range ON future_events(household_id, start_date, end_date);
