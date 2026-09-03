# Security gap: password login has no brute-force protection

## Summary

Semaphore UI `v2.19.8` returns the same generic `401` for an existing account with a wrong password
and for an unknown account, and neither response creates a session cookie. However, five consecutive
failed password logins remain unthrottled: every request returns `401` without `Retry-After`, and the
server has no application-level counter, lockout or warning for the rejected attempts.

## Environment

- Semaphore UI image: `semaphoreui/semaphore:v2.19.8`;
- database: SQLite;
- profile: `core-sqlite-local`;
- reproduced: 2026-08-25.

## Automated reproduction

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.AuthenticationSecurityApiTest
```

The test sends only five invalid requests to the local fixture account. It then performs a valid
login to prove that the account remains usable and that the negative requests did not create a
session.

## Expected security behavior

Invalid credentials must not reveal whether an account exists and must never issue a session. A
deployment also needs an explicit brute-force control: application rate limiting, temporary
account/IP backoff, or a documented reverse-proxy policy. Rejected attempts should create a safe
warning or audit event without recording the submitted password.

## Actual result

- existing username with a wrong password: `401`, empty body, no session cookie;
- unknown username: identical `401`, empty body, no session cookie;
- empty password: `400`, no session cookie;
- five repeated failures: five ordinary `401` responses, no `429` and no `Retry-After`;
- correct credentials immediately afterward: `204` and a valid session.

The account-enumeration and session boundaries are correct. Brute-force resistance is absent.

## Source-level boundary

`api/login.go` resolves the user and compares bcrypt hashes in `loginByPassword`. Both unknown users
and password mismatches become `db.ErrNotFound`; the handler returns `401` without a body. That is
the correct indistinguishable response.

The same handler has no attempt counter, backoff or rate limiter. Its `db.ErrNotFound` branch returns
before logging, so rejected password attempts do not produce the warning requested by upstream
manual case TC-002. `util/config.go` exposes password-login enable/disable but no throttle or lockout
configuration.

## Impact

An internet-exposed self-hosted instance can accept sustained password guessing at the cost of a
bcrypt comparison for every valid username. Operators also lack an application audit trail for
detecting the attack. External reverse-proxy controls may mitigate the risk, but they are not part
of Semaphore's default deployment contract.

## Suggested correction

Add configurable rate limiting with bounded memory and a safe key such as source IP plus normalized
login. Return `429` with `Retry-After` after the threshold, emit a password-free security warning,
and document trusted-proxy handling. Keep responses indistinguishable for existing and unknown
accounts and avoid permanent account lockout that an attacker could use for denial of service.
