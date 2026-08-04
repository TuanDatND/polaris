ALTER TABLE instances
    ADD COLUMN generation BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN observed_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_observed_state TEXT NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN last_observed_at TIMESTAMPTZ;

ALTER TABLE instances
    ADD CONSTRAINT chk_instance_generation_positive
        CHECK (generation > 0),
    ADD CONSTRAINT chk_instance_observed_generation_valid
        CHECK (
            observed_generation >= 0
            AND observed_generation <= generation
        ),
    ADD CONSTRAINT chk_instance_last_observed_state
        CHECK (
            last_observed_state IN (
                'MISSING',
                'CREATED',
                'RUNNING',
                'STOPPED',
                'UNKNOWN'
            )
        );