CREATE TABLE oauth2_registered_client
(
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    client_secret                 VARCHAR(200),
    client_secret_expires_at      TIMESTAMPTZ,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000),
    post_logout_redirect_uris     VARCHAR(1000),
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,

    CONSTRAINT pk_oauth2_registered_client
        PRIMARY KEY (id),

    CONSTRAINT uq_oauth2_registered_client_client_id
        UNIQUE (client_id)
);