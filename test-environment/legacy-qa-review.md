# Semaphore UI Legacy Test Review

**Date:** 2026-08-19
**Source:** upstream `semaphore/test/` at commit `ae12f3acac626f78673b95cc57acd62ed873b089`

## Decision

The old code is not ported into Bookwright as a ready-made implementation. The scenarios are kept as a source of requirements and are gradually rewritten at the appropriate level: API for business rules and lifecycle, UI only for critical user paths and client-side validation.

## Playwright `test/e2e`

The suite contains five tests: a successful task run, stop in the waiting/cloning/running states, and rejection of a Variable Group with an empty name.

Reasons not to use the implementation directly:

- `package.json` contains no run command;
- `baseURL` is hardcoded to `http://localhost:8080`, whereas the reproducible test bench runs on `3000`;
- the tests create an external demo project and depend on its contents;
- expectations are tied to the English UI and to specific lines of Ansible output;
- `afterEach` assumes an open dialog, so the original failure may be hidden by a cleanup failure;
- three workers run stateful UI scenarios in parallel without proven isolation;
- trace, video, and screenshots on failure are disabled;
- the `role` fixture does not actually assign the selected role: the corresponding line is commented out.

What we keep:

| Legacy scenario | Current coverage | Decision |
|---|---|---|
| Task success via UI | Covered | A short UI smoke launches an API-prepared executable template without the demo project and confirms success via the API |
| Stop while waiting | Covered via queue/capacity | Waiting admission and dequeue are verified; a separate UI stop is not needed |
| Stop while cloning | Clone failure and stop running exist, but not their intersection | Backlog task lifecycle |
| Stop while running | Covered by regular stop and force-stop | The old code is not needed |
| Variable Group with empty key | Covered by API | Keep a future UI validation check |

## Manual test cases

Legend: **covered** — the contract is already protected by automation; **partial** — the core is protected, but not the entire original scenario; **backlog** — a useful gap; **external** — requires separate infrastructure or a service.

| ID | Area | Status | Decision |
|---|---|---|---|
| TC-001 | Admin login | Covered | API login and an independent browser password-login smoke pass |
| TC-002 | Invalid login / brute force | Covered with a security gap | Existing/unknown/empty credentials do not create a session and do not disclose the account; five repeated attempts remain without throttle or warning |
| TC-003 | TOTP | Covered | API and browser enrollment/challenge/recovery pass with a controlled RFC 6238 secret |
| TC-004 | User lifecycle | Covered within API boundaries | Create/update/delete/absence/recreate are automated; deactivate/reactivate is absent from the current router and user model |
| TC-005 | API token | Covered | Create/list, expiry validation, Bearer access, revoke, and protection of token material are automated |
| TC-006 | Project create | Covered | Do not duplicate |
| TC-007 | Max parallel tasks | Covered | Limits 1→2, waiting admission, slot release, and concurrent running are automated |
| TC-008 | Backup/restore | Covered with a defect | Round trip, relinking, execution, and negative paths are automated; duplicate-name validation has a separate canary |
| TC-009 | Delete project dependencies | Covered with a defect | After `stopped`, cascading deletion succeeds; while `running`, the API incorrectly returns `204`, the executor keeps working and causes FK errors |
| TC-010 | SSH Git repository | Covered | Local SSH Git fixture, negative auth, and rotation are automated |
| TC-011 | HTTPS token repository | Covered | A local private HTTPS remote verifies trusted TLS, Basic Auth, execution, negative auth, and masking |
| TC-012 | SSH inventory key | Covered | The same SSH fixture confirms remote Ansible execution |
| TC-013 | Login/password key | Covered | Usage and absence of plaintext are verified |
| TC-014 | Vault storage | External | Feature profile with a Vault dev server |
| TC-015 | Static inventory | Covered | INI `static` and YAML `static-yaml` are persisted; the template `limit` selects one group, and the host of the second group is not executed |
| TC-016 | File inventory | Covered with a defect | A repository-backed file is actually executed; create lets traversal through, while update rejects it with an empty `400` |
| TC-017 | Terraform inventory | Covered | Plan-only Terraform/OpenTofu use the selected workspace inventories on a local module without provider downloads |
| TC-018 | Variable Groups mixed | Covered | JSON/ENV/secret var+env, rename, masking, and task execution are automated |
| TC-019 | TF_VAR secrets | Covered | A secret of type `env` actually becomes a Terraform/OpenTofu input variable; a SHA-256 marker confirms injection without plaintext in API/output/Allure |
| TC-020 | Ansible template execution | Covered | Do not duplicate |
| TC-021 | Build/deploy chain | Covered with a clarification | Manual selection of a successful build, `build_task_id`, nested history version, and target/incoming executor env are automated; a `version` of its own exists only on the build task |
| TC-022 | Survey variables | Covered by API with a defect | Enum/int/string/env/secret metadata, persistence, local execution, and backend target validation; `v2.19.8` loses the secret on remote dispatch; UI widgets/required remain a browser check |
| TC-023 | Task overrides | Covered by API | Launch values, template/task arguments, and Ansible limit/tags/skip-tags/diff/skip-galaxy are actually executed |
| TC-024 | Stop task | Covered | Regular stop and force-stop are made deterministic by a marker |
| TC-025 | Cron schedule | Partial | CRUD/validation/toggle added; real fire and DST to be moved to the slow profile |
| TC-026 | Run-at schedule | Partial | Payload/validation added; fire/delete-after-run to be moved to the slow profile |
| TC-027 | Runner registration | Partial | Registration/status/heartbeat are covered; offline recovery reproduces `error` instead of the expected waiting |
| TC-028 | Runner tags | Covered with a defect | Exact tag and used_runner_id pass, busy runner requeue works; unavailable/unmatched tag ends in error |
| TC-029 | GitHub integration | External | Needs a webhook receiver and a controlled GitHub event fixture |
| TC-030 | Task Runner RBAC | Covered | Permission mask and forbidden mutations are verified |

## MCP plans

`test/mcp/api` and `test/mcp/e2e` are instructions for an interactive agent run, not deterministic regression tests. They launch an external `cursor-agent`, use demo data, and in one case execute Bash from the full upstream repository. We do not port them to CI. The useful ideas — project update, build/deploy chain, and user lifecycle — are already reflected in the backlog above.

## Migration order

1. Schedules contract and validation — current implementation.
2. Local SSH Git/inventory fixture without external network access.
3. Variable Groups, survey variables, and launch-time overrides — done at the API level.
4. Queue/max parallel and runner tags — done; unavailable runner recovery and loss of the survey secret on remote dispatch are captured by separate reproducers/canaries.
5. Minimal UI smoke — done: password login, task launch, and client-side project-name validation without a POST.
6. Separate feature profiles for Vault, Terraform, and webhook integration.
