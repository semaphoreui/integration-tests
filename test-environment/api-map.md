# Карта API для первой автоматизации

**Версия стенда:** Semaphore UI `v2.19.8`
**Источники:** `api-docs.yml`, `api/router.go`, read-only запросы к локальному стенду

## Общая картина

В `api-docs.yml` описано 77 путей и 127 операций:

| Метод | Количество |
|---|---:|
| GET | 55 |
| POST | 34 |
| PUT | 16 |
| DELETE | 22 |

Спецификация покрывает основную модель продукта, но не полностью соответствует фактическому router. Поэтому генерацию тестов напрямую из OpenAPI пока нельзя считать надёжной без дополнительной проверки маршрутов и схем.

## Сквозной сценарий

| Шаг | Основные операции | Ожидаемые коды | Что проверять |
|---|---|---|---|
| Health | `GET /api/ping` | 200 | Доступность и точное тело `pong` |
| Login | `GET`, `POST /api/auth/login` | 200, 204 | Метаданные входа, корректные и неверные credentials, cookie-сессия |
| Projects | `GET`, `POST /api/projects` | 200, 201 | Создание изолированного проекта, обязательность имени, уникальность данных |
| Project role | `GET /api/project/{project_id}/role` | 200 | Роль и permissions текущего пользователя |
| Keys | CRUD `/api/project/{project_id}/keys` | 200/201/204 | Типы `none`, `ssh`, `login_password`, скрытие секретов, refs и удаление |
| Repositories | CRUD `/api/project/{project_id}/repositories` | 200/201/204 | Git URL, branch/ref, access key, branches, playbooks, ошибки clone |
| Inventory | CRUD `/api/project/{project_id}/inventory` | 200/201/204 | `static`, `static-yaml`, `file`, связи с key/repository, validation |
| Templates | CRUD `/api/project/{project_id}/templates` | 200/201/204 | Связи repository/inventory/key, playbook, arguments, survey variables |
| Tasks | `POST /tasks`, `GET /tasks/{id}` | 201, 200 | Queue и lifecycle, параметры запуска, итоговый статус |
| Task output | `GET /tasks/{id}/output`, `/raw_output` | 200 | Структурированный и сырой output, отсутствие секретов |
| Stop task | `POST /tasks/{id}/stop` | 204 | Обычная и принудительная остановка |
| Schedules | CRUD `/api/project/{project_id}/schedules` | 200/201/204 | cron, `run_at`, active, timezone/DST, task params |
| Project users | CRUD `/api/project/{project_id}/users` | 200/204 | Роли owner/manager/task_runner/guest и project isolation |
| Global runners | `GET/PUT /api/runners/{id}`, `GET /api/runner_tags` | 200/204 | Регистрация, active/default, heartbeat, tags, capacity и routing |
| Integrations | CRUD `/api/project/{project_id}/integrations`, aliases, matchers, values; `POST /api/integrations/{alias}` | 200/201/204 | Token auth, matcher routing, body/header extraction, связь с task и безопасный отказ |
| Project backup | `GET /api/project/{project_id}/backup`, `POST /api/projects/restore` | 200 | Перенос связей ресурсов, отсутствие task history и authentication secrets, исполнимость восстановленного template |
| Cleanup | DELETE созданных ресурсов и проекта | 204 | Удаление в обратном порядке и отсутствие остаточных данных |

## Зависимости ресурсов

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

Это определяет порядок создания тестовых данных и обратный порядок очистки.

## Подтверждённые read-only проверки

| Endpoint | Результат |
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

## Найденные расхождения документации и реализации

### 1. Не описан `GET /project/{project_id}/schedules`

В `api-docs.yml` для collection endpoint описан только `POST`, но `api/router.go` регистрирует также `GET` и `HEAD`. Живой запрос `GET /api/project/1/schedules` возвращает 200.

Расхождение подтверждено автоматизированным smoke: неактивный cron schedule успешно создаётся, читается по ID и находится через collection `GET`.

### 2. Query-параметры `sort` и `order` ошибочно помечены обязательными

В OpenAPI они имеют `required: true` для пользователей, ключей, репозиториев, inventory и templates. Живые запросы без этих параметров возвращают 200. Для клиентов, сгенерированных по спецификации, это создаёт лишнее ограничение.

### 3. Request schemas не задают обязательные поля

У `Login`, `ProjectRequest`, `AccessKeyRequest`, `RepositoryRequest`, `InventoryRequest`, `TemplateRequest` и `ScheduleRequest` отсутствуют массивы `required`. По спецификации почти любое пустое тело выглядит допустимым, хотя реализация ожидает конкретные поля.

### 4. Router содержит дополнительные важные операции

В реализации присутствуют маршруты, отсутствующие или неполно представленные в текущей выборке OpenAPI:

- подтверждение и отклонение задачи;
- task stages и Ansible hosts/errors;
- repository branches, playbooks и refs;
- refs ресурсов перед удалением;
- cron validation;
- активация schedule;
- template permissions;
- project roles;
- secret storages;
- очистка project cache.

Перед контрактным тестированием необходимо автоматически сравнить router и OpenAPI целиком.

### 5. Sensitive fields access key не возвращаются в plaintext

Автоматизированный security smoke для `login_password` key подтверждает, что уникальный password marker отсутствует в ответах create/get/list. Ключ используется как inventory credential при реальном запуске локальной Ansible-задачи; marker также отсутствует в structured output, raw output, Allure и JUnit artifacts.

## Первая очередь API-тестов

### P0 — обязательный smoke

1. Health и валидный/невалидный login.
2. Создание и чтение проекта.
3. Создание key, repository, inventory и template.
4. Запуск task и ожидание terminal status.
5. Проверка output и отсутствия секретов.
6. Удаление созданных данных.

### P1 — основные риски

1. Git branch/ref и ошибки clone.
2. Расширенные SSH и login/password access keys: rotation, known_hosts и custom SSH config.
3. Schedule: cron, run-at, timezone/DST и параметры задачи.
4. RBAC для manager, task_runner и guest.
5. Запрет доступа к ресурсам другого проекта.
6. Stop/force stop, project `max_parallel_tasks` и параллельный запуск задач.
7. Variable Groups: смешанные JSON/ENV/secret values, rename persistence и безопасное task execution.
8. Survey variables и launch-time overrides: metadata, persistence, secret masking, arguments и Ansible params.

### P2 — расширение

1. Secret storage. External storage management является Pro-функцией; Community API сообщает выключенный feature flag и не даёт честного Vault/OpenBao/AWS/Azure сценария без test subscription.
2. Integrations и webhooks. Token auth, project alias routing, header matcher, body/header extraction и реальный task execution автоматизированы; HMAC/GitHub/Bitbucket/Basic auth остаются расширением.
3. Runners. Registration/default/heartbeat, exact tag routing и capacity автоматизированы; unavailable recovery и one-off остаются известными дефектами.
4. Workflows. DAG execution является Pro-функцией: Community controller — документированный stub, поэтому e2e отложен до test subscription.
5. Backup/restore и миграционные сценарии. Project backup/restore round trip автоматизирован; release upgrade SQLite/PostgreSQL покрыт отдельно.

## Текущий статус и ближайшее расширение

Исполняемый API smoke реализован на Bookwright v1.4.0: он создаёт изолированные ресурсы без фиксированных ID, использует deterministic typed fixtures, выполняет LIFO cleanup и защищает диагностику от секретов.

Из Git-рисков P1 автоматизированы успешный запуск из явно выбранной ветки, отсутствующий ref и недоступный authenticated HTTPS remote. Ошибки приводят задачу в ожидаемый статус `error`, сохраняют полезную Git-диагностику и не раскрывают login/password в structured или raw output.

Успешный SSH repository/access key автоматизирован в отдельном `feature-ssh-local`: зашифрованный ключ используется для Git clone и Ansible SSH inventory, удалённый output подтверждён маркером. Negative-сценарий с неверным ключом проверяет диагностируемый отказ. Rotation-сценарий использует два SSH fixture с разными authorized keys: старый secret получает отказ на втором сервере, `PUT /keys/{id}` заменяет secret без изменения key ID, после чего Git clone и Ansible SSH проходят. Private keys/passphrases отсутствуют в API responses, HTTP/Allure diagnostics, structured и raw task output.

Для встроенных ролей `manager` и `task_runner` автоматизированы точные permission bitmask и поведенческие границы. Обе роли могут запускать задачи; manager может управлять ресурсами, но не проектом и участниками; task runner не может изменять ресурсы, проект или участников.

Обычный stop и force-stop автоматизированы на long-running Ansible fixture. Запрос отправляется после marker фактического начала playbook; затем проверяются terminal `stopped` и отсутствие marker шага после паузы.

Persistent remote runner автоматизирован отдельной API-группой и production-like профилем PostgreSQL. Проверяются регистрация, `active`, `is_default`, `online`, heartbeat и выполнение task suite вне server process. Exact tag сохраняется и выбирает ожидаемый `used_runner_id`; при capacity `1` второй matching task возвращается в `waiting` и запускается после освобождения слота. При отсутствии matching active runner `v2.19.8` вместо recoverable waiting завершает задачу с `error: no runners available`; доказательства и code boundary находятся в `runner-unavailable-routing-defect.md`. Secret survey variables также теряются на границе remote dispatch; canary и upstream #4086 описаны в `remote-runner-survey-secrets-defect.md`.

Project concurrency покрыта независимо от runner capacity: parallel-capable template при project limit `1` удерживает вторую задачу в `waiting`, после stop первая освобождает слот; update проекта до `2` разрешает двум задачам одновременно дойти до running marker. Это защищает create/update persistence и queue admission без таймингового предположения о моменте POST.

Schedule P1 расширен отдельным API-набором: backend cron validation, диагностируемые ошибки invalid cron/type/run-at, CRUD/update, active toggle, сохранение `run_at`, `delete_after_run` и task parameters, запрет создания для `task_runner`, а также контракт системной timezone. Реальное cron и `run_at` исполнение покрыто отдельным `feature-schedule-timezone`, но на `v2.19.8` оба сценария воспроизводят отсутствие автоматически созданной task. До Linux-подтверждения профиль оставлен вне CI matrix; доказательства собраны в `schedule-execution-defect.md`.

Variable Groups покрыты отдельным API-набором: create/get/list, смешанные JSON extra vars и ENV, secrets типов `var`/`env`, переименование secret с сохранением значения, backend validation пустого имени и реальное Ansible execution. Секреты проверяются внутри playbook по SHA-256 под `no_log` и отсутствуют в API responses, structured/raw output и Allure diagnostics. Набор зелёный на SQLite и PostgreSQL `v2.19.8`; API persistence для сценария #2293 работает, браузерный payload остаётся отдельной UI-проверкой.

Survey/task override API-набор сохраняет enum/int/string/env/secret definitions и выполняет задачу с launch environment/secret, template/task arguments и Ansible params на SQLite и PostgreSQL local execution. Проверяются persisted template/task payloads, реальный marker и отсутствие survey secret в structured/raw output. На persistent runner `v2.19.8` positive path заменён known-defect canary: secret не достигает executor; fix #4086 уже находится в `v2.20.0-alpha1`. Неподдерживаемый target отклоняется с `400`. `v2.19.8` также принимает enum default вне values; defect и upstream fix `eb29c3e8` описаны в `survey-default-validation-defect.md`.

Webhook integrations покрыты отдельным domain API и steps. Сквозной сценарий создаёт token-authenticated searchable integration, общий project alias, header matcher и два extractor для JSON body/header. Неверный token и несовпавший matcher возвращают публичный `204` без task headers; валидный webhook создаёт task, возвращает `X-Semaphore-*` identifiers, сохраняет `integration_id` и передаёт extracted значения в Ansible variables. Access-key secret остаётся замаскированным в API и HTTP/Allure diagnostics.

Project backup/restore покрыт отдельным domain API и steps. Round trip экспортирует проект с access keys, repository, inventory, template, schedule и уже выполненной task, проверяет отсутствие plaintext login/password в JSON, меняет только имя проекта и восстанавливает конфигурацию. Новые resource IDs и все ссылки между ними проверяются, task history не переносится, а восстановленный template успешно выполняет доверенный playbook.

Negative restore contract проверяет `401` для non-admin и `400` для отсутствующей repository-ссылки. Отдельный canary фиксирует дефект `v2.19.8`: backup с двумя одинаковыми repository names принимается с `200` из-за условия `n > 2` в общем duplicate validator; восстановленный проект действительно содержит оба объекта. Подробности находятся в `project-backup-restore-validation-defect.md`.

Schedule execution defect подтверждён в Linux CI: `v2.19.8` не создаёт task ни для active cron, ни для `run_at`. SSH key rotation автоматизирована. Строгий `known_hosts` остаётся version-gated сценарием: соответствующая конфигурация отсутствует в `v2.19.8` и должна быть добавлена после перехода на релиз, содержащий текущую upstream-реализацию.
