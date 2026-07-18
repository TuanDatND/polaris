CREATE TABLE instances
(
    id             UUID PRIMARY KEY,

    tenant_id      UUID        NOT NULL REFERENCES tenants (id),

    quota_released BOOLEAN     NOT NULL DEFAULT FALSE,

    name           TEXT        NOT NULL,
    image_name     TEXT        NOT NULL,

    cpu_allocated  INT         NOT NULL,
    ram_mb         INT         NOT NULL,

    desired_state  TEXT        NOT NULL,
    current_state  TEXT        NOT NULL,

    container_id   TEXT,

    failure_reason TEXT,

    version        BIGINT      NOT NULL DEFAULT 0,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),


    CONSTRAINT chk_instance_cpu_positive
        CHECK (cpu_allocated > 0),

    CONSTRAINT chk_instance_ram_positive
        CHECK (ram_mb > 0),

    CONSTRAINT chk_instance_desired_state
        CHECK (desired_state IN (
                                 'RUNNING',
                                 'STOPPED',
                                 'DELETED'
            )),

    CONSTRAINT chk_instance_current_state
        CHECK (current_state IN (
                                 'PENDING',
                                 'PROVISIONING',
                                 'STARTING',
                                 'RUNNING',
                                 'STOPPING',
                                 'STOPPED',
                                 'DELETING',
                                 'DELETED',
                                 'FAILED'
            )),

    CONSTRAINT chk_instance_container_id_state
        CHECK (
            container_id IS NULL
                OR current_state IN (
                                     'PROVISIONING',
                                     'STARTING',
                                     'RUNNING',
                                     'STOPPING',
                                     'STOPPED',
                                     'DELETING',
                                     'FAILED'
                )
            )
);

CREATE UNIQUE INDEX uq_instances_active_tenant_name
    ON instances (tenant_id, name) WHERE current_state <> 'DELETED';

CREATE INDEX idx_instances_tenant
    ON instances (tenant_id);

CREATE INDEX idx_instances_current_state_updated_at
    ON instances (current_state, updated_at);

CREATE INDEX idx_instances_desired_current
    ON instances (desired_state, current_state);

CREATE INDEX idx_instances_drift
    ON instances (desired_state, current_state, updated_at) WHERE desired_state != current_state;