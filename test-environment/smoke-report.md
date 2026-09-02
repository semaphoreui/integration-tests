# Smoke check of the local Semaphore UI

**Date:** 2026-08-07  
**Version:** Semaphore UI `v2.19.7`  
**Configuration:** Docker Compose, SQLite, single container

## Result

The minimal environment is up and suitable for further exploration of the UI and API.

The standalone executable smoke `api-smoke.mjs` also successfully passes the full cycle of creating and deleting a temporary project.

The same scenario was ported to the Bookwright Java framework and implemented in `SemaphoreProjectSmokeTest`. The test uses Retrofit/OkHttp, Guice, Allure steps, AssertJ, and automatic LIFO cleanup. The scenario was extended with the access key → repository → inventory → task template chain.

| Check | Expected result | Actual result | Status |
|---|---|---|---|
| `GET /api/ping` | `200`, body `pong` | `200`, `pong` | Pass |
| `POST /api/auth/login` | Successful authentication | `204` | Pass |
| UI load | Login form opens | `200`, form is displayed | Pass |
| Login via UI | Dashboard opens | Dashboard of the `demo` project | Pass |
| `GET /api/projects` | Project list accessible | `200`, 1 project | Pass |
| `GET /api/project/1/repositories` | Repositories accessible | `200`, 1 repository | Pass |
| `GET /api/project/1/inventory` | Inventory accessible | `200`, 3 records | Pass |
| `GET /api/project/1/templates` | Templates accessible | `200`, 8 templates | Pass |
| `GET /api/project/1/tasks` | Task history accessible | `200`, 2 successful tasks | Pass |
| Wrong password | Login rejected | `401` | Pass |
| Temporary project creation | Project created | `201`, ID received | Pass |
| Project creator role | `owner` | `200`, role `owner` | Pass |
| Temporary project cleanup | Project deleted | `204` | Pass |
| Bookwright `SemaphoreProjectSmokeTest` | Full Java API smoke | Pass | Pass |
| Core resource chain creation | Key, repository, inventory, and template linked correctly | Pass | Pass |
| Trusted playbook execution | Task reaches `success` | Pass | Pass |
| Task output check | Marker `semaphore-bookwright-smoke-ok` found | Pass | Pass |
| Inactive cron schedule | Create/get/list and link to template | Pass | Pass |

A full local run of the Bookwright infrastructure self-tests together with the Semaphore smoke completed successfully.

No errors or warnings were found in the browser console during login and when opening the Repositories section.

## Observations

- On first start, the image automatically applies migrations and creates an administrator.
- In the initial state, a demo project `demo` is automatically created with a repository, inventory, templates, and a history of successful tasks.
- The demo data is convenient for smoke checking but must not be used as a basis for independent automated tests.
- Automation will require controlled data creation via the API and a guaranteed reset of the SQLite volume.
- `api-docs.yml` describes 77 paths: 55 `GET` operations, 34 `POST`, 16 `PUT`, and 22 `DELETE`.

## Next step

The API map of the first end-to-end scenario is prepared in `api-map.md`. The basic independent API smoke is implemented. The next step is to extend it with project resources:

1. a separate API session for a second user;
2. RBAC and project isolation;
3. negative Git clone and branch/ref scenarios;
4. verification that no secrets appear in the output.
