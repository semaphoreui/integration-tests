# Defect: survey enum accepts a default outside its allowed values

## Summary

Semaphore UI `v2.19.8` accepts a task template whose enum survey variable has a
`default_value` that is absent from its `values` list. The create request returns `201` and the
invalid definition is persisted, even though the launch form cannot represent that default as a
valid enum choice.

The executable stable suite checks a different server-side rule—unsupported survey targets are
rejected—while this version-specific defect remains documented separately.

## Environment

- Semaphore UI image: `semaphoreui/semaphore:v2.19.8`;
- database: SQLite;
- execution mode: local;
- profile: `core-sqlite-local`;
- reproduced: 2026-08-20.

## Steps to reproduce

1. Create a valid project, repository and inventory.
2. Send `POST /api/project/{project_id}/templates` with an enum survey variable:

   ```json
   {
     "name": "deployment_env",
     "type": "enum",
     "values": [
       {"name": "Development", "value": "dev"},
       {"name": "Production", "value": "prod"}
     ],
     "default_value": "qa"
   }
   ```

3. Read the created template through `GET /api/project/{project_id}/templates/{template_id}`.

## Expected result

Template creation returns `400` with a validation message explaining that `qa` is not in the
allowed values list.

## Actual result

Template creation returns `201`; `default_value: qa` is persisted.

## Impact

An invalid template can be created through the API or an outdated client. The launch UI receives
an enum default that does not correspond to any option, so default selection and task parameters
can diverge. Validation occurs too late—or only in the browser—instead of at the persistence
boundary.

## Upstream status

The release source for `v2.19.8` validates survey targets but does not validate compatibility
between type, values and default. Upstream commit
[`eb29c3e8`](https://github.com/semaphoreui/semaphore/commit/eb29c3e802df4890dc803709954dc373ae8968b2)
adds `ValidateSurveyVar` and backend tests, including rejection of enum defaults outside the values
list. The commit is contained in `v2.20.0-alpha1`, so the regression check should switch to the
expected `400` contract when the test matrix advances to that release line.
