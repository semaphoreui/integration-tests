# Integration child mutation regression

## Summary

Semaphore `v2.19.12` returned `204 No Content` for three documented integration mutation requests
without applying the requested change on SQLite:

- `PUT /api/project/{project_id}/integrations/{integration_id}/matchers/{matcher_id}`;
- `DELETE /api/project/{project_id}/integrations/{integration_id}/matchers/{matcher_id}`;
- `DELETE /api/project/{project_id}/integrations/{integration_id}/values/{value_id}`.

The integration, matcher and extracted value remain readable after the successful responses. Updating
the integration itself, updating an extracted value, and deleting an integration alias work in the
same scenario, which limits the defect to the child mutation paths above.

**Severity:** medium

**Component:** integrations / API persistence

**Historical baseline:** `semaphoreui/semaphore:v2.19.12` (`012ed06d`) with SQLite

**Current status:** fixed by
[`1af4c105`](https://github.com/semaphoreui/semaphore/commit/1af4c1052dba7da6c3bf55307013ba199421a745).
The integration suite runs against the current Semaphore application source and verifies the
positive persistence contract.

## User impact

The UI and API clients receive a successful response and can tell the operator that a webhook rule
was changed or removed while Semaphore continues using the old configuration. An unwanted matcher
can therefore keep launching tasks, and an extracted value can keep injecting data into later task
runs. The user has no failure response from which to recover.

## Reproduction

1. Create a project, runnable template and integration.
2. Add a header matcher and a body extracted value.
3. Send the documented matcher update payload without IDs in the body:

   ```json
   {
     "name": "Route updated deploy event",
     "match_type": "header",
     "method": "equals",
     "body_data_type": "string",
     "key": "X-Bookwright-Event",
     "value": "deploy-updated"
   }
   ```

4. Observe `204`, then list the matchers: the old name and value remain.
5. Delete that matcher and the extracted value through their documented URLs.
6. Observe `204` for both requests, then list both collections: both records remain.

**Expected:** each `204` mutation is persisted; subsequent list/get requests show the updated matcher
or no longer contain a deleted child.

**Actual:** all three requests return `204`, but the persisted records are unchanged.

## Cause

The `v2.19.12` matcher update handler binds `IntegrationMatcherRequest` directly but does not copy
`matcher_id` from the URL. The documented request schema contains neither `id` nor `integration_id`,
so the store executes an update for matcher ID `0`. The SQL path does not check affected rows and the
handler returns `204`.

The two delete paths use SQL shaped as `DELETE FROM <table> t WHERE ...`. SQLite rejects the table
alias with a syntax error. The release handlers handle only `ErrInvalidOperation`, ignore other
errors, and still return `204`.

Upstream commit `1af4c105` derives IDs from the route, scopes mutations to the project and
integration, uses portable delete SQL, checks affected rows, and propagates remaining errors.

## Automated regression

```bash
eval "$(scripts/app-source.sh ensure | grep '^APP_' | sed 's/^/export /')"
test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local --no-daemon \
  --tests io.bookwright.tests.semaphore.WebhookIntegrationApiTest.integrationChildMutationsPersist
test-environment/profile down core-sqlite-local
```

The regression requires the matcher update to persist and requires the deleted matcher and extracted
value to disappear from their collections. It fails if the historical false-success behavior
returns.

## Historical workaround

Until the fix reaches the deployed release, delete and recreate the whole integration, then verify
its matcher and extracted-value lists through the API before enabling the webhook alias.
