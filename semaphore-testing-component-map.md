# Карта компонентов Semaphore UI для анализа дефектов

**Дата среза:** 2026-08-06  
**Источник:** структура репозитория `semaphoreui/semaphore`, роуты API и основные сервисы.  
**Назначение:** единая классификация issues и будущих тестов.

## Правило классификации

Для каждого тикета указываем:

- **основной компонент** — место, где проявилась или была исправлена проблема;
- **дополнительные теги** — затронутые поперечные области, например `RBAC`, `Secrets`, `Migration`, `UI`, `Performance`;
- если причина не установлена, компонент помечается как **«требует проверки»**, а не угадывается по заголовку.

Один тикет может затрагивать несколько компонентов. Например, доступ пользователя проекта A к логу задачи проекта B классифицируется как `Tasks & execution` с тегами `Auth/RBAC`, `Project isolation`, `Task output`.

## Продуктовые компоненты

| Код | Компонент | Что входит | Основные области кода | Типичные риски и проверки |
|---|---|---|---|---|
| AUTH | Аутентификация и сессии | Login/logout, API tokens, TOTP, recovery, LDAP, OIDC, внешние identity, JWT/JWKS | `api/login*.go`, `api/auth.go`, `api/jwks.go`, `services/session_svc.go`, `pkg/jwt/`, `db/Session.go`, `db/UserExternalIdentity.go` | Обход аутентификации, срок жизни сессии, logout, token leakage, несовместимость LDAP/OIDC |
| USERS | Пользователи и глобальное администрирование | Пользователи, администраторы, настройки пользователя, системная информация | `api/users.go`, `api/user*.go`, `api/admin_info.go`, `db/User.go`, `cli/cmd/user*.go` | Повышение привилегий, lifecycle пользователя, несовместимые настройки |
| PROJECTS | Проекты и изоляция | Создание/изменение проектов, участники, приглашения, роли проекта, статистика | `api/projects/project*.go`, `api/projects/users.go`, `services/server/project_svc.go`, `db/Project*.go`, `db/Role.go` | Горизонтальный доступ, неверная роль, удаление связанных данных, изоляция проектов |
| TEMPLATES | Шаблоны задач | Task templates, параметры запуска, привязки inventory/repository/environment/key, template permissions | `api/projects/templates.go`, `db/Template*.go`, `api/router.go` | Некорректные связи, override параметров, права на запуск/редактирование, обратная совместимость |
| WORKFLOWS | Workflows | Workflow definitions, nodes, approvals, запуски и артефакты | `api/projects/workflows.go`, `db/Workflow*.go`, `pro_interfaces/workflow_*`, `pro/services/` | Порядок шагов, остановка, approval bypass, частичный сбой, доступ к артефактам |
| TASKS | Задачи и исполнение | Очередь, lifecycle задачи, локальное исполнение, stop/confirm/reject, статусы и retry | `api/projects/tasks.go`, `api/tasks/`, `services/tasks/`, `db/Task*.go` | Потерянные/зависшие задачи, гонки, неверный статус, остановка, параллелизм, cleanup |
| OUTPUT | Вывод задач и события | Сохранение output, stages, raw output, WebSocket streaming, event log, alerts | `services/tasks/TaskRunner_logging.go`, `pkg/task_logger/`, `api/sockets/`, `db/Event.go`, `services/tasks/alert.go` | Потеря строк, утечка секретов, зависание стрима, большой вывод, неверный порядок, алерты |
| RUNNERS | Удалённые раннеры | Регистрация, токены, теги, polling, назначение и выполнение job, reconciliation | `api/runners/`, `services/runners/`, `services/tasks/RemoteJob.go`, `db/Runner.go`, `cli/cmd/runner*.go` | Неверное назначение, потеря связи, duplicate execution, token auth, runner tags, большой payload |
| REPOSITORIES | Git-репозитории | Clone/pull, SSH/HTTPS auth, branches, playbooks, cache | `api/projects/repository.go`, `db_lib/*Git*`, `pkg/git/`, `db/Repository.go`, `api/cache.go` | Private repo auth, branch/ref, timeout, cache invalidation, command injection, недоступный remote |
| INVENTORY | Inventory и целевые хосты | Static/file/Terraform inventory, aliases и Terraform state | `api/projects/inventory.go`, `db/Inventory.go`, `db/TerraformInventory*`, `services/server/inventory_svc.go`, `pro_interfaces/terraform_inventory_ctl.go` | Большой inventory, неверный формат, утечка содержимого, state locking, удаление используемого ресурса |
| SECRETS | Ключи, секреты и Variable Groups | Access keys, SSH/login/vault keys, environments, secret storage, sync, task secrets | `api/projects/keys.go`, `api/projects/environment.go`, `api/projects/secret_storages.go`, `services/server/*secret*`, `services/server/access_key_*`, `db/AccessKey.go`, `db/Environment.go`, `db/SecretStorage.go` | Утечка в API/UI/logs/backup, encryption at rest, неправильный key, masking, sync, cross-project access |
| SCHEDULES | Расписания и время | Cron/run-at, timezone, activation, scheduler pool | `api/projects/schedules.go`, `services/schedules/`, `db/Schedule.go`, `pkg/tz/` | DST/timezone, повторный/пропущенный запуск, disable race, восстановление после рестарта |
| INTEGRATIONS | Интеграции и webhooks | Webhooks, aliases, matchers, extracted values, внешние триггеры | `api/integration.go`, `api/projects/integration*.go`, `hook_helpers/`, `db/Integration*.go` | Неавторизованный запуск, неверный matcher, replay, parsing payload, secret verification |
| PROJECT_DATA | Backup, restore, import и export | Экспорт/импорт проекта, backup/restore связанных сущностей | `api/projects/backup_restore.go`, `services/project/`, `services/export/`, `cli/cmd/project_*` | Потеря/дублирование данных, утечка секретов, несовместимость версий, broken references |
| UI | Веб-интерфейс | Vue-приложение, формы, таблицы, маршрутизация, отображение задач и логов | `web/src/`, `web/tests/` | Неверное состояние формы, скрытие ошибок, permissions only in UI, browser compatibility, accessibility |
| CLI | CLI и setup | Server/setup, user/project/vault commands, migrations, runner management | `cli/`, `cli/cmd/`, `cli/setup/` | Различие с API, destructive defaults, validation, exit codes, secret exposure in terminal |

## Платформенные компоненты

| Код | Компонент | Что входит | Основные области кода | Типичные риски и проверки |
|---|---|---|---|---|
| API | HTTP API и контракт | Router, middleware, request validation, response/error contracts, OpenAPI | `api/router.go`, `api/helpers/`, `api-docs.yml`, `.dredd/` | Документация расходится с кодом, неверные status codes, отсутствующая validation, несовместимые изменения |
| RBAC | Авторизация и права | Глобальные и проектные роли, permissions на ресурсы и шаблоны | `api/router.go`, auth middleware, `db/Role.go`, `db/ProjectUser.go`, `db/TemplateRole*` | IDOR, horizontal access, privilege escalation, UI скрывает разрешённый backend endpoint |
| DB | Хранилище и целостность данных | Store interfaces, SQL implementations, transactions, constraints и индексы | `db/`, `db/sql/`, `db/factory/` | Различия SQLite/MySQL/Postgres/Bolt, N+1, race, orphan data, неправильные транзакции |
| MIGRATIONS | Миграции и обновления | Schema migrations, version transitions, rekey/compatibility | `db/migration/`, `db/sql/migration*.go`, `deployment/`, `cli/cmd/migrate.go`, `cli/cmd/vault_*` | Апгрейд со старой версии, потеря данных, rollback/restart, большие БД, secret migration |
| EXECUTORS | Интеграция с исполняемыми инструментами | Ansible, Terraform/OpenTofu/Terragrunt, Bash, PowerShell, локальные команды | `db_lib/*App.go`, `services/tasks/*executor*`, `db/ansible.go` | Аргументы команд, quoting/injection, exit codes, timeouts, несовместимые версии инструментов |
| CONFIG | Конфигурация приложения | Env/config file, schema, feature flags, mail/alerts, paths | `config.schema.yaml`, `util/config.go`, `api/options.go`, `db/Option.go` | Ошибочные defaults, несовместимые env vars, validation, secret values in config/logs |
| DEPLOYMENT | Установка и упаковка | Docker, compose, systemd, deb/rpm, devcontainer, release artifacts | `deployment/`, `.devcontainer/`, Dockerfile, release workflows | Права на файлы, volume/data loss, platform/arch, upgrade path, healthcheck |
| HA | Кластер и high availability | Claims, coordination, cluster status, Pro HA boundaries | `api/cluster.go`, `pro_interfaces/ha.go`, `pro/` | Duplicate execution, split brain, stale claim, failover, consistency |
| OBSERVABILITY | Логи, метрики и диагностика | Application logs, task logs, metrics, debug log, system info | `pkg/debuglog/`, `pkg/metrics/`, `api/system_info.go`, `api/admin_info.go` | Недостаточная диагностика, PII/secrets в логах, неправильные метрики, excessive logging |
| CI | Сборка и CI проекта | Unit/integration/e2e jobs, lint, release workflows | `.github/workflows/`, `Taskfile.yml`, `.golangci.yml`, `qodana.yaml`, `.codacy.yml` | Тесты не запускаются, flaky pipeline, различие local/CI, отсутствие артефактов |
| TEST_INFRA | Тестовая инфраструктура | E2E environment, fixtures, test cases, Playwright | `test/`, `test/e2e/`, `web/tests/` | Невоспроизводимость, shared state, brittle selectors, реальные секреты, слабая диагностика |

## Поперечные теги

Эти значения не заменяют основной компонент:

| Тег | Когда использовать |
|---|---|
| `Security` | Нарушение границы доверия, обход проверки, инъекция, небезопасный default |
| `Secrets` | Возможна утечка, повреждение или неверное использование чувствительных данных |
| `RBAC` | Ошибка зависит от роли или принадлежности проекту |
| `Regression` | Ранее работавший сценарий сломан изменением |
| `Data loss` | Потеря, повреждение или необратимое изменение данных |
| `Performance` | Время, CPU, память, размер payload, DB load или масштабирование |
| `Concurrency` | Race, duplicate execution, deadlock, очередь или параллельные задачи |
| `Compatibility` | Версия ОС, браузера, БД, Ansible/Terraform или формат старых данных |
| `Upgrade` | Установка новой версии поверх существующей |
| `Documentation` | Поведение расходится с документацией или документации недостаточно |
| `UX` | Ошибка понятности, обратной связи или предотвращения пользовательской ошибки |
| `Flaky` | Результат нестабилен при одинаковых входных данных |

## Укрупнённая карта потока выполнения

```text
User / API client
  -> UI or HTTP API
  -> authentication + RBAC
  -> project resources
       repository + inventory + environment/secrets + access key
  -> template / workflow / schedule / integration
  -> task queue
  -> local executor or remote runner
  -> Ansible / Terraform / Shell / PowerShell
  -> task status + DB output + WebSocket + alerts
```

## Замечания для анализа issues

- Жалоба пользователя и корневая причина — разные поля. Например, «UI бесконечно грузится» может быть дефектом DB query или WebSocket.
- Для закрытого тикета способ исправления подтверждаем PR, commit или diff. Если такой связи нет, пишем «не установлено».
- Если тикет закрыт без исправления, это фиксируется явно: duplicate, cannot reproduce, configuration/support question, stale или won’t fix.
- Дату тикета в реестре следует понимать как дату создания; для закрытых тикетов дополнительно полезна дата закрытия.
