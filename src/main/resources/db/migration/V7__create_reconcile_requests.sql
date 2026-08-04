CREATE TABLE reconcile_requests
(
    instance_id          UUID PRIMARY KEY
        REFERENCES instances (id) ON DELETE CASCADE,

    status               TEXT        NOT NULL DEFAULT 'READY',
    requested_generation BIGINT      NOT NULL,
    available_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    failure_count        INT         NOT NULL DEFAULT 0,

    claim_token          UUID,
    claimed_by           TEXT,
    lease_expires_at     TIMESTAMPTZ,

    last_error           TEXT,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reconcile_request_status
        CHECK (status IN ('READY', 'RUNNING', 'BLOCKED')),

    CONSTRAINT chk_reconcile_request_generation_positive
        CHECK (requested_generation > 0),

    CONSTRAINT chk_reconcile_request_failure_count
        CHECK (failure_count >= 0),

    CONSTRAINT chk_reconcile_request_claim_shape
        CHECK (
            (
                status = 'RUNNING'
                    AND claim_token IS NOT NULL
                    AND claimed_by IS NOT NULL
                    AND lease_expires_at IS NOT NULL
                )
                OR
            (
                status <> 'RUNNING'
                    AND claim_token IS NULL
                    AND claimed_by IS NULL
                    AND lease_expires_at IS NULL
                )
            )
);

CREATE INDEX idx_reconcile_requests_ready
    ON reconcile_requests (available_at, instance_id)
    WHERE status = 'READY';

CREATE INDEX idx_reconcile_requests_expired_lease
    ON reconcile_requests (lease_expires_at)
    WHERE status = 'RUNNING';