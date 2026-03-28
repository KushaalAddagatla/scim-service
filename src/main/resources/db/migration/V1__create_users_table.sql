CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE scim_users (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id      VARCHAR(255),
    user_name        VARCHAR(255) NOT NULL UNIQUE,
    active           BOOLEAN NOT NULL DEFAULT true,
    display_name     VARCHAR(255),
    given_name       VARCHAR(255),
    family_name      VARCHAR(255),
    middle_name      VARCHAR(255),
    locale           VARCHAR(50),
    title            VARCHAR(255),
    profile_url      VARCHAR(500),
    timezone         VARCHAR(100),
    meta_version     INTEGER NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE scim_user_emails (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES scim_users(id) ON DELETE CASCADE,
    value       VARCHAR(255) NOT NULL,
    type        VARCHAR(50),        -- work, home, other
    primary_email BOOLEAN DEFAULT false,
    display     VARCHAR(255)
);

CREATE TABLE scim_user_phone_numbers (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES scim_users(id) ON DELETE CASCADE,
    value       VARCHAR(100) NOT NULL,
    type        VARCHAR(50),        -- work, home, mobile, fax, other
    primary_phone BOOLEAN DEFAULT false
);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    event_type      VARCHAR(100) NOT NULL,  -- PROVISION, DEPROVISION, SCIM_PATCH, etc.
    actor           VARCHAR(255),            -- system or manager email
    target_user_id  UUID,
    resource_id     VARCHAR(255),
    scim_operation  JSONB,                   -- raw request body
    outcome         VARCHAR(50) NOT NULL,    -- SUCCESS, FAILED, PENDING
    source_ip       VARCHAR(50),
    correlation_id  UUID
);

CREATE INDEX idx_scim_users_username    ON scim_users(user_name);
CREATE INDEX idx_scim_users_external_id ON scim_users(external_id);
CREATE INDEX idx_audit_log_timestamp    ON audit_log(timestamp);
CREATE INDEX idx_audit_log_target_user  ON audit_log(target_user_id);