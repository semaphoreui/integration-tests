# Semaphore UI Testing Development Plan

**Project:** [semaphoreui/semaphore](https://github.com/semaphoreui/semaphore)  
**Working format:** analysis of the current state, creation and continuous development of automated testing by a single project owner.

---

## Goal

Go beyond an audit and recommendations, and launch testing as a working engineering system:

- understand the real problems of the product;
- define risk-based priorities;
- obtain a reproducible test environment;
- choose a practical stack;
- implement the first useful tests;
- connect them to CI;
- keep documentation, tests, and CI up to date as the product evolves.

The outcome should be not only a strategy, but also a repository with a clear structure, working tests, and a simple way to run them locally and in CI.

---

## Principles

1. **Start with the product's pain points, not with the tool.** First determine where and why defects occur, then choose the tests and technologies.
2. **Critical scenarios matter more than coverage percentage.** First and foremost, protect authentication, access rights, secrets, task execution, repository handling, and upgrades.
3. **API is the primary starting level.** API tests are usually faster and more stable than UI e2e, while still allowing business logic and access rights to be verified directly.
4. **UI e2e is used selectively.** Through the browser we cover only critical user paths and whatever cannot be sufficiently verified at a lower level.
5. **Everything must be maintainable by a single engineer.** Running tests, diagnosing failures, and adding a new test must not require a dedicated infrastructure team.
6. **Maintainability is built in from day one.** Decisions, run commands, limitations, and known problems are documented as work proceeds, so that the project can be confidently picked up again after a pause.

---

## Stage 1. Research and Prioritization

This is the starting stage. Its task is to gather the facts on which the test strategy and the first implementation will be built.

### 1.1. Analysis of open and closed issues

I review open and closed issues, primarily those labeled `bug`, `regression`, `security`, and problems related to releases.

What needs to be determined:

- which parts of the product break most often;
- which defects recur;
- which problems cause the greatest damage to users;
- which scenarios are hard to reproduce manually;
- where missing automation slows down releases or bug fixes;
- which defects have already been fixed but may come back.

Issues are grouped by functional area:

- authentication and user management;
- RBAC and cross-project access;
- task creation and execution;
- runners, queue, and parallel execution;
- secrets, keys, and Variable Groups;
- Git repositories and integrations;
- inventory and environments;
- schedules;
- migrations and different DBMSs;
- UI;
- installation and upgrade.

An initial pass has been completed over all 2,192 issues: 865 open and 1,327 closed. For each record, the user complaint, symptom, fix status, available evidence, and the main system component were recorded. The full register is stored in `outputs/issues-assessment/semaphore-issues-register.xlsx`.

#### Issue analysis results

The highest concentration of reports and explicitly labeled defects was found in the following components:

| Component | Total issues | Issues with label `bug` | Open | Issues with label `critical` |
|---|---:|---:|---:|---:|
| Git repositories | 450 | 68 | 152 | 17 |
| Authentication and sessions | 295 | 50 | 126 | 17 |
| Keys, secrets, and Variable Groups | 208 | 22 | 89 | 10 |
| Schedules and time | 164 | 23 | 88 | 12 |
| Task output and events | 137 | 24 | 52 | 5 |
| Task templates | 127 | 18 | 66 | 5 |

Main conclusions:

- **Git repositories are the largest problem area.** The most characteristic risks: clone/pull, SSH and HTTPS access, branches and refs, playbook paths, and the Git cache.
- **Authentication is one of the riskiest areas.** It concentrates login, API tokens, LDAP, OIDC, TOTP, sessions, and related access checks.
- **Secrets form a critical link with repositories and task execution.** Errors in access keys, Vault, Variable Groups, encryption, or secret passing block the end-to-end user scenario.
- **Schedules have a large unresolved backlog:** 88 of 164 issues are open (53.7%). Special attention is required for cron, timezone/DST, one-off runs, and parameter passing.
- **Templates also remain a problem area:** 66 of 127 issues are open (52%). The main risks lie at the intersection of launch parameters, survey variables, inventory, repositories, and secrets.

The number of issues is not equal to the number of confirmed defects: the sample includes feature requests, questions, and reports without the `bug` label. Therefore, priority is determined by a combination of frequency, criticality, and impact on the main user flow.

#### First automation priority

1. User login and obtaining an API token.
2. Creating a project and configuring a Git repository.
3. Creating an access key and verifying safe handling of secrets.
4. Creating an inventory and a task template, running a task.
5. Verifying the task lifecycle, final status, and output.
6. Creating a schedule with cron, timezone, and launch parameters.
7. Negative RBAC and project isolation checks.

This end-to-end flow covers the main user actions and at the same time touches the five most problematic components.

**Result:** a pain-point map and a preliminary list of critical scenarios with an explanation of priorities.

### 1.2. Local test environment

I bring up Semaphore locally and record a reproducible path from cloning the repository to a running service.

**Current status:** release profiles have been moved to `v2.19.12`
(`012ed06d3eccadaed594c73b93b3d8a2459b576f`), and the upgrade path to
`v2.19.8 → v2.19.12`. The new Linux baseline was fully confirmed on 2026-09-04: all 11
configuration matrix profiles are green, as are the PR gate with SQLite/UI and the SQLite/PostgreSQL upgrade profiles. At the same time,
Linux CI confirmed that `v2.19.12` loses one of the short `stdout`/`stderr` streams after the terminal `success`;
the strict `feature-shell-output` keeps the reproduction separate from the stable gate. `feature-schedule-timezone` reproduces
the absence of tasks for an active cron/`run_at`, and `feature-dynamic-runner` reproduces a one-off
runner that never exits after a successful task and the `finish` webhook. All three defect profiles are excluded from the stable CI
matrix. The reports are located in `test-environment/v2.19.8-regression-report.md`,
`test-environment/schedule-execution-defect.md`, and
`test-environment/dynamic-runner-one-off-exit-defect.md`, and the new release defect is in
`test-environment/shell-output-loss-defect.md`.

The Java framework Bookwright v1.4.0 was chosen for automation. Gradle/JUnit 5, Retrofit/OkHttp, Guice, Allure, Playwright, deterministic typed fixtures, target/domain API and steps, architecture self-tests, typed precondition state, and LIFO cleanup have been adapted into the test repository. The end-to-end Java API smoke successfully verifies health, login, the project and the owner role, a local Git fixture, inventory, template, Ansible task execution, output, an inactive cron schedule, guest access to an assigned project, denial of access-key changes for a guest, and isolation of an unassigned project. A separate security smoke creates a `login_password` key, actually uses the password during task execution, and confirms the absence of plaintext in API responses, structured/raw task output, and test artifacts. The Variable Group suite verifies mixed JSON/ENV/secret values, secret rename, backend validation, and real Ansible execution without plaintext; SQLite and PostgreSQL `v2.19.8` are green. The survey/task override suite on SQLite and PostgreSQL local execution verifies persistence of enum/int/string/env/secret definitions, launching with template/task arguments and Ansible params, real use of the values, and the absence of the survey secret in the output. On the persistent runner `v2.19.8`, the secret is lost before dispatch; this was confirmed by a safe canary and fixed upstream in #4086 (`081425d2`) in `v2.20.0-alpha1`. A version-specific gap was also found: `v2.19.8` accepts an enum default outside the allowed values; the upstream fix `eb29c3e8` is included in `v2.20.0-alpha1`. The concurrency suite proves project queue admission with limits 1→2 on a parallel-capable template. The persistent runner suite confirms exact tags, a persisted used_runner_id, and capacity requeue; the absence of a matching active runner moves the task to error instead of the expected recoverable waiting and is recorded as a separate defect candidate. The webhook integration suite verifies token auth, a shared project alias, matcher routing, body/header extraction, no launch on an invalid token/event, and real passing of extracted values into the Ansible task linked via `integration_id`. The Git suite verifies task execution from an explicitly selected branch, the expected failure for a missing ref and an unreachable authenticated HTTPS remote, including the absence of credentials in diagnostics. The SSH feature suite verifies Git clone and the Ansible target, negative authorization, secret rotation for an existing key ID across two servers with different authorized keys, and the absence of the private key/passphrase in the API, HTTP reports, and task output. The RBAC suite confirms the permission bitmask and the `manager`/`task_runner` boundaries: permitted task launches, permitted manager resource management, and denials of project/resource/member mutations according to the role. The task lifecycle suite, after a confirmed start of a long-running playbook, verifies a regular stop and a force-stop, the terminal `stopped` status, and that the next step is not executed. Project test data is deleted automatically; the RBAC fixture user is reused between runs due to the restriction on deleting a user with a login session history in Semaphore v2.19.7.

I verify:

- which dependencies are needed;
- which launch method is more convenient for test development;
- how to create users, projects, and test data;
- how to clean up or recreate state;
- how to obtain application and task logs;
- which external dependencies the tests will need;
- whether the environment can be run identically locally and in CI.

The initial configuration matrix and the way to extend it are recorded in `test-environment/configuration-testing-overview.md`. Instead of a full combinatorial sweep, fast configuration checks, several reference end-to-end profiles, and standalone feature profiles are used. Five DB/execution profiles have been implemented, along with SSH, a schedule reproducer, OIDC via pinned Dex, OIDC via HTTPS NGINX/subpath on PostgreSQL, LDAPS via pinned OpenLDAP, TOTP MFA, database encryption keyring rotation, and a dynamic one-off runner reproducer. On `v2.19.8`, the core API suite passes in all five reference configurations; OIDC and LDAP cover positive login, provisioning/reuse, logout, and negative account/provider/credential paths. TOTP covers API self-enrollment, challenge, invalid/valid passcode, recovery, and single-use recovery codes, while the browser flow covers Security settings, QR rendering, the challenge screen, and the recovery form. OTP material does not end up in HTTP/Allure artifacts, and sensitive UI screenshots/HTML/traces are not published on failure. Proxy OIDC additionally confirms TLS, `/semaphore` routing, and the `Secure`/`HttpOnly` session cookie. The encryption lifecycle verifies hot reload of a new primary, mixed-key reads, backup/rekey, and removal of the retired key without losing the task fixture. The dynamic runner executes the task but does not terminate the one-off process due to a confirmed logic error in the runner lifecycle. HA has been investigated and deferred until an Enterprise test subscription: the community build does not contain Redis-backed coordination and cannot provide an honest active-active check.

**Result:** a working test environment and a documented launch command.

### 1.3. API and documentation analysis

I study the application routes, `api-docs.yml`, the actual API behavior, and discrepancies between the implementation and the documentation.

I verify:

- completeness and currency of the API specification;
- authentication methods;
- main CRUD operations and resource lifecycles;
- role checks and resource-to-project ownership checks;
- error contracts and input validation;
- asynchronous operations: task launch, statuses, logs, stop;
- suitability of the API for preparing and cleaning up test data;
- feasibility of contract testing against the specification.

The list of the first API tests is defined together with the pain-point map from the issue analysis. The existence of an endpoint does not by itself make it a priority.

**Result:** an API map, a list of documentation discrepancies, and a prioritized set of scenarios for automation.

### 1.4. Review of the previous QA's tests

I study the existing materials in `test/`, including the Playwright configuration, test cases, and helper files.

For each element I determine:

- what it verifies and whether that matches the current product;
- whether it runs now;
- how stable and diagnosable the test is;
- whether its structure, fixtures, or data can be reused;
- how much restoring it would cost compared to rewriting;
- whether the scenario relates to the established priorities.

The decision is made based on the review results, not in advance. Possible options: keep, fix, use partially as a source of scenarios, or delete after recording the useful information.

**Result:** a brief report on the existing tests and a decision for each useful block.

**Current status:** the review is complete. The old Playwright tests are not being ported as code due to a non-reproducible configuration, fragile UI cleanup, and dependence on the demo project and task output text. Their scenarios, 30 manual test cases, and MCP plans are classified in `test-environment/legacy-qa-review.md`; useful gaps have been moved to the backlog, and API scenarios that are already protected are marked separately.

---

## Stage 2. Strategy and Technical Foundation

Based on the first stage, I define a minimal testing strategy.

### What we define

- critical user and technical scenarios;
- which checks are needed at the Go unit/integration, API, and UI e2e levels;
- the minimal set of test environments;
- the approach to test data and state cleanup;
- rules for handling secrets in tests;
- the way reports are produced and failures are diagnosed;
- what runs on every PR, and what runs on a schedule or before a release;
- criteria by which a test is considered complete and maintainable.

### Tool selection

Priority is given to the stack already used by the project, if it is suitable:

- standard Go tooling for unit and integration tests;
- API tests in a language and framework that are easy for the core team to maintain;
- Playwright for a small number of critical UI scenarios, if the existing configuration is viable;
- GitHub Actions for CI;
- the existing OpenAPI/Dredd infrastructure, after verifying its currency and usefulness.

A new tool is added only when it solves a specific problem better than the existing one and does not create unjustified maintenance cost.

**Result:** a short strategy, a test-level diagram, the chosen stack, and the test project structure.

---

## Stage 3. Implementation of the First Working Version

I create a minimal but complete set of tests that already delivers value and serves as a model for further development.

Preliminary end-to-end scenario:

1. launching a clean test environment;
2. creating or logging in a test user;
3. creating a project;
4. adding a repository, inventory, key, and task template;
5. launching a task;
6. waiting for completion;
7. verifying the status and result;
8. verifying access with a different role or user;
9. cleaning up the created data.

The exact composition is determined after the issue and API analysis. In addition to the positive scenario, the first version must include checks of the most dangerous failures: wrong permissions, someone else's project, invalid input data, an external dependency error, or a leak of sensitive data.

### Requirements for the first version

- one obvious way to run;
- reproducibility on a clean machine;
- independent or safely isolated tests;
- clear fixtures and test data;
- diagnosable error messages;
- no real secrets in the repository or logs;
- reasonable execution time;
- examples from which a new test can be written quickly without re-studying the entire architecture.

**Result:** a working baseline set of API/integration tests and, if necessary, several critical UI e2e tests.

**Current status:** the baseline version has been implemented and extended according to the main risks from the issues: task lifecycle, Git/SSH/private HTTPS Git, static/file/workspace inventories, RBAC, API tokens, the supported user lifecycle, schedules, Variable Groups, survey/overrides, project/runner concurrency and routing, webhook integrations, project backup/restore, secrets, and a critical UI smoke. Password login is protected by regression checks for an identical response for existing/unknown accounts and the absence of a session cookie; `v2.19.8` neither throttles nor audits five repeated failures, which is recorded as a security gap. INI `static` and YAML `static-yaml` multi-group inventories are persisted and actually execute only the template's selected `limit` group. Terraform 1.11.3 and OpenTofu 1.11.0 from the release image execute a plan-only local module in the selected `terraform-workspace`/`tofu-workspace` without external providers; a Variable Group secret of type `env` with the `TF_VAR_` prefix actually becomes an input variable, is confirmed by a safe SHA-256 marker, and does not appear in the API/output/Allure. The Build → Deploy chain verifies assignment of `start_version`, manual selection of a successful build, persistence of `build_task_id`, and identical target/incoming versions in the executor; a version deploy is shown via the nested build task in the history API. A repository-backed file inventory executes the playbook from the expected host group; a safe canary records a create/update validation gap for a traversal path. Deleting a project after the task has been stopped succeeds with cascading cleanup, but `v2.19.8` accepts deletion while `running`: the executor continues the playbook and then writes FK errors; the reproduction is in `project-deletion-running-task-defect.md`. Private HTTPS Git is verified by a separate profile with a trusted self-signed CA and Basic Auth: an authenticated clone actually executes the playbook, a request without a key is rejected, the password is absent from API/Allure diagnostics, and the login/password is absent from the task output. The API token block covers create/list, expiry, Bearer access, revoke, and plaintext protection in diagnostics. The user lifecycle covers create/update/delete/recreate; deactivate/reactivate is not claimed because the current router and `db.User` have no such state. The webhook block covers token auth, matcher routing, body/header extraction, negative no-launch, and real task execution. Backup/restore verifies transfer of relationships, the absence of task history/plaintext secrets, executability of the restored template, denial for non-admins, and broken references. An off-by-one duplicate validation defect was found: two resources with the same name are accepted and created; the canary and analysis are in `project-backup-restore-validation-defect.md`. Workflows and external Secret Storage management are confirmed as Pro features with Community stubs/feature flags; an honest e2e for them requires a test subscription, not just a local Vault toolchain.

---

## Stage 4. CI Integration

I add tests to CI by cost tier:

- fast checks on every PR;
- heavier integration and e2e tests on a schedule or before a release;
- preservation of logs, screenshots, traces, and other artifacts on failure;
- timeouts and a clear execution result;
- rules for temporarily disabling an unstable test with a mandatory reason and a fix ticket.

At the start, tests should not block development until their stability is confirmed. After an observation period, reliable critical checks are promoted to a mandatory gate.

**Result:** a working CI pipeline and a clear process for analyzing failures.

**Current status:** the stage has been implemented. The pull-request workflow runs the framework quality gate, the API baseline, and a short Chromium UI smoke on `core-sqlite-local`; the browser suite verifies password login, launching an API-prepared executable template, and client-side project-name validation without sending a create request. The daily matrix job runs the PostgreSQL, MySQL, MariaDB, persistent runner, SSH, private HTTPS Git, direct OIDC, HTTPS/subpath OIDC, LDAPS, TOTP, and encryption-rotation feature profiles; the weekly and manual release workflow verifies the SQLite/PostgreSQL upgrade. Jobs have timeouts, `fail-fast: false` for matrices, preserve JUnit/HTML/Allure and Compose diagnostics, and cleanup always runs. The full manual configuration matrix on Semaphore `v2.19.12` passed successfully on 2026-09-04: all 11 profiles are green. On the same day, the PR gate with SQLite/UI and both `v2.19.8 → v2.19.12` upgrade profiles passed separately. Manual investigation runs confirm the known schedule, dynamic-runner, and shell-output defects and are not part of the stable gate. After each run, the individual profile Allure reports are assembled by a reusable workflow into a self-contained single-file HTML artifact. GitHub Pages deployment is suspended due to the plan limitation of the private repository.

---

## Stage 5. Development Roadmap

After the foundation is launched, I form a backlog of subsequent improvements. Each task contains:

- the risk or problem it closes;
- the recommended test level;
- approximate scope;
- dependencies;
- completion criteria;
- priority.

Preliminary directions:

- extending the RBAC matrix;
- negative scenarios for handling secrets;
- concurrent task launch and stop;
- Git integrations and unreachable remotes;
- schedules and time handling;
- migrations between versions;
- testing on supported DBMSs;
- critical UI paths;
- installation and upgrade;
- load and security checks.

**Result:** a living prioritized backlog for systematic development of the suite by the project owner.

---

## Stage 6. Operation and Maintainability

The system is considered suitable for long-term independent maintenance when the project owner, after any pause and without restoring context from correspondence, can:

- bring up the environment;
- run all test levels;
- understand the cause of a typical failure;
- add a new test following an existing example;
- open a PR and get a correct CI result.

### Maintained materials

- README on local launch;
- description of the test structure;
- rules for test data and secrets;
- commands for running the different suites;
- CI description;
- known limitations and sources of instability;
- risk and priority map;
- up-to-date backlog of further automation;
- short architecture diagram;
- log of known product defects and infrastructure problems.

Documentation is updated together with changes to tests, profiles, and CI. A handover to another engineer is not currently planned; if that changes, a separate handover process will be added later.

---

## Main Project Deliverables

1. A map of product pain points based on issues and defect history.
2. A prioritized model of risks and critical scenarios.
3. A reproducible local test environment.
4. Analysis of the API and the state of the API documentation.
5. A decision on the previous QA's legacy.
6. A short strategy and the chosen tool stack.
7. A working baseline set of automated tests.
8. Tests running in CI with diagnostic artifacts.
9. A living prioritized development backlog.
10. Documentation for independent operation and extension.

---

## What Needs to Be Clarified at the Start

- Is the closed `pro/` part in scope, and is it available for testing?
- Is only self-hosted Semaphore tested, or the SaaS portal as well?
- Which installation methods and DBMSs actually matter to users right now?
- Which regressions or incidents does the customer consider the most painful?
- Where will CI run, and are there resource constraints?
- Who will be able to review changes in the application and the test infrastructure?
- What regular cadence of development, Semaphore upgrades, and CI failure analysis does the project need?

---

## Mode of Further Work

The initial foundation has already been implemented. Further work proceeds in short, complete iterations from a risk or a discovered problem to an automated check and a result in CI.

Recommended cycle:

1. Analyze a new issue, upstream change, or CI failure and assess the risk.
2. Choose the minimal suitable check level: API, integration, UI, or configuration profile.
3. Implement the scenario with deterministic fixtures and diagnostics.
4. Run it locally on the target configuration and then in the appropriate CI workflow.
5. Update the coverage map, known limitations, and backlog.

New scenarios are added based on risk and actual value, without a fixed project completion date and without the goal of maximizing the number of tests.
