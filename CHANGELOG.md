# Changelog

All notable changes to the Semaphore UI test automation project are documented in this file.

## [Unreleased]

### Added

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
- Variable Group API coverage for mixed JSON/ENV/secret values, secret rename persistence, task execution, masking, and empty-name validation.
- Survey-variable and launch-time override coverage for enum/int/string/env/secret values, template/task arguments, Ansible params, persistence, execution, and secret masking.
- Reproducer and upstream fix trace for the `v2.19.8` backend gap that accepts enum defaults outside their allowed survey values.
- GitHub Actions pull-request gate with framework checks and the SQLite core profile.
- Daily PostgreSQL, MySQL, MariaDB, persistent-runner, SSH, OIDC, LDAPS, TOTP, and encryption-rotation configuration matrix.
- Weekly and manually triggered SQLite/PostgreSQL release-upgrade verification.
- CI artifacts containing JUnit, HTML, Allure, and failure diagnostics.
- Downloadable HTML site with a separate Allure report for every executed profile.

## [0.1.0]

### Added

- Bookwright-based Java 21 API automation foundation for Semaphore UI.
- Managed Docker Compose profiles for SQLite, PostgreSQL, MySQL, MariaDB, and a persistent remote runner.
- Critical workflow, RBAC, secrets, Git, schedules, task lifecycle, and configuration coverage.
- Two-phase release-upgrade profiles for SQLite and PostgreSQL.
