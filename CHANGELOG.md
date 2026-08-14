# Changelog

All notable changes to the Semaphore UI test automation project are documented in this file.

## [Unreleased]

### Added

- GitHub Actions pull-request gate with framework checks and the SQLite core profile.
- Daily PostgreSQL, MySQL, MariaDB, and persistent-runner configuration matrix.
- Weekly and manually triggered SQLite/PostgreSQL release-upgrade verification.
- CI artifacts containing JUnit, HTML, Allure, and failure diagnostics.
- Downloadable HTML site with a separate Allure report for every executed profile.

## [0.1.0]

### Added

- Bookwright-based Java 21 API automation foundation for Semaphore UI.
- Managed Docker Compose profiles for SQLite, PostgreSQL, MySQL, MariaDB, and a persistent remote runner.
- Critical workflow, RBAC, secrets, Git, schedules, task lifecycle, and configuration coverage.
- Two-phase release-upgrade profiles for SQLite and PostgreSQL.
