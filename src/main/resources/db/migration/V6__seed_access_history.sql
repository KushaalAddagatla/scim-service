-- Set manager relationships on seeded users so the escalation email path is exercisable locally.
-- Alice (001) is the Engineering Manager — Bob and Carol report to her.
UPDATE scim_users SET manager_id = 'a1000000-0000-0000-0000-000000000001'
WHERE id IN (
    'a1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000003'
);

-- Stale access_history rows: last_accessed_at > 90 days ago.
-- These are the rows the certification scheduler will pick up on its first run.
-- Fixed UUIDs for idempotency across restarts.
INSERT INTO access_history (id, user_id, resource_id, resource_name, access_granted_at, last_accessed_at, access_status)
VALUES
    -- Bob: stale access to GitHub Enterprise and AWS Production
    ('c3000000-0000-0000-0000-000000000001',
     'a1000000-0000-0000-0000-000000000002',
     'res-github-enterprise', 'GitHub Enterprise',
     NOW() - INTERVAL '365 days', NOW() - INTERVAL '100 days', 'ACTIVE'),

    ('c3000000-0000-0000-0000-000000000002',
     'a1000000-0000-0000-0000-000000000002',
     'res-aws-production', 'AWS Production',
     NOW() - INTERVAL '200 days', NOW() - INTERVAL '120 days', 'ACTIVE'),

    -- Carol: stale access to AWS Production and Jira
    ('c3000000-0000-0000-0000-000000000003',
     'a1000000-0000-0000-0000-000000000003',
     'res-aws-production', 'AWS Production',
     NOW() - INTERVAL '180 days', NOW() - INTERVAL '110 days', 'ACTIVE'),

    ('c3000000-0000-0000-0000-000000000004',
     'a1000000-0000-0000-0000-000000000003',
     'res-jira', 'Jira',
     NOW() - INTERVAL '400 days', NOW() - INTERVAL '95 days', 'ACTIVE'),

    -- Alice: recent access — should NOT be picked up by the scheduler (last_accessed_at < 90 days)
    ('c3000000-0000-0000-0000-000000000005',
     'a1000000-0000-0000-0000-000000000001',
     'res-aws-production', 'AWS Production',
     NOW() - INTERVAL '500 days', NOW() - INTERVAL '10 days', 'ACTIVE');
