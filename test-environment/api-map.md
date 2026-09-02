# API map for the first automation

**Stand version:** Semaphore UI `v2.19.7`  
**Sources:** `api-docs.yml`, `api/router.go`, read-only requests to the local stand

## Big picture

`api-docs.yml` describes 77 paths and 127 operations:

| Method | Count |
|---|---:|
| GET | 55 |
| POST | 34 |
| PUT | 16 |
| DELETE | 22 |

The specification covers the product's core model but does not fully match the actual router. Therefore generating tests directly from OpenAPI cannot yet be considered reliable without additional verification of routes and schemas.

## End-to-end scenario

| Step | Main operations | Expected codes | What to verify |
|---|---|---|---|
| Health | `GET /api/ping` | 200 | Availability and the exact body `pong` |
| Login | `GET`, `POST /api/auth/login` | 200, 204 | Login metadata, valid and invalid credentials, cookie session |
| Projects | `GET`, `POST /api/projects` | 200, 201 | Creating an isolated project, name being required, data uniqueness |
| Project role | `GET /api/project/{project_id}/role` | 200 | Role and permissions of the current user |
| Keys | CRUD `/api/project/{project_id}/keys` | 200/201/204 | Types `none`, `ssh`, `login_password`, secret hiding, refs, and deletion |
| Repositories | CRUD `/api/project/{project_id}/repositories` | 200/201/204 | Git URL, branch/ref, access key, branches, playbooks, clone errors |
| Inventory | CRUD `/api/project/{project_id}/inventory` | 200/201/204 | `static`, `static-yaml`, `file`, links to key/repository, validation |
| Templates | CRUD `/api/project/{project_id}/templates` | 200/201/204 | Repository/inventory/key links, playbook, arguments, survey variables |
| Tasks | `POST /tasks`, `GET /tasks/{id}` | 201, 200 | Queue and lifecycle, launch parameters, final status |
| Task output | `GET /tasks/{id}/output`, `/raw_output` | 200 | Structured and raw output, absence of secrets |
| Stop task | `POST /tasks/{id}/stop` | 204 | Regular and forced stop |
| Schedules | CRUD `/api/project/{project_id}/schedules` | 200/201/204 | cron, `run_at`, active, timezone/DST, task params |
| Project users | CRUD `/api/project/{project_id}/users` | 200/204 | Roles owner/manager/task_runner/guest and project isolation |
| Global runners | `GET /api/runners` | 200 | Registration, active/default flags, online status, and heartbeat |
| Cleanup | DELETE of the created resources and project | 204 | Deletion in reverse order and no leftover data |

## Resource dependencies

```text
Project
├── Access Key
├── Repository ──> Access Key
├── Inventory ───> Repository + Access/Become Keys
├── Variable Group
└── Task Template ──> Repository + Inventory + Variable Group
    ├── Task ──> Status + Output
    └── Schedule ──> Cron/Run-at + Task Parameters
```

This defines the order of test data creation and the reverse order of cleanup.

## Confirmed read-only checks

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

## Discovered discrepancies between documentation and implementation

### 1. `GET /project/{project_id}/schedules` is not documented

In `api-docs.yml`, only `POST` is described for the collection endpoint, but `api/router.go` also registers `GET` and `HEAD`. A live request `GET /api/project/1/schedules` returns 200.

The discrepancy is confirmed by the automated smoke: an inactive cron schedule is successfully created, read by ID, and found via the collection `GET`.

### 2. The `sort` and `order` query parameters are incorrectly marked as required

In the OpenAPI they have `required: true` for users, keys, repositories, inventory, and templates. Live requests without these parameters return 200. For clients generated from the specification this creates an unnecessary constraint.

### 3. Request schemas do not define required fields

`Login`, `ProjectRequest`, `AccessKeyRequest`, `RepositoryRequest`, `InventoryRequest`, `TemplateRequest`, and `ScheduleRequest` lack `required` arrays. According to the specification, almost any empty body looks acceptable, although the implementation expects specific fields.

### 4. The router contains additional important operations

The implementation has routes that are missing or incompletely represented in the current OpenAPI sample:

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

Before contract testing, the router and the OpenAPI must be compared automatically in full.

### 5. Access key sensitive fields are not returned in plaintext

The automated security smoke for a `login_password` key confirms that the unique password marker is absent from the create/get/list responses. The key is used as an inventory credential during a real run of a local Ansible task; the marker is also absent from the structured output, raw output, Allure, and JUnit artifacts.

## First wave of API tests

### P0 — mandatory smoke

1. Health and valid/invalid login.
2. Project creation and reading.
3. Creation of a key, repository, inventory, and template.
4. Task launch and waiting for a terminal status.
5. Output verification and absence of secrets.
6. Deletion of the created data.

### P1 — main risks

1. Git branch/ref and clone errors.
2. SSH and login/password access keys.
3. Schedule: cron, run-at, timezone/DST, and task parameters.
4. RBAC for manager, task_runner, and guest.
5. Denial of access to another project's resources.
6. Stop/force stop and parallel task launches.

### P2 — extension

1. Variable Groups and secret storage.
2. Integrations and webhooks.
3. Runners. The basic persistent remote runner is automated; disconnect/reconnect, capacity, and one-off modes remain.
4. Workflows.
5. Backup/restore and migration scenarios.

## Current status and nearest extension

The executable API smoke is implemented on Bookwright v1.4.0: it creates isolated resources without fixed IDs, uses deterministic typed fixtures, performs LIFO cleanup, and protects diagnostics from secrets.

Of the P1 Git risks, the following are automated: a successful launch from an explicitly selected branch, a missing ref, and an unreachable authenticated HTTPS remote. Failures bring the task to the expected `error` status, preserve useful Git diagnostics, and do not expose the login/password in the structured or raw output.

For the built-in `manager` and `task_runner` roles, the exact permission bitmasks and behavioral boundaries are automated. Both roles can run tasks; a manager can manage resources but not the project or its members; a task runner cannot modify resources, the project, or its members.

Regular stop and force-stop are automated on the long-running Ansible fixture. The request is sent after the marker of the playbook's actual start; then the terminal `stopped` status and the absence of the post-pause step marker are verified.

The persistent remote runner is automated with a dedicated API group and the production-like PostgreSQL profile. Registration, `active`, `is_default`, `online`, heartbeat, and execution of the entire existing task suite outside the server process are verified. A discovered configuration contract: a global runner does not become default automatically after auto-registration, and tasks without a tag pick only `is_default=true` runners.

The next P1 extension: SSH key and cron validation/timezone.
