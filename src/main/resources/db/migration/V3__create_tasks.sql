CREATE TABLE tasks
(
    id              UUID PRIMARY KEY,

    type            TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'QUEUED',

    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    instance_id     UUID        NOT NULL REFERENCES instances (id),

    attempts        INT         NOT NULL DEFAULT 0,
    max_attempts    INT         NOT NULL DEFAULT 5,

    available_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    claim_token     UUID,

    locked_at       TIMESTAMPTZ,
    locked_by       TEXT,

    idempotency_key TEXT,

    payload         JSONB,
    last_error      TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_task_type
        CHECK (type IN (
                        'CREATE_INSTANCE',
                        'START_INSTANCE',
                        'STOP_INSTANCE',
                        'DELETE_INSTANCE',
                        'RECONCILE_INSTANCE'
            )),

    CONSTRAINT chk_task_status
        CHECK (status IN (
                          'QUEUED',
                          'RUNNING',
                          'SUCCESS',
                          'FAILED',
                          'CANCELLED'
            )),

    CONSTRAINT chk_task_attempts_valid
        CHECK (attempts >= 0 AND attempts <= max_attempts),

    CONSTRAINT chk_task_max_attempts_positive
        CHECK (max_attempts > 0),

    CONSTRAINT uq_tasks_tenant_idempotency
        UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_tasks_poll
    ON tasks (status, available_at) WHERE status = 'QUEUED';

CREATE INDEX idx_tasks_running_locked
    ON tasks (status, locked_at) WHERE status = 'RUNNING';

CREATE INDEX idx_tasks_instance
    ON tasks (instance_id);

CREATE INDEX idx_tasks_tenant
    ON tasks (tenant_id);