-- Seed data for local dev / demo against Okta or Azure AD dev tenant.
-- Uses fixed UUIDs so the data is idempotent across restarts.

INSERT INTO scim_users (id, external_id, user_name, active, display_name,
                        given_name, family_name, title, meta_version)
VALUES
    ('a1000000-0000-0000-0000-000000000001',
     'okta-uid-001', 'alice.jones@example.com', true, 'Alice Jones',
     'Alice', 'Jones', 'Engineering Manager', 1),

    ('a1000000-0000-0000-0000-000000000002',
     'okta-uid-002', 'bob.smith@example.com', true, 'Bob Smith',
     'Bob', 'Smith', 'Software Engineer', 1),

    ('a1000000-0000-0000-0000-000000000003',
     'okta-uid-003', 'carol.white@example.com', true, 'Carol White',
     'Carol', 'White', 'Security Engineer', 1),

    -- Inactive user — demonstrates soft-delete / deprovisioning lifecycle
    ('a1000000-0000-0000-0000-000000000004',
     'okta-uid-004', 'dan.lee@example.com', false, 'Dan Lee',
     'Dan', 'Lee', 'Contractor', 2);

INSERT INTO scim_user_emails (user_id, value, type, primary_email)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'alice.jones@example.com',  'work', true),
    ('a1000000-0000-0000-0000-000000000002', 'bob.smith@example.com',    'work', true),
    ('a1000000-0000-0000-0000-000000000003', 'carol.white@example.com',  'work', true),
    ('a1000000-0000-0000-0000-000000000004', 'dan.lee@example.com',      'work', true);

INSERT INTO scim_user_phone_numbers (user_id, value, type, primary_phone)
VALUES
    ('a1000000-0000-0000-0000-000000000001', '+1-555-100-0001', 'work', true),
    ('a1000000-0000-0000-0000-000000000003', '+1-555-100-0003', 'work', true);

INSERT INTO audit_log (id, event_type, actor, target_user_id, resource_id, outcome)
VALUES
    ('b2000000-0000-0000-0000-000000000001',
     'PROVISION', 'okta-provisioner',
     'a1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'SUCCESS'),

    ('b2000000-0000-0000-0000-000000000002',
     'PROVISION', 'okta-provisioner',
     'a1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000002', 'SUCCESS'),

    ('b2000000-0000-0000-0000-000000000003',
     'PROVISION', 'okta-provisioner',
     'a1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000003', 'SUCCESS'),

    ('b2000000-0000-0000-0000-000000000004',
     'PROVISION', 'okta-provisioner',
     'a1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000004', 'SUCCESS'),

    ('b2000000-0000-0000-0000-000000000005',
     'DEPROVISION', 'okta-provisioner',
     'a1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000004', 'SUCCESS');
