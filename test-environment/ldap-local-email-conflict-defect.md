# Defect: LDAP login with a local account's email returns 500

## Summary

When an LDAP user signs in with an email that already belongs to a local (password) account,
Semaphore `develop` answers `POST /api/auth/login` with `500` and an empty body instead of `401`.
The takeover protection itself holds: the local account is neither linked nor changed. The status
comes from a failed `INSERT` of a duplicate user, not from a deliberate rejection.

The defect was introduced by [`1201b3c1`](https://github.com/semaphoreui/semaphore/commit/1201b3c1)
(2026-07-14, external identities) and went unnoticed because the LDAPS profile failed on certificate
verification from 2026-07-19 until its fixture was fixed.

## Environment

- Application: `develop`, image `ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-develop-c89598aa27e1b560c98c9e7d62582a999d1e09af`;
  `api/login.go` and `api/login_identity.go` are unchanged up to `c170fce8`.
- Profile: `feature-ldap-tls` (SQLite, OpenLDAP `1.5.0`, LDAPS with certificate verification).
- Directory user `ldap-admin-conflict` with `mail: admin@localhost`, the email of the local admin
  (`test-environment/profiles/feature-ldap-tls/ldif/50-bookwright-users.ldif`).
- `external_auth_email_matching` is unset (`auto`).

## Steps to reproduce

```bash
test-environment/profile up feature-ldap-tls
curl -i -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"auth":"ldap-admin-conflict","password":"Bookwright-LDAP-Conflict-42!","method":"ldap"}'
test-environment/profile logs feature-ldap-tls | grep -E 'Local Account Conflict|UNIQUE'
```

The same happens without `method` (legacy login path).

## Expected result

`401`, as for any other rejected login, and no new user. This is what `develop` returned before
`1201b3c1`: `loginByLDAP` found the local user by email and answered `ErrNotFound` because it was
not external.

## Actual result

`500` with an empty body. The server log shows the bind succeeded and the insert failed:

```text
level=info msg="User LDAP Local Account Conflict with email admin@localhost authorized via LDAP correctly"
level=error msg="constraint failed: UNIQUE constraint failed: user.email (2067)"
```

The local admin keeps `external=false` and `admin=true`.

## Source-level boundary

`api/login_identity.go`, `resolveExternalUser`:

1. No external identity exists for the LDAP DN.
2. `matchExternalUserByEmail` finds the local account and returns `db.ErrNotFound` — the
   takeover-protection invariant ("Local accounts are never adopted by external providers").
3. `resolveExternalUser` treats `ErrNotFound` as "no match" and calls `CreateUserWithoutPassword`
   with the same email, which violates the unique index on `user.email`.
4. The error is not a `loginError`, so the handler answers `500`.

## Impact

- A user whose directory email collides with a local account sees a server error instead of a
  rejected login; administrators see an error-level database constraint failure in the log.
- Monitoring that alerts on `5xx` fires on an ordinary authentication outcome.
- On PostgreSQL and MySQL the same path depends on the driver's error text; the status is expected to
  be `500` as well, but only SQLite was checked.

## Automated reproducer

```bash
test-environment/profile test feature-ldap-tls \
  --tests 'io.bookwright.tests.semaphore.SemaphoreLdapAuthenticationTest'
```

`LDAP login cannot take over a local account with the same email` fails with
`Expected invalid login to return 4xx but received 500`. The assertion is intentionally not
changed; the test turns green when the defect is fixed.

## Suggested correction

Distinguish "a local account owns this email or username" from "no account matches" in
`matchExternalUserByEmail`, and turn the former into a `loginError` (for example
`audit.ReasonLocalAccountConflict`) so the handler answers `401` and the audit log records the reason.
