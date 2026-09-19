# Changelog

All notable changes to the Semaphore UI test automation project are documented in this file.

## [Unreleased]

### Changed

- Core SQLite CI now uses the published Community `develop` image and records its resolved image digest and source revision in Allure.
- Fixture paths are relative to `test-environment/fixtures`: `fixture-init` places the folder contents at the root of the fixtures Git repository, so records use `ansible/smoke.yml` instead of `test-environment/fixtures/ansible/smoke.yml`.
- Branch-isolation fixture branches are created inline by the `parallel-tasks` Compose overlay in the shared `fixture-repository` volume before `fixture-git-init` publishes it, replacing the mounted `setup.sh` and its separate local repository.
- Fixtures are served by a local `fixture-git` HTTP service inside the Compose network instead of being cloned from GitHub; the default `git.fixtures.repository` is `http://fixture-git/fixtures.git`, CI no longer sets `TEST_REPOSITORY`/`TEST_BRANCH`, and the server/runner fixture volume mounts are removed.
- Updated release profiles from Semaphore `v2.19.8` to `v2.19.12` and the SQLite/PostgreSQL upgrade path from `v2.19.7 → v2.19.8` to `v2.19.8 → v2.19.12`.
- Profile suites run test classes sequentially while `v2.19.12` has a concurrent task-output collector race; task concurrency remains covered inside its dedicated scenario.
- The final post-upgrade API regression suite now follows the same sequential execution rule, preventing the known output collector race from masking migration results.

### Added

- `external` profile running the `core-sqlite-local` suite against a user-managed Semaphore from `API_BASE_URL`/`API_USERNAME`/`API_PASSWORD`; it starts only the fixture services (now in `compose.fixtures.yml`) and publishes `fixture-git` on `FIXTURE_GIT_PORT` for `TEST_REPOSITORY`.
- Manual shell-output defect profile proving that `v2.19.12` can lose either short `stdout` or `stderr` after task success, with Linux CI evidence and upstream fix trace.
- Read-only `externalTest` suite with explicit target credentials and no dependency on local task fixtures.
- API-token lifecycle coverage for creation, prefix-only listing, bearer authentication, project access, revocation, expiry validation, and secret-safe HTTP diagnostics.
- Supported local-user lifecycle coverage for create, update, delete, absence verification, and recreation; unsupported deactivate/reactivate semantics are documented explicitly.
- Private HTTPS Git profile with trusted self-signed TLS, Basic Auth, successful playbook execution, missing-credential failure, and credential-leak checks.
- Repository-backed Ansible file inventory execution and a canary for create/update path-validation inconsistency.
- INI and YAML static multi-group inventory execution proving that a template limit selects only the requested host group.
- Plan-only Terraform and OpenTofu execution with real workspace inventory selection and no external provider downloads.
- Terraform/OpenTofu `TF_VAR_*` secret injection from a Variable Group with hash-based execution proof and API/output/report leak checks.
- Build-to-Deploy template chaining with successful-build selection, persisted linkage, nested history version, and executor target/incoming version checks.
- Password-login security coverage for account-enumeration resistance, empty credentials, session-cookie absence, repeated failures, and recovery through a valid login.
- Project deletion coverage after a stopped task and a reproducer for deletion during execution causing continued automation and foreign-key errors.
- SSH access-key rotation coverage using isolated servers with distinct authorized keys.
- Browser-based OIDC discovery, callback, session, return-path, and external-user provisioning coverage through a pinned local Dex provider.
- OIDC repeat-login, logout, local-account collision, and unavailable-provider coverage.
- Pinned OpenLDAP LDAPS profile covering bind/search, provisioning, repeat login, logout, invalid credentials, and local-account collision.
- Dynamic one-off runner profile covering webhook start, task execution, finish callback, and the runner process lifecycle.
- Reproducer and source-level analysis for the `v2.19.8` defect where a successful one-off runner never exits.
- PostgreSQL/Dex OIDC profile behind pinned NGINX with TLS termination and a non-root `/semaphore` public URL.
- Explicit OIDC session-cookie checks for `HttpOnly`, HTTPS-dependent `Secure`, and path attributes.
- PostgreSQL encryption-keyring rotation coverage for hot reload, mixed-key reads, `vault check`, backup/rekey, retired-key removal, and post-rekey task execution.
- TOTP self-enrollment, login challenge, invalid passcode, recovery, and recovery-code rotation coverage.
- TOTP secret, passcode, and recovery-code redaction in both HTTP attachments and raw Allure step parameters.
- Browser TOTP enrollment through Security settings, QR rendering, challenge, invalid passcode, and recovery-form coverage with sensitive failure artifacts suppressed.
- Core browser smoke for password login, launching an API-provisioned executable template, and client-side project-name validation without a create request.
- Browser regression for #2293 covering Variable Group secret-name update payload, persisted UI/API state, preserved value execution, and plaintext protection.
- Variable Group API coverage for mixed JSON/ENV/secret values, secret rename persistence, task execution, masking, and empty-name validation.
- Survey-variable and launch-time override coverage for enum/int/string/env/secret values, template/task arguments, Ansible params, persistence, execution, and secret masking.
- Reproducer and upstream fix trace for the `v2.19.8` backend gap that accepts enum defaults outside their allowed survey values.
- Project queue coverage proving `max_parallel_tasks` admission at limits one and two with a parallel-capable template.
- Persistent runner routing coverage for exact tags, `used_runner_id`, capacity re-queueing, active-state recovery through a fresh task, and unmatched tags.
- Reproducer and source-level boundary for tasks failing instead of waiting when no matching active runner is available.
- Persistent-runner canary and upstream fix trace for survey secrets being lost during remote dispatch on `v2.19.8`.
- Webhook integration coverage for token authentication, project-alias matcher routing, body/header extraction, task linkage, and ignored invalid requests.
- Project backup/restore round-trip coverage for resource relinking, omitted task history and authentication secrets, and post-restore task execution.
- Documented Pro subscription boundaries for Workflows and external Secret Storage management in the Community image.
- Reproducer and source-level analysis for project restore accepting duplicate resource names because of an off-by-one validation boundary.
- GitHub Actions pull-request gate with framework checks and the SQLite core profile.
- Daily PostgreSQL, MySQL, MariaDB, persistent-runner, SSH, OIDC, LDAPS, TOTP, and encryption-rotation configuration matrix.
- Weekly and manually triggered SQLite/PostgreSQL/MySQL/MariaDB release-upgrade verification.
- CI artifacts containing JUnit, HTML, Allure, and failure diagnostics.
- Downloadable HTML site with a separate Allure report for every executed profile.

### Changed

- Fixture paths are relative to `test-environment/fixtures`: `fixture-init` places the folder contents at the root of the fixtures Git repository, so records use `ansible/smoke.yml` instead of `test-environment/fixtures/ansible/smoke.yml`.
- Revalidated schedule, persistent-runner, and dynamic one-off runner defects against Semaphore
  `v2.19.12` and updated their current-stable evidence and next actions.

## [0.1.0]

### Added

- Bookwright-based Java 21 API automation foundation for Semaphore UI.
- Managed Docker Compose profiles for SQLite, PostgreSQL, MySQL, MariaDB, and a persistent remote runner.
- Critical workflow, RBAC, secrets, Git, schedules, task lifecycle, and configuration coverage.
- Two-phase release-upgrade profiles for SQLite and PostgreSQL.
