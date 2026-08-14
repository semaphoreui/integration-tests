# Локальное тестовое окружение

Профиль `core-sqlite-local`: минимальный стенд Semaphore UI `v2.19.7` с SQLite, локальным выполнением задач и доверенным Git fixture.

Manifest профиля находится в `profiles/<profile>/profile.yaml`. В нём закреплены версия Semaphore, способ установки, СУБД, execution mode и capabilities. Lifecycle-команда читает manifest, использует стабильное Compose project name и записывает фактическую конфигурацию и image digests в `build/allure-results/environment.properties`.

Доступны три опорных профиля:

| Профиль | СУБД | Назначение |
|---|---|---|
| `core-sqlite-local` | SQLite | быстрый основной baseline |
| `core-postgres-local` | PostgreSQL 14.3 | black-box проверка SQL dialect и миграций на чистом PostgreSQL |
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
```

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

## PostgreSQL

Переключение с SQLite на PostgreSQL с сохранением данных SQLite:

```bash
test-environment/profile down core-sqlite-local
test-environment/profile up core-postgres-local
test-environment/profile test core-postgres-local
```

PostgreSQL использует отдельные Compose project и volumes. `down` сохраняет БД, а `clean core-postgres-local --yes` удаляет только volumes этого профиля.

## Remote runner

Production-like профиль использует тот же PostgreSQL overlay, включает `SEMAPHORE_USE_REMOTE_RUNNER` и запускает `semaphoreui/runner:v2.19.7` отдельным сервисом:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

При первом старте runner регистрируется через тестовый global registration token и сохраняет выданный долгоживущий token в `runner-data`. Авторегистрация создаёт global runner с `is_default=false`, а задачи без runner tag выбирают только default runners. Поэтому one-shot `runner-configure` после регистрации входит через admin API, выставляет `is_default=true`, и lifecycle не объявляет профиль готовым до успешного завершения этой настройки.

Git fixture монтируется по одинаковому пути `/fixtures/ansible` в server и runner. Иначе локальный repository был бы доступен server, но отсутствовал бы в реальной среде исполнения задачи.

API-набор дополнительно проверяет, что runner активен, зарегистрирован, назначен default, имеет статус `online` и отправляет heartbeat. Успешные task/output и stop/force-stop сценарии при включённом remote mode подтверждают фактическое выполнение на runner.

Compose-сервис `fixture-init` создаёт отдельный Git repository из `fixtures/ansible` с ветками `main` и `bookwright-fixture-ref`. Инициализация безопасно повторяется для существующего volume и завершается ошибкой при сбое Git-команды. Repository монтируется в Semaphore read-only и используется для проверки task lifecycle, выбора ветки и отсутствующего ref. `long-running.yml` содержит marker начала, контролируемую паузу и marker завершения для детерминированной проверки stop/force-stop.

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
