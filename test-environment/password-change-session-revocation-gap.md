# Password changes do not revoke existing sessions

**Affected baseline:** `semaphoreui/semaphore:v2.19.12`  
**Component:** authentication / session lifecycle  
**Severity:** medium security gap  
**Status:** confirmed locally; the same source boundary remains on `develop` at `83bddaf0`

## Description

Changing a local user's password, including an administrator reset, updates the password hash but
does not expire any sessions already issued to that user. A session created before the password
change continues to authorize API requests afterward.

## Impact

A user cannot remove an attacker who already possesses a valid session by changing the password.
An administrator password reset has the same limitation, so account recovery requires an additional
session-revocation mechanism that is not exposed by the current Community API.

## Steps to reproduce

1. Create or reuse a local non-admin user.
2. Log in twice and retain both independent session cookies.
3. Through the first session, send `POST /api/users/{user_id}/password` with the correct current
   password and a new password.
4. Confirm that the old password can no longer create a session and the new password can.
5. Through the second pre-existing session, request `GET /api/user`.

**Expected:** changing or administratively resetting a password revokes the user's other active
sessions, so the second session receives `401`.

**Actual:** the second session still receives `200` and remains authenticated.

## Automated reproducer

```bash
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local \
  --tests io.bookwright.tests.semaphore.AuthenticationLifecycleApiTest
```

The canary records the current stable behaviour and will fail once Semaphore starts revoking the
pre-existing session.

## Source boundary

`UsersController.UpdateUserPassword` validates authorization and the current password, then calls
`Store.SetUserPassword`. The SQL implementation only updates `user.password`. Neither path expires
rows in `session`; `ExpireSession` is currently used by logout for one specific session only.
The same controller boundary was verified on upstream `develop` at `83bddaf0` on 2026-09-18.

## Recommended fix

After updating the password, expire every active session for the affected user except optionally the
session that performed a verified self-service change. Administrator resets should revoke all of the
target user's sessions. The operation should be transactional with the password update, and the API
contract should document whether the initiating session survives.
