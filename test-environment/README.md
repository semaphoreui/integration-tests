# Локальное тестовое окружение

Профиль `core-sqlite-local`: минимальный стенд Semaphore UI `v2.19.12` с SQLite, локальным выполнением задач и доверенным Git fixture.

Manifest профиля находится в `profiles/<profile>/profile.yaml`. В нём закреплены версия Semaphore, способ установки, СУБД, execution mode и capabilities. Lifecycle-команда читает manifest, использует стабильное Compose project name и записывает фактическую конфигурацию и image digests в `build/allure-results/environment.properties`.

Доступны пять опорных профилей и десять feature-профилей:

| Профиль | СУБД | Назначение |
|---|---|---|
| `core-sqlite-local` | SQLite | быстрый основной baseline |
| `core-postgres-local` | PostgreSQL 14.3 | black-box проверка SQL dialect и миграций на чистом PostgreSQL |
| `core-mysql-local` | MySQL 8.4 | black-box проверка MySQL dialect и миграций |
| `core-mariadb-local` | MariaDB 10.11 | проверка совместимости MariaDB через MySQL dialect |
| `prod-postgres-runner` | PostgreSQL 14.3 | production-like server → DB → persistent remote runner |
| `feature-ssh-local` | SQLite | Git over SSH, Ansible SSH target и защита key material |
| `feature-git-https` | SQLite | приватный Git over HTTPS, Basic Auth, доверенный self-signed CA и защита credentials |
| `feature-oidc-local` | SQLite | browser login через Dex, session/logout, provisioning и negative account/provider scenarios |
| `feature-proxy-oidc` | PostgreSQL 14.3 | OIDC через NGINX, HTTPS и non-root public path `/semaphore` |
| `feature-ldap-tls` | SQLite | LDAPS bind/search, provisioning/reuse user, logout и negative credential/account scenarios |
| `feature-totp-local` | SQLite | API и browser TOTP: Security/QR, challenge, invalid passcode и recovery lifecycle |
| `feature-encryption-rotation` | PostgreSQL 14.3 | hot reload keyring, mixed-key reads, vault rekey и удаление retired key |
| `feature-schedule-timezone` | SQLite | cron/run-at execution в `Pacific/Kiritimati`; локальный defect reproducer |
| `feature-dynamic-runner` | SQLite | webhook-launched one-off runner; defect reproducer для незавершающегося процесса |
| `feature-shell-output` | SQLite | строгий defect reproducer потери `stdout`/`stderr` короткой task |

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

После поднятия того же профиля минимальный browser smoke запускается отдельно:

```bash
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
```

Он проверяет password login, реальный запуск API-подготовленного template через форму и client-side validation пустого project name. Validation-сценарий дополнительно доказывает, что `POST /api/projects` не отправлялся.

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

Версии MySQL 8.4 и MariaDB 10.11 закреплены в тестовой матрице и прошли core suite на Semaphore `v2.19.8`. Старые официальные Compose-примеры используют MySQL 8.0 и MariaDB 10.8; их можно позже добавить в compatibility-набор после фиксации минимально поддерживаемых версий.

## Remote runner

Production-like профиль использует тот же PostgreSQL overlay, включает `SEMAPHORE_USE_REMOTE_RUNNER` и запускает `semaphoreui/runner:v2.19.12` отдельным сервисом:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

При первом старте runner регистрируется через тестовый global registration token и сохраняет выданный долгоживущий token в `runner-data`. Авторегистрация создаёт global runner с `is_default=false`, а задачи без runner tag выбирают только default runners. Поэтому one-shot `runner-configure` после регистрации входит через admin API, выставляет `is_default=true`, и lifecycle не объявляет профиль готовым до успешного завершения этой настройки.

Git fixture монтируется по одинаковому пути `/fixtures/ansible` в server и runner. Иначе локальный repository был бы доступен server, но отсутствовал бы в реальной среде исполнения задачи.

API-набор дополнительно проверяет, что runner активен, зарегистрирован, назначен default, имеет статус `online` и отправляет heartbeat. Успешные task/output и stop/force-stop сценарии при включённом remote mode подтверждают фактическое выполнение на runner.

На `v2.19.8` профиль также содержит known-defect canary: secret survey variable теряется перед remote dispatch, хотя тот же launch проходит при local execution. Canary не печатает значение секрета; upstream-исправление #4086 и критерий удаления workaround описаны в `remote-runner-survey-secrets-defect.md`.

## SSH feature-профиль

```bash
test-environment/profile down prod-postgres-runner
test-environment/profile up feature-ssh-local
test-environment/profile test feature-ssh-local
```

Профиль собирает минимальный Alpine SSH fixture и монтирует в него локальный Git repository read-only. Один зашифрованный Semaphore access key используется для clone `ssh://fixture@ssh-fixture:22/repositories/ansible` и подключения Ansible к `ssh-fixture`. Отдельный negative-сценарий проверяет неверный ключ и clone failure. Create/get/list responses, structured output и raw output проверяются на отсутствие private key и passphrase.

Пара ключей генерируется при `profile up` в игнорируемом Git каталоге `build/test-fixtures/ssh`. В контейнер монтируется только public key, а private key остаётся вне Docker build context и используется Java-тестом только для локального API. Версия fixture image записывается в Allure environment.

## Private HTTPS Git feature-профиль

Приватный HTTPS Git fixture поднимает pinned NGINX, публикует bare-репозиторий только внутри Compose network и требует Basic Auth. Self-signed CA генерируется в игнорируемом `build/test-fixtures/git-https` и передаётся дочерним Git-процессам через разрешённую переменную окружения:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-git-https
test-environment/profile test feature-git-https
```

## Schedule timezone feature-профиль

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

Профиль задаёт `SEMAPHORE_SCHEDULE_TIMEZONE=Pacific/Kiritimati`, передаёт ту же зону в test JVM и записывает её в Allure environment. Тесты рассчитывают ближайший cron в этой зоне и отдельный `run_at`, затем ожидают автоматически созданную task по `schedule_id` и её успешный output.

На release `v2.19.8` профиль сейчас является defect reproducer: API сохраняет активные cron и one-shot schedules, но task не появляется. Он сознательно не включён в CI matrix до Linux-подтверждения и решения по upstream issue. Полный отчёт — `schedule-execution-defect.md`.

## Shell output feature-профиль

```bash
test-environment/profile down feature-schedule-timezone
test-environment/profile up feature-shell-output
test-environment/profile test feature-shell-output
```

На release `v2.19.12` короткая успешно завершённая Bash-задача может сохранить только один из
потоков процесса: `stdout` или `stderr`. Профиль запускает только строгий `ShellOutputTest`,
включая background-child сценарий, и остаётся ручным красным reproducer до появления уже
существующих upstream-исправлений в stable. Полный отчёт — `shell-output-loss-defect.md`.

## OIDC feature-профиль

```bash
test-environment/profile down feature-schedule-timezone
test-environment/profile up feature-oidc-local
test-environment/profile test feature-oidc-local
```

Профиль запускает pinned Dex `v2.45.1` и браузерный `uiTest`. Positive path проверяет provider button, credentials на IdP, OAuth callback, Semaphore session через `/api/user`, возврат на `/tokens` и provisioning non-admin external user. Повторный вход доказывает reuse того же user ID, logout очищает session, а отдельные negative paths защищают локальный account при совпадении email и не создают session при отказе discovery. `SEMAPHORE_WEB_ROOT` задан явно, а claims `username`/`name` маппятся на `email`, потому что локальный Dex connector не выдаёт `preferred_username`.

## HTTPS proxy + OIDC feature-профиль

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-proxy-oidc
test-environment/profile test feature-proxy-oidc
```

Профиль использует PostgreSQL 14.3, Dex и pinned NGINX `1.27.5-alpine`. Semaphore публикуется как `https://localhost:3443/semaphore`; proxy сохраняет subpath и поддерживает WebSocket upgrade. Lifecycle генерирует localhost certificate и PKCS12 truststore в `build/test-fixtures/proxy-tls`, передаёт truststore только test JVM и ждёт readiness через доверенный HTTPS endpoint.

Тот же OIDC-набор проверяет discovery, callback, return path, provisioning/reuse, logout и negative account/provider paths. После успешного входа отдельно подтверждаются `HttpOnly`, `Secure` и cookie path `/`. Сертификат, truststore и private key являются disposable fixtures и не входят в Git.

## LDAPS feature-профиль

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-ldap-tls
test-environment/profile test feature-ldap-tls
```

Профиль запускает pinned OpenLDAP `1.5.0` с самоподписанным TLS и тремя directory users. Semaphore подключается по LDAPS `636`, делает service bind, search по `uid`, user bind и mapping `uid`/`cn`/`mail`. Четыре сценария проверяют provisioning external user, reuse того же ID после logout, отказ неверного пароля без provisioning и защиту локального admin при совпадении email.

## TOTP feature-профиль

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-totp-local
test-environment/profile test feature-totp-local
```

Профиль явно включает TOTP и recovery и запускает общий `totpTest`. API-сценарий создаёт отдельного non-admin пользователя, выполняет self-enrollment, получает `otpauth://` material, проверяет состояние `TOTP_REQUIRED`, отказ изменённого passcode и успешный вход с RFC 6238-кодом. Затем recovery code восстанавливает сессию и удаляет старую TOTP-привязку; повторный enrollment выпускает новый recovery code, а старый получает `INVALID_RECOVERY_CODE`.

Независимый browser-сценарий включает TOTP через вкладку Security, проверяет загрузку QR и показ recovery code, выходит из сессии, проходит password → challenge flow с отрицательной и положительной проверкой passcode, а затем использует UI recovery form. API после восстановления подтверждает удаление TOTP-привязки.

OTP secret, passcode и recovery code не попадают в HTTP attachments и raw Allure result JSON. Шаги принимают redacted request-объекты: одного визуального `hidden`-режима Allure недостаточно, поскольку он оставляет исходное значение внутри downloadable artifact. Для browser-сценария при падении сохраняется только безопасная browser diagnostics; screenshot, HTML и Playwright trace отключены, потому что могут содержать QR, recovery code или введённый passcode.

## Dynamic one-off runner

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

Профиль регистрирует global runner с webhook, назначает его default и на `start` запускает отдельный `semaphore runner start --no-config` с `SEMAPHORE_RUNNER_ONE_OFF=true`. Launcher записывает события `webhook_start`, `runner_started`, `webhook_finish` и фактический exit code процесса, но сам процесс не останавливает.

На `v2.19.8` task успешно выполняется и сервер вызывает `finish`, однако runner остаётся жив. Тест намеренно красный, потому что ожидает `runner_exited` с кодом `0`. Профиль не входит в обычную CI matrix; полная репродукция и анализ исходного кода — в `dynamic-runner-one-off-exit-defect.md`.

## Rotation ключей шифрования БД

```bash
test-environment/profile encryption-rotation-test feature-encryption-rotation
```

Специализированная команда пересоздаёт только volumes этого профиля и проводит три фазы на PostgreSQL. Сначала Semaphore создаёт зашифрованный `login_password` access key и выполняет template со старым primary. Затем keyring атомарно переключается на новый primary без рестарта: старый secret по-прежнему читается, а новый записывается уже с другим key ID. Команда `semaphore vault check` должна показать одновременно `retired, rekey pending` и `active`.

После `semaphore vault rekey --backup` lifecycle требует для конкретного старого key ID состояние `0 rows — retired, SAFE TO REMOVE`, убирает его из keyring и снова запускает сохранённый template. Финальная фаза также создаёт новый secret, поэтому проверяет и чтение rekeyed ciphertext, и запись после удаления retired key. Сгенерированные test-only keys находятся в игнорируемом `build/test-fixtures/encryption-rotation` и не входят в Git.

## Обновление N-1 → current

Два изолированных профиля проверяют обновление release image `v2.19.8` → `v2.19.12` с сохранением одной и той же БД:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

Команда удаляет только volumes выбранного upgrade-профиля, поднимает N-1, создаёт связанный persisted fixture и выполняет задачу. Затем она пересоздаёт только server на текущем image, проверяет сохранённые project/access key/repository/inventory/template/schedule/task output, повторно выполняет старый template и запускает обычную core suite. Оба image references и digests записываются в Allure environment.

Пара `v2.19.8` → `v2.19.12` является текущим upgrade gate и успешно прошла на SQLite и
PostgreSQL в Linux CI 2026-09-04. Предыдущая пара `v2.19.7` → `v2.19.8` также читала сохранённые
project/access key/repository/inventory/template/schedule/task и повторно выполняла template на
обеих СУБД 2026-08-19. Upgrade workflow остаётся отдельным наблюдаемым gate, потому что проверяет
миграцию сохранённого состояния между release images.
Подробности — в `v2.19.8-regression-report.md`; исторический schema-дефект
`v2.19.6` → `v2.19.7` сохранён в `upgrade-report.md`.

## CI-профили

Профили подключены к трём GitHub Actions workflows:

| Workflow | Триггер | Профили |
|---|---|---|
| `CI` | pull request и push в `main` | API suite и Chromium UI smoke на `core-sqlite-local` после framework quality gate |
| `Configuration matrix` | ежедневно `01:30 UTC`, вручную | `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner`, `feature-ssh-local`, `feature-git-https`, `feature-oidc-local`, `feature-proxy-oidc`, `feature-ldap-tls`, `feature-totp-local`, `feature-encryption-rotation` |
| `Release upgrade` | воскресенье `03:30 UTC`, вручную | `upgrade-sqlite-local`, `upgrade-postgres-local` |

Каждый matrix profile работает на отдельном runner, поэтому общий порт `3000` не создаёт конфликтов. После выполнения workflow сохраняет JUnit/HTML/Allure artifacts, при ошибке добавляет `profile ps` и конечный снимок Compose logs, а затем удаляет только контейнеры и volumes выбранного профиля.

На stable `v2.19.12` команда `profile test` выполняет JUnit-классы последовательно из-за
подтверждённой гонки product output collector. Это не отключает проверку конкурентного выполнения:
`ProjectConcurrencyApiTest` сам запускает несколько Semaphore tasks и проверяет queue admission.
Строгий конкурентный/short-output контракт изолирован в `feature-shell-output`.

В ручном `Configuration matrix` inputs `include_schedule_investigation=true` и
`include_shell_output_investigation=true` добавляют соответствующие defect-профили только к
выбранному run. Их ожидаемое до исправления падение не загрязняет ежедневный gate, но сохраняет
Linux diagnostics для подтверждения дефектов.

Raw Allure results каждого job загружаются отдельным artifact. Финальный reusable workflow скачивает их, генерирует независимый HTML-отчёт для каждого профиля и загружает общий сайт как downloadable artifact. Сборка выполняется и после тестового падения, включая pull request, поэтому диагностику красного run можно открыть без GitHub Pages. Pages deployment приостановлен, пока private-репозиторий остаётся на тарифе без private Pages.

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
