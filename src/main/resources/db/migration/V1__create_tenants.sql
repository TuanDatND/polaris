CREATE TABLE tenants
(
    id                       UUID PRIMARY KEY,
    username                 TEXT UNIQUE NOT NULL,

    quota_cpu                INT         NOT NULL DEFAULT 10,
    quota_ram_mb             INT         NOT NULL DEFAULT 8192,
    quota_instance_count     INT         NOT NULL DEFAULT 5,

    allocated_cpu            INT         NOT NULL DEFAULT 0,
    allocated_ram_mb         INT         NOT NULL DEFAULT 0,
    allocated_instance_count INT         NOT NULL DEFAULT 0,

    version                  BIGINT      NOT NULL DEFAULT 0,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_tenant_quota_cpu_positive
        CHECK ( quota_cpu > 0 ),

    CONSTRAINT chk_tenant_quota_ram_mb_positive
        CHECK ( quota_ram_mb > 0 ),

    CONSTRAINT chk_tenant_quota_instance_positive
        CHECK ( quota_instance_count > 0 ),

    CONSTRAINT chk_tenant_allocated_cpu_valid
        CHECK ( allocated_cpu >= 0 AND allocated_cpu <= quota_cpu ),

    CONSTRAINT chk_tenant_allocated_ram_valid
        CHECK (allocated_ram_mb >= 0 AND allocated_ram_mb <= quota_ram_mb),

    CONSTRAINT chk_tenant_allocated_instance_valid
        CHECK (allocated_instance_count >= 0 AND allocated_instance_count <= quota_instance_count)
);