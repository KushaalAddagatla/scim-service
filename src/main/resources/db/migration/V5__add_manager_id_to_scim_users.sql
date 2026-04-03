-- manager_id is a self-referential FK on scim_users.
-- Used by the escalation scheduler to email a user's manager when a certification expires with no response.
-- ON DELETE SET NULL: if the manager account is deprovisioned, the report's manager_id becomes null
-- rather than blocking the delete or orphaning the row.
ALTER TABLE scim_users
    ADD COLUMN manager_id UUID REFERENCES scim_users(id) ON DELETE SET NULL;

CREATE INDEX idx_scim_users_manager_id ON scim_users(manager_id);
