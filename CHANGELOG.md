# Changelog

All notable changes to the Semaphore UI test automation project are documented in this file.

## [Unreleased]

### Added

- SSH access-key rotation coverage using isolated servers with distinct authorized keys.
- Browser-based OIDC discovery, callback, session, return-path, and external-user provisioning coverage through a pinned local Dex provider.
- GitHub Actions pull-request gate with framework checks and the SQLite core profile.
- Daily PostgreSQL, MySQL, MariaDB, persistent-runner, SSH, and OIDC configuration matrix.
- Weekly and manually triggered SQLite/PostgreSQL release-upgrade verification.
- CI artifacts containing JUnit, HTML, Allure, and failure diagnostics.
- Downloadable HTML site with a separate Allure report for every executed profile.

## [0.1.0]

### Added

- Bookwright-based Java 21 API automation foundation for Semaphore UI.
- Managed Docker Compose profiles for SQLite, PostgreSQL, MySQL, MariaDB, and a persistent remote runner.
- Critical workflow, RBAC, secrets, Git, schedules, task lifecycle, and configuration coverage.
- Two-phase release-upgrade profiles for SQLite and PostgreSQL.
