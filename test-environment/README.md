# Локальное тестовое окружение

Профиль `core-sqlite-local`: минимальный стенд Semaphore UI `v2.19.7` с SQLite, локальным выполнением задач и доверенным Git fixture.

Manifest профиля находится в `profiles/<profile>/profile.yaml`. В нём закреплены версия Semaphore, способ установки, СУБД, execution mode и capabilities. Lifecycle-команда читает manifest, использует стабильное Compose project name и записывает фактическую конфигурацию и image digests в `build/allure-results/environment.properties`.

Доступны пять опорных профилей:

| Профиль | СУБД | Назначение |
|---|---|---|
| `core-sqlite-local` | SQLite | быстрый основной baseline |
| `core-postgres-local` | PostgreSQL 14.3 | black-box проверка SQL dialect и миграций на чистом PostgreSQL |
| `core-mysql-local` | MySQL 8.4 | black-box проверка MySQL dialect и миграций |
| `core-mariadb-local` | MariaDB 10.11 | проверка совместимости MariaDB через MySQL dialect |
| `prod-postgres-runner` | PostgreSQL 14.3 | production-like server → DB → persistent remote runner |

Общая конфигурация Semaphore и Git fixture находится в `compose.base.yml`, а профили добавляют только DB/execution-specific overlay. Все публикуют Semaphore на порту `3000`, поэтому одновременно должен быть запущен только один профиль.

## Запуск

```bash
test-environment/profile up core-sqlite-local
```

После запуска UI доступен по адресу <http://localhost:3000>.

- пользователь: `admin`
- пароль: `test-password`

## Состояние и логи

```bash
test-environment/profile ps core-sqlite-local
test-environment/profile logs core-sqlite-local
test-environment/profile logs core-sqlite-local --follow
```

Без флага команда `logs` печатает конечный снимок логов всех сервисов профиля, что подходит для CI diagnostics. Флаг `--follow` включает интерактивное слежение.

## Остановка

```bash
test-environment/profile down core-sqlite-local
```

SQLite хранится в именованном Docker volume и сохраняется между перезапусками.

Полностью пересоздать профиль вместе с volume можно только с явным подтверждением:

```bash
test-environment/profile clean core-sqlite-local --yes
test-environment/profile up core-sqlite-local
```

Продуктовые API-тесты с readiness check и Allure metadata:

```bash
test-environment/profile test core-sqlite-local
```

Список профилей и manifest выбранного профиля:

```bash
test-environment/profile list
test-environment/profile show core-sqlite-local
```

Прямой вызов `docker compose -f test-environment/compose.yml ...` сохранён для диагностики и обратной совместимости, но основной интерфейс запуска — команда `profile`.

## SQL-матрица

Переключение профилей с сохранением данных каждой СУБД:

```bash
test-environment/profile down core-sqlite-local
test-environment/profile up core-postgres-local
test-environment/profile test core-postgres-local

test-environment/profile down core-postgres-local
test-environment/profile up core-mysql-local
test-environment/profile test core-mysql-local

test-environment/profile down core-mysql-local
test-environment/profile up core-mariadb-local
test-environment/profile test core-mariadb-local
```

Каждый профиль использует отдельные Compose project и volumes. `down` сохраняет БД, а `clean <profile> --yes` удаляет только volumes выбранного профиля.

Версии MySQL 8.4 и MariaDB 10.11 совпадают с образами, на которых upstream CI Semaphore `v2.19.7` запускает migration и integration jobs. Старые официальные Compose-примеры используют MySQL 8.0 и MariaDB 10.8; их можно позже добавить в compatibility-набор после фиксации минимально поддерживаемых версий.

## Remote runner

Production-like профиль использует тот же PostgreSQL overlay, включает `SEMAPHORE_USE_REMOTE_RUNNER` и запускает `semaphoreui/runner:v2.19.7` отдельным сервисом:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

При первом старте runner регистрируется через тестовый global registration token и сохраняет выданный долгоживущий token в `runner-data`. Авторегистрация создаёт global runner с `is_default=false`, а задачи без runner tag выбирают только default runners. Поэтому one-shot `runner-configure` после регистрации входит через admin API, выставляет `is_default=true`, и lifecycle не объявляет профиль готовым до успешного завершения этой настройки.

Git fixture монтируется по одинаковому пути `/repository` в server и runner. Иначе локальный repository был бы доступен server, но отсутствовал бы в реальной среде исполнения задачи.

API-набор дополнительно проверяет, что runner активен, зарегистрирован, назначен default, имеет статус `online` и отправляет heartbeat. Успешные task/output и stop/force-stop сценарии при включённом remote mode подтверждают фактическое выполнение на runner.

## Обновление N-1 → current

Два изолированных профиля проверяют обновление release image `v2.19.6` → `v2.19.7` с сохранением одной и той же БД:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

Команда удаляет только volumes выбранного upgrade-профиля, поднимает N-1, создаёт связанный persisted fixture и выполняет задачу. Затем она пересоздаёт только server на текущем image, проверяет сохранённые project/access key/repository/inventory/template/schedule/task output, повторно выполняет старый template и запускает обычную core suite. Оба image references и digests записываются в Allure environment.

На проверенной паре релизов оба профиля сейчас ожидаемо завершаются ошибкой продукта: `v2.19.6` добавляет колонки access key из миграции `v2.20.1`, а `v2.19.7` удаляет миграцию и поля модели без rollback. Подробности и критерии исправления находятся в `upgrade-report.md`. Тест нельзя помечать skipped или expected failure: зелёный результат должен означать, что опубликованный upgrade path действительно восстановлен.

## CI-профили

Профили подключены к трём GitHub Actions workflows:

| Workflow | Триггер | Профили |
|---|---|---|
| `CI` | pull request и push в `main` | `core-sqlite-local` после framework quality gate |
| `Configuration matrix` | ежедневно `01:30 UTC`, вручную | `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner` |
| `Release upgrade` | воскресенье `03:30 UTC`, вручную | `upgrade-sqlite-local`, `upgrade-postgres-local` |

Каждый matrix profile работает на отдельном runner, поэтому общий порт `3000` не создаёт конфликтов. После выполнения workflow сохраняет JUnit/HTML/Allure artifacts, при ошибке добавляет `profile ps` и конечный снимок Compose logs, а затем удаляет только контейнеры и volumes выбранного профиля.

Raw Allure results каждого job загружаются отдельным artifact. Финальный reusable workflow скачивает их, генерирует независимый HTML-отчёт для каждого профиля и загружает общий сайт как downloadable artifact. Сборка выполняется и после тестового падения, включая pull request, поэтому диагностику красного run можно открыть без GitHub Pages. Pages deployment приостановлен, пока private-репозиторий остаётся на тарифе без private Pages.

Compose-сервис `fixture-init` создаёт отдельный Git repository из `fixtures` с ветками `main` и `bookwright-fixture-ref`, сохраняя пути относительно корня проекта под `test-environment/fixtures`. Инициализация безопасно повторяется для существующего volume и завершается ошибкой при сбое Git-команды. Repository монтируется в Semaphore read-only и используется для проверки task lifecycle, выбора ветки и отсутствующего ref. `long-running.yml` содержит marker начала, контролируемую паузу и marker завершения для детерминированной проверки stop/force-stop.

## Быстрая проверка

```bash
curl http://localhost:3000/api/ping
```

Ожидаемый ответ: `pong`.

## API smoke

Smoke создаёт отдельный проект, проверяет его и удаляет в блоке cleanup:

```bash
node test-environment/api-smoke.mjs
```

Адрес стенда и учётную запись можно переопределить:

```bash
SEMAPHORE_BASE_URL=http://localhost:3000 \
SEMAPHORE_USERNAME=admin \
SEMAPHORE_PASSWORD=test-password \
node test-environment/api-smoke.mjs
```
