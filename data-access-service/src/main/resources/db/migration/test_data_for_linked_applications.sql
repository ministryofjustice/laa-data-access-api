delete from applications;
delete from Caseworkers;

--
-- Caseworkers
---
INSERT INTO caseworkers (id, username) VALUES ('8a082fe2-d539-4177-aae3-7498fd5904c7', 'caseworker1');
INSERT INTO caseworkers (id, username) VALUES ('41435bf3-625f-495a-ac1d-9bb95229b5a4', 'caseworker2');
INSERT INTO caseworkers (id, username) VALUES ('afffcfe0-2b5b-40da-a8fc-5e31d724bb25', 'caseworker3');
INSERT INTO caseworkers (id, username) VALUES ('b42db458-8740-456a-a3b2-75910785a9f2', 'caseworker4');
INSERT INTO caseworkers (id, username) VALUES ('450bb6da-f06f-4251-a740-d015bea9b00c', 'caseworker5');
INSERT INTO caseworkers (id, username) VALUES ('a291fbc4-146d-474d-a0c0-eae6a0ff04ce', 'caseworker6');
INSERT INTO caseworkers (id, username) VALUES ('c5748122-6183-4255-aae0-a2916907b816', 'caseworker7');
INSERT INTO caseworkers (id, username) VALUES ('0523e75e-139c-4355-8ef5-62aa5ee13b26', 'caseworker8');
INSERT INTO caseworkers (id, username) VALUES ('b73d1861-e128-4dd3-8554-3e4bfdcb1b44', 'Ant');
INSERT INTO caseworkers (id, username) VALUES ('0df62456-e106-4c77-8d2a-8f20eadeb103', 'Hev');
INSERT INTO caseworkers (id, username) VALUES ('0cf2cde5-6efa-4aa0-84a2-ea2beabff5d1', 'Gabby');

---
--- 1 Lead with Two Associated Applications
---
--- Lead 1
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('00000000-0000-0000-0000-000000000001',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        '11111111-1111-1111-1111-111111111111',
        '2024-01-01T10:00:00Z',
        'LAA-LEAD123456',
        '8a082fe2-d539-4177-aae3-7498fd5904c7');

--- Associated 1A - Linked to lead
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('bb000000-0000-0000-0000-000000000001',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        '11111111-1111-1111-1111-111111111111',
        '2024-01-02T10:00:00Z',
        'LAA-ASSOC123456-A',
        '8a082fe2-d539-4177-aae3-7498fd5904c7');

--- Associated 1B - Linked to lead
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('cc000000-0000-0000-0000-000000000001',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        uuid_generate_v4(),
        '2024-01-02T10:00:00Z',
        'LAA-ASSOC123456-B',
        '8a082fe2-d539-4177-aae3-7498fd5904c7');

---
--- 1 Lead with One Associated Applications
---
--- Lead 1
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('00000000-0000-0000-0000-000000000002',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        '11111111-1111-1111-1111-111111111111',
        '2024-01-01T10:00:00Z',
        'LAA-LEAD-22123456',
        '41435bf3-625f-495a-ac1d-9bb95229b5a4');

--- Associated 1A - Linked to lead
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('ff000000-0000-0000-0000-000000000001',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        '11111111-1111-1111-1111-111111111111',
        '2024-01-02T10:00:00Z',
        'LAA-ASSOC1-223456-A',
        '41435bf3-625f-495a-ac1d-9bb95229b5a4');

---
--- Linked Applications
---
INSERT INTO linked_applications (id, lead_application_id, associated_application_id, linked_at)
VALUES (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'bb000000-0000-0000-0000-000000000001', NOW());

INSERT INTO linked_applications (id, lead_application_id, associated_application_id, linked_at)
VALUES (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'cc000000-0000-0000-0000-000000000001', NOW());

INSERT INTO linked_applications (id, lead_application_id, associated_application_id, linked_at)
VALUES (uuid_generate_v4(), '00000000-0000-0000-0000-000000000001', 'ee000000-0000-0000-0000-000000000001', NOW());

INSERT INTO linked_applications (id, lead_application_id, associated_application_id, linked_at)
VALUES (uuid_generate_v4(), '00000000-0000-0000-0000-000000000002', 'ff000000-0000-0000-0000-000000000001', NOW());

---
--- Standalone Application (no linked applications)
---
INSERT INTO applications (id, status, application_content, apply_application_id, submitted_at, laa_reference, caseworker_id)
VALUES ('dd000000-0000-0000-0000-000000000001',
        'APPLICATION_IN_PROGRESS',
        '{}'::jsonb,
        uuid_generate_v4(),
        '2024-01-03T10:00:00Z',
        'LAA-STANDALONE-001',
        'afffcfe0-2b5b-40da-a8fc-5e31d724bb25');

INSERT INTO individuals (id, first_name, last_name, date_of_birth, individual_type, individual_content, created_at, modified_at)
VALUES ('ee111111-1111-1111-1111-111111111111',
        'Jimi',
        'Hendrix',
        '1942-11-27',
        'CLIENT',
        '{}',
        NOW(),
        NOW());

INSERT INTO linked_individuals (application_id, individual_id)
VALUES ('dd000000-0000-0000-0000-000000000001', 'ee111111-1111-1111-1111-111111111111');
