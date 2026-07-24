CREATE TABLE app_users
(
    id            UUID PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tenant_memberships
(
    user_id    UUID        NOT NULL REFERENCES app_users (id),
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    status     VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, tenant_id),

    CONSTRAINT chk_membership_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_memberships_tenant
    ON tenant_memberships (tenant_id);

CREATE TABLE iam_roles
(
    id          UUID PRIMARY KEY,
    tenant_id   UUID REFERENCES tenants (id),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    managed     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_iam_roles_tenant_name
    ON iam_roles (tenant_id, name)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_iam_roles_global_name
    ON iam_roles (name)
    WHERE tenant_id IS NULL;

CREATE TABLE iam_policies
(
    id          UUID PRIMARY KEY,
    tenant_id   UUID REFERENCES tenants (id),
    name        VARCHAR(100) NOT NULL,
    document    JSONB       NOT NULL,
    policy_version INT      NOT NULL DEFAULT 1,
    managed     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_policy_document_object
        CHECK (jsonb_typeof(document) = 'object'),

    CONSTRAINT chk_policy_version_positive
        CHECK (policy_version > 0)
);

CREATE UNIQUE INDEX uq_iam_policies_tenant_name
    ON iam_policies (tenant_id, name)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_iam_policies_global_name
    ON iam_policies (name)
    WHERE tenant_id IS NULL;

CREATE TABLE iam_role_policies
(
    role_id   UUID NOT NULL REFERENCES iam_roles (id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES iam_policies (id) ON DELETE CASCADE,

    PRIMARY KEY (role_id, policy_id)
);

CREATE TABLE iam_user_roles
(
    user_id    UUID        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role_id    UUID        NOT NULL REFERENCES iam_roles (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_iam_user_roles_role
    ON iam_user_roles (role_id);
