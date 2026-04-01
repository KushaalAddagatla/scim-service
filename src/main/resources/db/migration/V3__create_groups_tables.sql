CREATE TABLE scim_groups (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id  VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    meta_version INTEGER NOT NULL DEFAULT 1,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Composite PK ensures a user can only be a member of a given group once.
-- ON DELETE CASCADE on both FKs: deleting a group removes its memberships;
-- deleting a user removes them from all groups (no orphan rows).
CREATE TABLE scim_group_memberships (
    group_id UUID NOT NULL REFERENCES scim_groups(id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES scim_users(id)  ON DELETE CASCADE,
    display  VARCHAR(255),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_scim_groups_display_name       ON scim_groups(display_name);
CREATE INDEX idx_scim_group_memberships_user_id ON scim_group_memberships(user_id);
