-- access_history tracks which users have access to which resources and when they last used it.
-- The certification engine queries this table to find stale access (last_accessed_at > 90 days ago).
CREATE TABLE access_history (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID NOT NULL REFERENCES scim_users(id) ON DELETE CASCADE,
    resource_id       VARCHAR(255) NOT NULL,
    resource_name     VARCHAR(255) NOT NULL,
    access_granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_accessed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    access_status     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (access_status IN ('ACTIVE', 'REVOKED'))
);

-- certifications are opened by the scheduler for each stale access_history row.
-- token_hash stores SHA-256(rawToken) — the raw token is only sent in the email link, never persisted.
-- Single-use enforcement: token_used flipped to true on first valid click, before any status update.
CREATE TABLE certifications (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID NOT NULL REFERENCES scim_users(id) ON DELETE CASCADE,
    resource_id  VARCHAR(255) NOT NULL,
    reviewer_id  UUID REFERENCES scim_users(id) ON DELETE SET NULL,
    status       VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED', 'EXPIRED')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at   TIMESTAMP WITH TIME ZONE,
    token_hash   VARCHAR(64) UNIQUE,
    token_used   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_access_history_user_id        ON access_history(user_id);
CREATE INDEX idx_access_history_last_accessed  ON access_history(last_accessed_at);
CREATE INDEX idx_certifications_status         ON certifications(status);
CREATE INDEX idx_certifications_token_hash     ON certifications(token_hash);
