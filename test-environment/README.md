# Локальное тестовое окружение

Профиль `core-sqlite-local`: минимальный стенд Semaphore UI `v2.19.7` с SQLite, локальным выполнением задач и доверенным Git fixture.

Manifest профиля находится в `profiles/core-sqlite-local/profile.yaml`. В нём закреплены версия Semaphore, способ установки, СУБД, execution mode и capabilities. Lifecycle-команда читает manifest, использует стабильное Compose project name и записывает фактическую конфигурацию в `build/allure-results/environment.properties`.

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
