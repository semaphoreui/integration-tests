# API Map for the First Automation

**Test environment version:** Semaphore UI `v2.19.8`
**Sources:** `api-docs.yml`, `api/router.go`, read-only requests against the local test environment

## Big Picture

`api-docs.yml` describes 77 paths and 127 operations:

| Method | Count |
|---|---:|
| GET | 55 |
| POST | 34 |
| PUT | 16 |
| DELETE | 22 |

The specification covers the core product model but does not fully match the actual router. Therefore, generating tests directly from OpenAPI cannot yet be considered reliable without additional verification of routes and schemas.

## End-to-End Scenario

| Step | Main operations | Expected codes | What to verify |
|---|---|---|---|
| Health | `GET /api/ping` | 200 | Availability and the exact `pong` body |
| Login | `GET`, `POST /api/auth/login` | 200, 204 | Login metadata, valid and invalid credentials, cookie session |
| API tokens | `GET/POST /api/user/tokens`, `DELETE /api/user/tokens/{prefix}` | 200/201/204 | Bearer auth, prefix-only listing, expiry, revoke, and no plaintext in diagnostics |
| Users | `POST /api/users`, `GET/PUT/DELETE /api/users/{id}` | 200/201/204 | Create/update/delete/recreate; deactivate/reactivate is absent from the current model |
| Projects | `GET`, `POST /api/projects` | 200, 201 | Creating an isolated project, name being required, data uniqueness |
| Project role | `GET /api/project/{project_id}/role` | 200 | Role and permissions of the current user |
| Keys | CRUD `/api/project/{project_id}/keys` | 200/201/204 | Types `none`, `ssh`, `login_password`, secret hiding, refs, and deletion |
| Repositories | CRUD `/api/project/{project_id}/repositories` | 200/201/204 | Git URL, branch/ref, access key, branches, playbooks, clone errors |
| Inventory | CRUD `/api/project/{project_id}/inventory` | 200/201/204 | `static`, `static-yaml`, `file`, links to key/repository, validation |
| Templates | CRUD `/api/project/{project_id}/templates` | 200/201/204 | Links to repository/inventory/key, playbook, arguments, survey variables |
| Tasks | `POST /tasks`, `GET /tasks/{id}` | 201, 200 | Queue and lifecycle, launch parameters, final status |
| Task output | `GET /tasks/{id}/output`, `/raw_output` | 200 | Structured and raw output, absence of secrets |
| Stop task | `POST /tasks/{id}/stop` | 204 | Regular and forced stop |
| Schedules | CRUD `/api/project/{project_id}/schedules` | 200/201/204 | cron, `run_at`, active, timezone/DST, task params |
| Project users | CRUD `/api/project/{project_id}/users` | 200/204 | Roles owner/manager/task_runner/guest and project isolation |
| Global runners | `GET/PUT /api/runners/{id}`, `GET /api/runner_tags` | 200/204 | Registration, active/default, heartbeat, tags, capacity, and routing |
| Integrations | CRUD `/api/project/{project_id}/integrations`, aliases, matchers, values; `POST /api/integrations/{alias}` | 200/201/204 | Token auth, matcher routing, body/header extraction, link to task, and safe rejection |
| Project backup | `GET /api/project/{project_id}/backup`, `POST /api/projects/restore` | 200 | Transfer of resource links, absence of task history and authentication secrets, executability of the restored template |
| Cleanup | DELETE of created resources and the project | 204 | Deletion in reverse order and absence of leftover data |

## Resource Dependencies

```text
Project
├── Access Key
├── Repository ──> Access Key
├── Inventory ───> Repository + Access/Become Keys
├── Variable Group
└── Task Template ──> Repository + Inventory + Variable Group
    ├── Task ──> Status + Output
    ├── Schedule ──> Cron/Run-at + Task Parameters
    └── Integration ──> Auth Key + Matcher + Extracted Values + Public Alias
        └── Webhook ──> Task + Extracted Ansible Variables
```

This defines the order in which test data is created and the reverse order of cleanup.

## Confirmed Read-Only Checks

| Endpoint | Result |
|---|---|
| `/api/project/1/role` | 200 |
| `/api/project/1/users` | 200 |
| `/api/project/1/keys` | 200 |
| `/api/project/1/repositories` | 200 |
| `/api/project/1/inventory` | 200 |
| `/api/project/1/templates` | 200 |
| `/api/project/1/schedules` | 200 |
| `/api/project/1/tasks` | 200 |
| `/api/project/1/tasks/1` | 200 |
| `/api/project/1/tasks/1/output` | 200 |
| `/api/project/1/tasks/1/raw_output` | 200 |

## Discrepancies Found Between Documentation and Implementation

### 1. `GET /project/{project_id}/schedules` is not documented

In `api-docs.yml`, only `POST` is described for the collection endpoint, but `api/router.go` also registers `GET` and `HEAD`. A live `GET /api/project/1/schedules` request returns 200.

The discrepancy is confirmed by an automated smoke test: an inactive cron schedule is successfully created, read by ID, and found via the collection `GET`.

### 2. Query parameters `sort` and `order` are incorrectly marked as required

In OpenAPI they have `required: true` for users, keys, repositories, inventory, and templates. Live requests without these parameters return 200. For clients generated from the specification, this creates an unnecessary restriction.

### 3. Request schemas do not define required fields

`Login`, `ProjectRequest`, `AccessKeyRequest`, `RepositoryRequest`, `InventoryRequest`, `TemplateRequest`, and `ScheduleRequest` lack `required` arrays. According to the specification, almost any empty body looks valid, even though the implementation expects specific fields.

### 4. The router contains additional important operations

The implementation has routes that are missing from, or incompletely represented in, the current OpenAPI sample:

- task confirmation and rejection;
- task stages and Ansible hosts/errors;
- repository branches, playbooks, and refs;
- resource refs before deletion;
- cron validation;
- schedule activation;
- template permissions;
- project roles;
- secret storages;
- project cache cleanup.

Before contract testing, the router and OpenAPI must be compared automatically in full.

### 5. Access key sensitive fields are not returned in plaintext

An automated security smoke test for a `login_password` key confirms that the unique password marker is absent from create/get/list responses. The key is used as an inventory credential during a real run of a local Ansible task; the marker is also absent from structured output, raw output, Allure, and JUnit artifacts.

## First Wave of API Tests

### P0 — mandatory smoke

1. Health and valid/invalid login.
2. Creating and reading a project.
3. Creating a key, repository, inventory, and template.
4. Launching a task and waiting for a terminal status.
5. Verifying output and the absence of secrets.
6. Deleting the created data.

### P1 — main risks

1. API tokens: Bearer auth, expiry, revoke, and protection of token material.
2. Git branch/ref and clone errors.
3. Extended SSH and login/password access keys: rotation, known_hosts, and custom SSH config.
4. Schedule: cron, run-at, timezone/DST, and task parameters.
5. RBAC for manager, task_runner, and guest.
6. Denial of access to another project's resources.
7. Stop/force stop, project `max_parallel_tasks`, and parallel task launches.
8. Variable Groups: mixed JSON/ENV/secret values, rename persistence, and safe task execution.
9. Survey variables and launch-time overrides: metadata, persistence, secret masking, arguments, and Ansible params.

### P2 — extension

1. Secret storage. External storage management is a Pro feature; the Community API reports a disabled feature flag and does not allow an honest Vault/OpenBao/AWS/Azure scenario without a test subscription.
2. Integrations and webhooks. Token auth, project alias routing, header matcher, body/header extraction, and real task execution are automated; HMAC/GitHub/Bitbucket/Basic auth remain an extension.
3. Runners. Registration/default/heartbeat, exact tag routing, and capacity are automated; unavailable recovery and one-off remain known defects.
4. Workflows. DAG execution is a Pro feature: the Community controller is a documented stub, so e2e is postponed until a test subscription is available.
5. Backup/restore and migration scenarios. The project backup/restore round trip is automated; the SQLite/PostgreSQL release upgrade is covered separately.

## Current Status and Upcoming Extension

The executable API smoke test is implemented on Bookwright v1.4.0: it creates isolated resources without fixed IDs, uses deterministic typed fixtures, performs LIFO cleanup, and protects diagnostics from secrets.

The API-token P1 is covered by a separate domain client/steps: creation with a future expiry, prefix-only list, Bearer-authenticated user read, and project creation pass; revoke immediately yields `401`, and a past expiry is rejected with `400`. The creation response containing the plaintext token is hidden from HTTP/Allure diagnostics, and Authorization is redacted. The supported local-user lifecycle create/update/delete/recreate is also automated; deactivate/reactivate is absent from the current router and `db.User`, so it is not claimed as an available feature.

Password login security covers the account-enumeration boundary: an existing user with a wrong password and
an unknown user receive the same `401` with an empty body, no invalid path creates a session cookie,
and an empty password is rejected. Five retries on `v2.19.8` remain without `429`, `Retry-After`, or a warning;
the canary and source boundary are in `password-login-brute-force-protection-gap.md`.

Of the P1 Git risks, the following are automated: a successful run from an explicitly selected branch, a missing ref, and an unreachable authenticated HTTPS remote. Errors bring the task to the expected `error` status, preserve useful Git diagnostics, and do not expose the login/password in structured or raw output.

A successful SSH repository/access key is automated in a separate `feature-ssh-local`: the encrypted key is used for Git clone and Ansible SSH inventory, and the remote output is confirmed by a marker. The negative scenario with an invalid key verifies a diagnosable failure. The rotation scenario uses two SSH fixtures with different authorized keys: the old secret is rejected on the second server, `PUT /keys/{id}` replaces the secret without changing the key ID, after which Git clone and Ansible SSH succeed. Private keys/passphrases are absent from API responses, HTTP/Allure diagnostics, and structured and raw task output.

A successful private HTTPS clone is automated in `feature-git-https`: a pinned NGINX serves a bare Git repository over self-signed TLS and requires Basic Auth. Semaphore trusts only the generated CA via `SEMAPHORE_FORWARDED_ENV_VARS`, executes the playbook after an authenticated clone, and a request without an access key receives a diagnosable failure. The password is verified to be absent from the create/get/list API and HTTP/Allure diagnostics; the login and password are absent from structured and raw task output.

The static inventory P1 is extended with a multi-group scenario for INI `static` and YAML `static-yaml`: the API
persists the two formats with different host aliases, the templates contain a default `limit`, and the task output
proves execution on the selected hosts and the absence of hosts from the second groups.

The repository-backed file inventory P1 is also automated: `type=file` persists `repository_id`, reads `inventories/localhost.ini` from a trusted Git fixture, and actually executes the playbook on the expected group. A validation defect was found in `v2.19.8`: create accepts a path containing `../`, while an update of the same object returns an empty `400`. The canary and source boundary are described in `file-inventory-path-validation-defect.md`; the unsafe inventory is not executed.

Workspace inventories are verified by real plan execution on the toolchain from the release image:
`terraform-workspace` selects a separate workspace in Terraform 1.11.3, and `tofu-workspace` does so in
OpenTofu 1.11.0. The same provider-free module receives a Variable Group secret of type `env` via
`TF_VAR_bookwright_secret`, compares it with the passed SHA-256, and prints only a safe marker.
The test verifies the create/get/list API, structured/raw output, and Allure for the absence of plaintext. The scenario
runs once on `core-sqlite-local` instead of being duplicated across the DB matrix.

The Build → Deploy chain is verified through the same project/template/task endpoints. The build template persists
`start_version`, the first successful task receives that version, and the build-template history endpoint returns
it as an available option for a manual deploy. The deploy request contains the selected `build_task_id`;
the detail API persists this link, the task history contains a nested `build_task` with the version, and the executor
receives the identical value via `SEMAPHORE_TASK_INCOMING_VERSION`. This refines the old expectation of
TC-021: the `version` field belongs to the build task, not to the deploy task itself.

For the built-in `manager` and `task_runner` roles, the exact permission bitmasks and behavioral boundaries are automated. Both roles can launch tasks; the manager can manage resources but not the project or its members; the task runner cannot modify resources, the project, or its members.

Regular stop and force-stop are automated on a long-running Ansible fixture. The request is sent after the marker of the actual playbook start; then the terminal `stopped` status and the absence of the step marker after the pause are verified.

Project deletion is now verified at the same lifecycle boundary. After terminal `stopped`, the API
deletes the project and its related resources, and detail/list confirm their absence. When deleting during
`running`, version `v2.19.8` returns `204`, even though the executor continues the playbook and, after the rows are deleted,
logs foreign-key errors. The canary and the analysis of the controller/service/store boundary are in
`project-deletion-running-task-defect.md`.

A persistent remote runner is automated as a separate API group with a production-like PostgreSQL profile. Registration, `active`, `is_default`, `online`, heartbeat, and execution of the task suite outside the server process are verified. The exact tag is persisted and selects the expected `used_runner_id`; with capacity `1`, the second matching task returns to `waiting` and starts after the slot is freed. When no matching active runner is present, `v2.19.8` finishes the task with `error: no runners available` instead of a recoverable waiting; the evidence and code boundary are in `runner-unavailable-routing-defect.md`. Secret survey variables are also lost at the remote dispatch boundary; the canary and upstream #4086 are described in `remote-runner-survey-secrets-defect.md`.

Project concurrency is covered independently of runner capacity: a parallel-capable template with a project limit of `1` holds the second task in `waiting`, and after a stop the first one frees the slot; updating the project to `2` allows two tasks to reach the running marker simultaneously. This protects create/update persistence and queue admission without a timing assumption about the moment of the POST.

The schedule P1 is extended with a separate API set: backend cron validation, diagnosable errors for invalid cron/type/run-at, CRUD/update, active toggle, persistence of `run_at`, `delete_after_run`, and task parameters, creation denied for `task_runner`, as well as the system timezone contract. Real cron and `run_at` execution is covered by a separate `feature-schedule-timezone`, but on `v2.19.8` both scenarios reproduce the absence of an automatically created task. Until confirmed on Linux, the profile is left out of the CI matrix; the evidence is collected in `schedule-execution-defect.md`.

Variable Groups are covered by a separate API set: create/get/list, mixed JSON extra vars and ENV, secrets of types `var`/`env`, renaming a secret while preserving its value, backend validation of an empty name, and real Ansible execution. Secrets are verified inside the playbook by SHA-256 under `no_log` and are absent from API responses, structured/raw output, and Allure diagnostics. The set is green on SQLite and PostgreSQL `v2.19.8`; API persistence for scenario #2293 works, while the browser payload remains a separate UI check.

The survey/task override API set persists enum/int/string/env/secret definitions and executes a task with a launch environment/secret, template/task arguments, and Ansible params on SQLite and PostgreSQL local execution. The persisted template/task payloads, the real marker, and the absence of the survey secret in structured/raw output are verified. On the persistent runner `v2.19.8`, the positive path is replaced by a known-defect canary: the secret does not reach the executor; fix #4086 is already in `v2.20.0-alpha1`. An unsupported target is rejected with `400`. `v2.19.8` also accepts an enum default outside of the values; the defect and upstream fix `eb29c3e8` are described in `survey-default-validation-defect.md`.

Webhook integrations are covered by a separate domain API and steps. The end-to-end scenario creates a token-authenticated searchable integration, a shared project alias, a header matcher, and two extractors for the JSON body/header. An invalid token and a non-matching matcher return a public `204` without task headers; a valid webhook creates a task, returns `X-Semaphore-*` identifiers, persists `integration_id`, and passes the extracted values into Ansible variables. The access-key secret remains masked in the API and HTTP/Allure diagnostics.

Project backup/restore is covered by a separate domain API and steps. The round trip exports a project with access keys, repository, inventory, template, schedule, and an already executed task, verifies the absence of plaintext login/password in the JSON, changes only the project name, and restores the configuration. The new resource IDs and all references between them are verified, task history is not transferred, and the restored template successfully executes a trusted playbook.

The negative restore contract verifies `401` for a non-admin and `400` for a missing repository reference. A separate canary records a `v2.19.8` defect: a backup with two identical repository names is accepted with `200` because of the `n > 2` condition in the shared duplicate validator; the restored project indeed contains both objects. Details are in `project-backup-restore-validation-defect.md`.

The schedule execution defect is confirmed in Linux CI: `v2.19.8` creates no task for either an active cron or `run_at`. SSH key rotation is automated. Strict `known_hosts` remains a version-gated scenario: the corresponding configuration is absent from `v2.19.8` and must be added after moving to a release that contains the current upstream implementation.
