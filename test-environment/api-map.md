# Карта API для первой автоматизации

**Версия стенда:** Semaphore UI `v2.19.7`  
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
    └── Schedule ──> Cron/Run-at + Task Parameters
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
2. SSH и login/password access keys.
3. Schedule: cron, run-at, timezone/DST и параметры задачи.
4. RBAC для manager, task_runner и guest.
5. Запрет доступа к ресурсам другого проекта.
6. Stop/force stop и параллельный запуск задач.

### P2 — расширение

1. Variable Groups и secret storage.
2. Integrations и webhooks.
3. Runners.
4. Workflows.
5. Backup/restore и миграционные сценарии.

## Ближайшее техническое решение

До выбора окончательного тестового фреймворка нужно сделать небольшой исполняемый API smoke, который:

- создаёт собственный проект с уникальным именем;
- не зависит от демонстрационного проекта и фиксированных ID;
- сохраняет ID созданных ресурсов из ответов;
- всегда выполняет cleanup;
- печатает понятную диагностику без секретов.
