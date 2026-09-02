# Semaphore UI Testing Launch Plan

**Project:** https://github.com/semaphoreui/semaphore
**Work format:** analysis of the current state, establishment of the foundation for automated testing, and handover of the project to an engineer who will further develop and maintain the test suite.

---

## Goal

The goal is not limited to an audit and recommendations, but to launch testing as a working engineering system:

* understand the actual product problems;
* define risk-based priorities;
* establish a reproducible test environment;
* choose a practical technology stack;
* implement the first useful tests;
* integrate them into CI;
* prepare documentation and hand over the system to the next engineer.

The outcome should be not only a strategy, but also a repository with a clear structure, working tests, and a simple way to run them locally and in CI.

---

## Principles

1. **Start with product pain points, not the tool.** First identify where and why defects occur, then choose the tests and technologies.
2. **Critical scenarios are more important than coverage percentage.** First protect authentication, access control, secrets, task execution, repositories, and upgrades.
3. **API is the primary starting level.** API tests are generally faster and more stable than UI e2e tests while allowing business logic and access control to be tested directly.
4. **Use UI e2e selectively.** Through the browser, cover only critical user journeys and functionality that cannot be sufficiently verified at a lower level.
5. **Everything must be maintainable by a single engineer.** Running tests, diagnosing failures, and adding a new test should not require a separate infrastructure team.
6. **Handover is built in from day one.** Decisions, run commands, limitations, and known issues are documented throughout the work.

---

## Phase 1. Research and Prioritization

This is the starting phase. Its purpose is to collect the facts on which the testing strategy and initial implementation will be based.

### 1.1. Analysis of Open and Closed Issues

Review open and closed issues, primarily those labeled `bug`, `regression`, and `security`, as well as issues related to releases.

The following needs to be determined:

* which parts of the product fail most frequently;
* which defects recur;
* which problems have the greatest impact on users;
* which scenarios are difficult to reproduce manually;
* where missing automation slows down releases or bug fixes;
* which defects have already been fixed but may return.

Issues are grouped by functional area:

* authentication and user management;
* RBAC and cross-project access;
* task creation and execution;
* runners, queue, and parallel execution;
* secrets, keys, and Variable Groups;
* Git repositories and integrations;
* inventory and environments;
* schedules;
* migrations and different databases;
* UI;
* installation and upgrades.

The initial pass covered all 2,192 issues: 865 open and 1,327 closed. For each record, the user complaint, symptom, fix status, available evidence, and primary system component were recorded. The complete registry is stored in `outputs/issues-assessment/semaphore-issues-register.xlsx`.

#### Results of the Issue Analysis

The highest concentration of reports and explicitly labeled defects was found in the following components:

| Component                          | Total issues | Issues with `bug` label | Open | Issues with `critical` label |
| ---------------------------------- | -----------: | ----------------------: | ---: | ---------------------------: |
| Git repositories                   |          450 |                      68 |  152 |                           17 |
| Authentication and sessions        |          295 |                      50 |  126 |                           17 |
| Keys, secrets, and Variable Groups |          208 |                      22 |   89 |                           10 |
| Scheduling and time                |          164 |                      23 |   88 |                           12 |
| Task output and events             |          137 |                      24 |   52 |                            5 |
| Task templates                     |          127 |                      18 |   66 |                            5 |

Key findings:

* **Git repositories are the largest problem area.** The most common risks include clone/pull, SSH and HTTPS access, branches and refs, playbook paths, and the Git cache.
* **Authentication is one of the highest-risk areas.** This includes login, API tokens, LDAP, OIDC, TOTP, sessions, and related access checks.
* **Secrets form a critical dependency chain with repositories and task execution.** Problems with access keys, Vault, Variable Groups, encryption, or secret transmission can block the end-to-end user workflow.
* **Scheduling has a large unresolved backlog:** 88 of 164 issues are open (53.7%). Particular attention is required for cron, time zones/DST, one-time runs, and parameter passing.
* **Templates also remain a problematic area:** 66 of 127 issues are open (52%). The main risks are at the intersection of launch parameters, survey variables, inventory, repositories, and secrets.

The number of issues does not equal the number of confirmed defects: the dataset also contains feature requests, questions, and reports without a `bug` label. Therefore, priority is determined by a combination of frequency, criticality, and impact on the main user flow.

#### First Automation Priority

1. User login and API token acquisition.
2. Project creation and Git repository configuration.
3. Access key creation and verification of secure secret handling.
4. Inventory and task template creation, followed by task execution.
5. Verification of the task lifecycle, final status, and output.
6. Schedule creation with cron, time zone, and execution parameters.
7. Negative RBAC and project isolation checks.

This end-to-end flow covers the main user actions while also touching the five most problematic components.

**Result:** a map of product pain points and a preliminary list of critical scenarios with an explanation of their priorities.

### 1.2. Local Test Environment

Set up Semaphore locally and document a reproducible path from cloning the repository to running the service.

**Current status:** a minimal `v2.19.7` environment with SQLite has been started using Docker Compose. The UI, authentication, and basic read-only API requests have been verified. Startup commands and smoke-test results are stored in `test-environment/`.

The Java-based Bookwright v1.4.0 framework has been selected for automation. Gradle/JUnit 5, Retrofit/OkHttp, Guice, Allure, Playwright, deterministic typed fixtures, target/domain APIs and steps, architecture self-tests, typed precondition state, and LIFO cleanup have been adapted to the test repository. The end-to-end Java API smoke suite successfully verifies health, login, project and owner role, a local Git fixture, inventory, template, Ansible task execution, output, an inactive cron schedule, guest access to an assigned project, denial of guest access-key modification, and isolation of an unassigned project. A separate security smoke test creates a `login_password` key, actually uses the password during task execution, and verifies the absence of plaintext in API responses, structured/raw task output, and test artifacts. The Git suite verifies task execution from an explicitly selected branch, expected failure for a missing ref and an unavailable authenticated HTTPS remote, including the absence of credentials in diagnostics. The RBAC suite verifies the permission bitmask and `manager`/`task_runner` boundaries: permitted task execution, permitted manager resource management, and restrictions on project/resource/member mutations according to the role. The task lifecycle suite, after confirming the start of a long-running playbook, verifies both regular stop and force-stop, terminal `stopped` status, and the absence of execution of the subsequent step. Project test data is removed automatically; the RBAC fixture user is reused between runs because Semaphore v2.19.7 does not allow deleting a user with a history of login sessions.

The following is verified:

* which dependencies are required;
* which startup method is most convenient for test development;
* how to create users, projects, and test data;
* how to clean up or recreate state;
* how to obtain application and task logs;
* which external dependencies are required for the tests;
* whether the same environment can be run locally and in CI.

The initial configuration matrix and the approach to extending it are documented in `test-environment/configuration-testing-overview.md`. Instead of exhaustive combinations, the approach uses fast configuration checks, several representative end-to-end profiles, and independent feature profiles. `core-sqlite-local`, `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, and the production-like `prod-postgres-runner` profiles have been implemented: manifests pin the configuration, a unified lifecycle command manages startup/setup and records runtime metadata and exact image digests in Allure. A single core API suite passes on SQLite, PostgreSQL, MySQL 8.4, and MariaDB 10.11, both locally and through a separate persistent runner; the runner API verifies registration, default/online status, and heartbeat. Separate `upgrade-sqlite-local` and `upgrade-postgres-local` profiles reproduce N-1 → current upgrades against a preserved database. They identified a blocking incompatibility in `v2.19.6 → v2.19.7`: the current release cannot read access keys from the previous release's schema because migration fields `v2.20.1` were removed. The automation and report are ready, but the upgrade jobs must remain red until the product defect is fixed.

**Result:** a working test environment and a documented startup procedure.

### 1.3. API and Documentation Analysis

Study application routes, `api-docs.yml`, actual API behavior, and discrepancies between the implementation and the documentation.

The following is verified:

* completeness and up-to-dateness of the API specification;
* authentication mechanisms;
* core CRUD operations and resource lifecycles;
* role and project-ownership checks;
* error contracts and input validation;
* asynchronous operations: task execution, statuses, logs, and stopping;
* API suitability for preparing and cleaning up test data;
* feasibility of contract testing against the specification.

The initial API test list is determined together with the pain-point map from the issue analysis. The existence of an endpoint alone does not make it a priority.

**Result:** an API map, a list of documentation discrepancies, and a prioritized set of scenarios for automation.

### 1.4. Review of Previous QA Tests

Review existing materials in `test/`, including Playwright configuration, test cases, and helper files.

For each item, determine:

* what it verifies and whether it matches the current product;
* whether it currently runs;
* how stable and diagnosable the test is;
* whether its structure, fixtures, or data can be reused;
* how much it would cost to restore compared with rewriting it;
* whether the scenario belongs to the established priorities.

The decision is made based on the review rather than in advance. Possible outcomes: keep, fix, partially reuse as a source of scenarios, or remove after documenting useful information.

**Result:** a brief report on existing tests and a decision for each useful block.

---

## Phase 2. Strategy and Technical Foundation

Based on Phase 1, establish the minimum testing strategy.

### What to Define

* critical user and technical scenarios;
* which checks are required at the Go unit/integration, API, and UI e2e levels;
* the minimum set of test environments;
* the approach to test data and state cleanup;
* rules for handling secrets in tests;
* reporting and failure-diagnostics mechanisms;
* what runs on every PR and what runs on a schedule or before a release;
* criteria for considering a test complete and maintainable.

### Tool Selection

Priority is given to the project's existing technology stack where it is suitable:

* standard Go tooling for unit and integration tests;
* API testing in a language and framework that the main team can easily maintain;
* Playwright for a small number of critical UI scenarios, if the existing configuration is viable;
* GitHub Actions for CI;
* existing OpenAPI/Dredd infrastructure, after verifying its current relevance and usefulness.

A new tool is introduced only when it solves a specific problem better than the existing solution and does not create an unjustified maintenance cost.

**Result:** a concise testing strategy, test-level model, selected stack, and test-project structure.

---

## Phase 3. Implementation of the First Working Version

Create a minimal but complete set of tests that already provides value and serves as an example for further development.

Preliminary end-to-end scenario:

1. start a clean test environment;
2. create or log in as a test user;
3. create a project;
4. add a repository, inventory, key, and task template;
5. run the task;
6. wait for completion;
7. verify the status and result;
8. verify access using another role or user;
9. clean up the created data.

The exact scope is determined after analyzing issues and the API. In addition to the positive scenario, the first version must include checks for the most dangerous failure modes: incorrect permissions, access to another user's project, invalid input data, external dependency failures, and leakage of sensitive data.

### First-Version Requirements

* one obvious way to run the tests;
* reproducibility on a clean machine;
* independent or safely isolated tests;
* clear fixtures and test data;
* diagnostic error messages;
* no real secrets in the repository or logs;
* reasonable execution time;
* examples that allow the next engineer to write a new test based on an existing pattern.

**Result:** a working baseline set of API/integration tests and, where necessary, several critical UI e2e tests.

---

## Phase 4. CI Integration

Integrate the tests into CI according to their cost level:

* fast checks on every PR;
* heavier integration and e2e tests on a schedule or before releases;
* preservation of logs, screenshots, traces, and other artifacts on failure;
* timeouts and clear execution results;
* rules for temporarily disabling an unstable test, with a mandatory reason and a tracking issue for fixing it.

Initially, tests should not block development until their stability has been confirmed. After an observation period, reliable critical checks are promoted to a mandatory gate.

**Result:** a working CI pipeline and a clear process for analyzing failures.

**Current status:** this phase has been implemented. The pull-request workflow runs the framework quality gate and `core-sqlite-local`; a daily matrix job runs PostgreSQL, MySQL, MariaDB, and the persistent runner; a weekly and manual release workflow verifies SQLite/PostgreSQL upgrades. Jobs have timeouts, `fail-fast: false` for matrices, preserve JUnit/HTML/Allure and Compose diagnostics, and always perform cleanup. The upgrade workflow is separated from the PR gate and remains red until the confirmed product defect is fixed. After each run, separate Allure reports for each profile are collected by a reusable workflow into a ready-to-view HTML artifact, including reports from failed jobs. GitHub Pages deployment is currently paused due to a pricing limitation of the private repository.

---

## Phase 5. Development Roadmap

After the foundation is launched, create a backlog of further improvements. Each task should contain:

* the risk or problem it addresses;
* the recommended testing level;
* an approximate effort;
* dependencies;
* completion criteria;
* priority.

Preliminary areas:

* expanding the RBAC matrix;
* negative secret-handling scenarios;
* concurrent task execution and stopping;
* Git integrations and unavailable remotes;
* scheduling and time-related behavior;
* version-to-version migrations;
* testing against supported databases;
* critical UI paths;
* installation and upgrades;
* load and security testing.

**Result:** a practical development plan for the first 2–3 months of work by the next engineer.

---

## Phase 6. Documentation and Handover

The handover is considered complete when a new engineer can, without verbal guidance:

* start the environment;
* run all test levels;
* understand the cause of a typical failure;
* add a new test based on an existing example;
* open a PR and obtain a correct CI result.

### Handover Materials

* README for local setup;
* description of the test structure;
* test data and secret-handling rules;
* commands for running different test suites;
* CI documentation;
* known limitations and sources of instability;
* risk and priority map;
* backlog for further automation;
* a short architecture diagram;
* several starter tasks for the new engineer.

Ideally, the handover should be completed by working together on the new engineer's first PR. This will demonstrate that the system is genuinely understandable and suitable for further development.

---

## Main Project Outcomes

1. A map of product pain points based on issues and defect history.
2. A prioritized risk model and list of critical scenarios.
3. A reproducible local test environment.
4. API analysis and an assessment of the API documentation state.
5. A decision regarding the legacy work of the previous QA engineer.
6. A concise strategy and selected technology stack.
7. A working baseline set of automated tests.
8. CI test execution with diagnostic artifacts.
9. A 2–3 month development backlog.
10. Documentation and handover to the next engineer.

---

## What Needs to Be Clarified at the Start

* Is the closed-source `pro/` part of the scope, and is it available for testing?
* Is only self-hosted Semaphore being tested, or the SaaS portal as well?
* Which installation methods and databases are actually important to users today?
* Which regressions or incidents does the customer consider the most painful?
* Where will CI run, and are there resource constraints?
* Who will be able to review changes to the application and test infrastructure?
* Is there a desired deadline for the first working version and project handover?

---

## Timeline Estimate

A precise estimate should reasonably be established after the initial research phase: the scope depends heavily on the state of the environment, the current state of the API documentation, and the quality of the existing tests.

Preliminary estimate:

| Block                                                    |               Estimate |
| -------------------------------------------------------- | ---------------------: |
| Research of issues, environment, API, and existing tests |       5–8 working days |
| Strategy and technical foundation                        |       2–3 working days |
| First working version of the tests                       |      5–10 working days |
| CI, documentation, and handover                          |       3–5 working days |
| **Total to the first handover-ready version**            | **15–26 working days** |

This is an estimate rather than a fixed commitment before the initial analysis is completed. After Phase 1, the plan and estimate will be refined based on the actual findings.