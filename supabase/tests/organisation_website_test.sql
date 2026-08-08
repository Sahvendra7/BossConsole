-- pgTAP tests for the organisation website (migration 20260807000000).
-- Run with: supabase test db
--
-- The website is the one organisation field that is BOTH filled in by any
-- authenticated user (on a request, before any review) and rendered as a link on
-- a page other members read. That combination is what makes the scheme check a
-- security property rather than tidiness: a javascript: or data: URL stored here
-- would be self-service stored XSS.
--
-- It is checked in two places on purpose - the RPC, so a typo comes back as a
-- sentence, and the column CHECK, so nothing that bypasses the RPC can store one.
-- Both are asserted below, because a test that only exercised the RPC would pass
-- with the constraint dropped.

begin;
select plan(12);

insert into auth.users (id, email, email_confirmed_at) values
    ('90000000-0000-0000-0000-000000000001', 'websiteasker@pgtap.test', now()),
    ('90000000-0000-0000-0000-000000000002', 'websiteadmin@pgtap.test', now());

insert into public.user_roles (user_id, role_id)
select '90000000-0000-0000-0000-000000000002', r.id
  from public.roles r where r.name = 'admin'
on conflict do nothing;

select set_config('request.jwt.claims', '{"role":"service_role"}', true);


-- ===========================================================================
-- The RPC refuses anything that is not http or https
-- ===========================================================================
select is(
    (select public.submit_organisation_request(
        'Evil', 'evilsite', null, null, null,
        'javascript:alert(1)', '90000000-0000-0000-0000-000000000001') ->> 'error'),
    'Website must be a full http:// or https:// address',
    'a javascript: URL is refused'
);

select is(
    (select public.submit_organisation_request(
        'Data', 'datasite', null, null, null,
        'data:text/html,<script>alert(1)</script>', '90000000-0000-0000-0000-000000000001') ->> 'error'),
    'Website must be a full http:// or https:// address',
    'a data: URL is refused'
);

select is(
    (select public.submit_organisation_request(
        'Bare', 'baresite', null, null, null,
        'acme.com', '90000000-0000-0000-0000-000000000001') ->> 'error'),
    'Website must be a full http:// or https:// address',
    'a bare domain with no scheme is refused, so it cannot render as a relative link'
);

select is(
    (select public.submit_organisation_request(
        'Long', 'longsite', null, null, null,
        'https://' || repeat('a', 500) || '.com', '90000000-0000-0000-0000-000000000001') ->> 'error'),
    'Website must be 500 characters or fewer',
    'an over-long website is refused'
);


-- ===========================================================================
-- A valid one is stored, and survives approval onto the organisation
-- ===========================================================================
select ok(
    (public.submit_organisation_request(
        'Acme', 'acmesite', 'Acme Corp', 'acmesite.example', 'Because.',
        'https://acmesite.example', '90000000-0000-0000-0000-000000000001') ->> 'success')::boolean,
    'a valid https website is accepted'
);

select is(
    (select website from public.organisation_requests where slug = 'acmesite'),
    'https://acmesite.example',
    'the website is stored on the request'
);

select ok(
    (public.approve_organisation_request(
        (select id from public.organisation_requests where slug = 'acmesite'),
        null, '90000000-0000-0000-0000-000000000002') ->> 'success')::boolean,
    'the request can be approved'
);

-- The whole point of asking at request time: it has to reach the organisation.
select is(
    (select website from public.organisations where slug = 'acmesite'),
    'https://acmesite.example',
    'approval carries the website onto the organisation'
);

-- And it has to reach the page that renders it.
select is(
    (public.get_organisation_detail(
        (select id from public.organisations where slug = 'acmesite'),
        '90000000-0000-0000-0000-000000000002') -> 'data' ->> 'website'),
    'https://acmesite.example',
    'get_organisation_detail projects the website, so a page can render it'
);


-- ===========================================================================
-- An organisation without one is not broken by it
-- ===========================================================================
select ok(
    (public.submit_organisation_request(
        'Plain', 'plainsite', null, null, null,
        null, '90000000-0000-0000-0000-000000000001') ->> 'success')::boolean,
    'the website is optional'
);


-- ===========================================================================
-- The column CHECK, independent of the RPC
-- ===========================================================================
-- Asserted separately because every case above goes through the RPC, and all of
-- them would still pass with the constraint dropped.
select throws_ok(
    $$update public.organisations set website = 'javascript:alert(1)' where slug = 'acmesite'$$,
    '23514',
    null,
    'the column CHECK refuses a javascript: URL written directly'
);

select throws_ok(
    $$update public.organisations set website = '' where slug = 'acmesite'$$,
    '23514',
    null,
    'an empty string is refused, so "unset" has exactly one representation'
);

select * from finish();
rollback;
