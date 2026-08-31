# Semaphore UI test automation

Тестовый проект для [Semaphore UI](https://github.com/semaphoreui/semaphore), построенный на основе [Bookwright v1.4.0](https://github.com/dantro86/bookwright/releases/tag/v1.4.0) (`b30d7e6`).

## Стек

- Java 21;
- Gradle;
- JUnit 5;
- Retrofit и OkHttp;
- Playwright;
- Guice;
- AssertJ;
- Allure;
- Awaitility.

Framework адаптирован под Semaphore с сохранением архитектуры Bookwright v1.4.0: API и steps разделены как `target/domain`, сценарные данные принадлежат typed fixtures, а состояние preconditions читается только через typed `TestStore`.

## Локальный стенд

```bash
test-environment/profile up core-sqlite-local
```

Semaphore будет доступен на <http://localhost:3000>.

## Первый API smoke

```bash
test-environment/profile test core-sqlite-local
```

Команда сама проверяет readiness и добавляет точную конфигурацию стенда в Allure environment. Остановка с сохранением SQLite volume: `test-environment/profile down core-sqlite-local`. Полное удаление состояния требует явной команды `test-environment/profile clean core-sqlite-local --yes`.

Core-набор также защищает lifecycle удаления проекта: после остановки task проект и зависимые
ресурсы удаляются корректно. Known-defect canary для `v2.19.8` показывает, что удаление во время
`running` ошибочно возвращает `204`, оставляет executor работающим и заканчивается FK errors.
Воспроизведение и source boundary описаны в
`test-environment/project-deletion-running-task-defect.md`.

Static inventory проверяется в обоих штатных форматах — INI `static` и YAML `static-yaml`.
Для каждого формата сценарий сохраняет две группы с разными host aliases и выполняет template с
default `limit`. Task output подтверждает выполнение только выбранной группы.

Password-login security test сравнивает ответы для существующего и неизвестного аккаунта, проверяет
отсутствие session cookie и корректный отказ пустого пароля. На `v2.19.8` пять последовательных
ошибок остаются без throttle и audit warning; этот security gap описан в
`test-environment/password-login-brute-force-protection-gap.md`.

Тот же core-профиль выполняет plan-only сценарии на встроенных в release image Terraform 1.11.3 и
OpenTofu 1.11.0. Минимальный локальный module не скачивает providers; workspace inventories типов
`terraform-workspace` и `tofu-workspace` подтверждаются реальным output каждого tool. Привязанный
Variable Group передаёт секрет типа `env` с префиксом `TF_VAR_`: module сравнивает его SHA-256 и
выводит только безопасный marker, а тест исключает plaintext из API, structured/raw output и Allure.

Короткий browser smoke на том же стенде проверяет password login, запуск подготовленного executable template через UI и обязательность project name до отправки запроса:

```bash
./gradlew uiTest -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
```

Тот же набор выполняется без копирования тестов на PostgreSQL, MySQL и MariaDB. Профили используют общий порт и запускаются последовательно:

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

Production-like вариант выполняет задачи в отдельном persistent runner:

```bash
test-environment/profile down core-postgres-local
test-environment/profile up prod-postgres-runner
test-environment/profile test prod-postgres-runner
```

Runner регистрируется автоматически, сохраняет долгоживущий token в отдельном volume и через admin API назначается default runner. API-тесты подтверждают `active`, `registered`, `is_default`, `online`, heartbeat, exact tag routing по persisted `used_runner_id` и capacity `1`: второй task остаётся в `waiting`, пока первый занимает runner.

При отсутствии подходящего активного runner поведение отличается от capacity: `v2.19.8` переводит task в `error: no runners available`, а не сохраняет в очереди. Это воспроизведено для временно отключённого matching runner и несуществующего tag; подробности находятся в `test-environment/runner-unavailable-routing-defect.md`.

Ещё одно отличие remote execution: `v2.19.8` теряет secret survey variables перед dispatch, поэтому задача получает undefined variable. Профиль содержит безопасный known-defect canary без вывода значения; исправление upstream #4086 уже входит в `v2.20.0-alpha1`. Доказательства и критерий переключения на positive regression описаны в `test-environment/remote-runner-survey-secrets-defect.md`.

SSH feature-профиль проверяет зашифрованный access key сразу на двух клиентских границах: Git clone по SSH и подключение Ansible к удалённому target:

```bash
test-environment/profile down prod-postgres-runner
test-environment/profile up feature-ssh-local
test-environment/profile test feature-ssh-local
```

Два изолированных SSH-сервера доступны только внутри Compose network и принимают разные сгенерированные ключи. Положительный сценарий подтверждает удалённое выполнение playbook, отрицательный — полезную clone-диагностику с неверным ключом. Rotation-сценарий сначала получает отказ второго сервера со старым ключом, обновляет secret у того же access key через API и затем подтверждает успешные Git clone и Ansible SSH. Все сценарии проверяют отсутствие private key и passphrase в API и task output.

Приватный Git по HTTPS проверяется отдельно, через локальный NGINX с self-signed TLS и обязательной Basic Auth:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-git-https
test-environment/profile test feature-git-https
```

Профиль передаёт доверенный CA в Git-процессы через штатный `SEMAPHORE_FORWARDED_ENV_VARS`. Positive-сценарий выполняет playbook после authenticated clone, negative подтверждает отказ без access key. Password не попадает в API/Allure diagnostics, а login и password отсутствуют в structured/raw task output.

OIDC feature-профиль выполняет полный браузерный вход через локальный Dex и проверяет callback, session, return path, provisioning external user, повторный вход, logout, конфликт с локальным email и отказ недоступного provider:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-oidc-local
test-environment/profile test feature-oidc-local
```

Production-like OIDC-вариант повторяет тот же контракт через pinned NGINX, HTTPS и non-root public URL `/semaphore` на PostgreSQL:

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-proxy-oidc
test-environment/profile test feature-proxy-oidc
```

TLS certificate и JVM truststore генерируются локально в игнорируемом `build/test-fixtures`. Тест дополнительно проверяет `Secure`, `HttpOnly` и path session cookie, routing API/assets через subpath и возврат OIDC callback на public HTTPS origin.

Ротация database encryption keyring проверяется отдельным трёхфазным lifecycle на PostgreSQL:

```bash
test-environment/profile encryption-rotation-test feature-encryption-rotation
```

Сценарий переключает primary без рестарта, подтверждает одновременное чтение старого и запись нового ciphertext, выполняет `vault check`/`vault rekey`, удаляет retired key и повторно выполняет сохранённый template. Test-only key material генерируется в игнорируемом `build/test-fixtures/encryption-rotation`.

LDAPS feature-профиль поднимает pinned OpenLDAP, выполняет service search и user bind по TLS, а затем проверяет provisioning/reuse external user, logout, неверный пароль и защиту локального account:

```bash
test-environment/profile down feature-oidc-local
test-environment/profile up feature-ldap-tls
test-environment/profile test feature-ldap-tls
```

TOTP MFA проверяется отдельным self-contained профилем:

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-totp-local
test-environment/profile test feature-totp-local
```

Общий `totpTest` покрывает API self-enrollment, `TOTP_REQUIRED`, неверный и корректный RFC 6238 passcode, recovery, повторный enrollment и отказ уже использованного recovery code. Browser-сценарий отдельно проверяет Security settings, QR/recovery-code rendering, challenge и recovery form. OTP material редактируется в HTTP и raw Allure JSON, а чувствительные browser artifacts при падении не публикуются.

Dynamic runner profile проверяет start/finish webhook, запуск отдельного one-off runner и реальное выполнение задачи:

```bash
test-environment/profile down feature-ldap-tls
test-environment/profile up feature-dynamic-runner
test-environment/profile test feature-dynamic-runner
```

На `v2.19.8` задача завершается успешно, но one-off runner не выходит после terminal progress. Профиль оставлен ручным красным reproducer и не входит в стабильную CI matrix. Анализ и вероятная причина находятся в `test-environment/dynamic-runner-one-off-exit-defect.md`.

Экспериментальный schedule-профиль воспроизводит реальное cron/`run_at` исполнение в non-UTC timezone:

```bash
test-environment/profile down feature-ssh-local
test-environment/profile up feature-schedule-timezone
test-environment/profile test feature-schedule-timezone
```

На `v2.19.8` оба сценария локально воспроизводят дефект: активное расписание сохраняется, но task не создаётся. Профиль пока не включён в CI matrix; доказательства и ожидаемое поведение находятся в `test-environment/schedule-execution-defect.md`.

Проверка обновления опубликованных образов на сохранённой SQLite или PostgreSQL запускается отдельной командой:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

Текущий N−1 путь — `v2.19.7 → v2.19.8`. Сохранённые данные и access keys читаются на SQLite и PostgreSQL; оба upgrade-профиля прошли в Linux CI 2026-08-19. Локальные прогоны ранее ловили неполный task output после terminal status, поэтому сценарий остаётся отдельным наблюдаемым gate. Диагностика зафиксирована в `test-environment/v2.19.8-regression-report.md`; исторический schema-дефект пары `v2.19.6 → v2.19.7` — в `test-environment/upgrade-report.md`.

## CI

GitHub Actions разделены по стоимости и назначению:

- `CI` запускается для каждого pull request и push в `main`: сначала выполняет framework quality gate, затем core API suite и короткий Chromium UI smoke на `core-sqlite-local`;
- `Configuration matrix` ежедневно в `01:30 UTC` и вручную проверяет PostgreSQL, MySQL, MariaDB, production-like PostgreSQL с persistent runner, SSH, приватный HTTPS Git, прямой и HTTPS/subpath OIDC, LDAPS, TOTP и ротацию database encryption keyring;
- `Release upgrade` еженедельно по воскресеньям в `03:30 UTC` и вручную проверяет обновление `v2.19.7 → v2.19.8` на SQLite и PostgreSQL.

Matrix jobs используют отдельные GitHub-hosted runners и выполняются параллельно с `fail-fast: false`. JUnit, HTML-отчёты, Allure results и диагностика контейнеров при падении сохраняются как artifacts. Upgrade workflow не входит в PR gate; зелёный job должен означать и сохранность данных, и полную финализацию task output.

При ручном запуске `Configuration matrix` можно включить input `include_schedule_investigation`. Тогда к матрице только для этого run добавится известный красный `feature-schedule-timezone`, чтобы подтвердить schedule defect на Linux и собрать стандартные артефакты; ежедневный запуск остаётся зелёным gate без expected failures.

После каждого CI, nightly matrix или release-upgrade запуска Allure автоматически собирается в готовый HTML-сайт и загружается как artifact `allure-html-<run>-<attempt>`. Каждый Allure-отчёт собирается в single-file mode: после скачивания достаточно распаковать архив и открыть `index.html` двойным кликом — локальный HTTP-сервер не нужен. Для matrix run стартовая страница содержит отдельный отчёт каждого профиля, поэтому результаты разных СУБД не смешиваются в retries. Публикация через GitHub Pages подготовлена, но приостановлена: текущий тариф не поддерживает Pages для private-репозитория.

Тест проверяет health, неверный и корректный login, создаёт изолированный проект и основную цепочку ресурсов:

```text
project → access key → local Git repository → inventory → task template
→ task execution → success status → output marker
→ inactive cron schedule → schedule verification
→ guest RBAC → assigned project access → forbidden mutation
→ unassigned project isolation
```

После теста Bookwright LIFO cleanup удаляет проектные данные в обратном порядке. Для RBAC используется один стабильный fixture-пользователь `bookwright-rbac-guest`: повторные запуски переиспользуют его, потому что Semaphore не позволяет удалить пользователя после создания login-сессии.

Отдельный RBAC-набор фиксирует встроенные контракты `manager` и `task_runner`. Manager может создавать проектные ресурсы и запускать задачи, но не может удалить проект или управлять участниками. Task runner может запускать задачи, но получает `403` при изменении ресурсов, проекта и состава участников.

API-token-набор создаёт ограниченный по времени token, проверяет prefix-only listing, аутентифицирует отдельный Retrofit session через Bearer header и создаёт проект. После отзыва тот же token получает `401`; создание уже истёкшего token отклоняется с `400`. Полное значение не попадает в URL, step parameters или HTTP/Allure attachments: creation response намеренно скрывается, Authorization редактируется, а delete использует публичный восьмисимвольный prefix.

User lifecycle-набор проверяет поддерживаемую Community API последовательность create → update → delete → absence → recreate на одноразовом typed fixture. У модели пользователя в текущем Semaphore нет поля `active/disabled` и endpoints deactivate/reactivate, поэтому такой контракт не имитируется подменой password/delete.

File inventory-набор создаёт repository-backed `type=file`, выполняет playbook через inventory-файл из доверенного Git fixture и проверяет сохранённый `repository_id`. Отдельный безопасный canary фиксирует дефект `v2.19.8`: create принимает traversal-путь `../…`, хотя update корректно возвращает `400`; такой inventory не запускается.

Отдельный security smoke создаёт `login_password` access key с уникальным маркером, использует его как inventory credential при выполнении задачи и проверяет отсутствие plaintext в create/get/list API, структурированном и raw task output, Allure и JUnit artifacts.

Variable Group-набор создаёт смешанную группу с JSON extra vars, ENV и секретами типов `var`/`env`, переименовывает сохранённый secret без замены значения и реально выполняет `variables.yml`. Playbook проверяет секреты по SHA-256 под `no_log` и выводит только безопасный marker; тест отдельно контролирует create/get/list API и structured/raw output. Тот же контракт прошёл на SQLite и PostgreSQL `v2.19.8`; пустое имя ENV-переменной получает диагностируемый `400`.

Terraform/OpenTofu-набор отдельно проверяет нативный контракт `TF_VAR_*`: уникальный secret хранится
в Variable Group как `env`, подключается к обоим templates и действительно читается как Terraform
input variable. Provider-free module публикует marker только после совпадения хеша; plaintext не
появляется в Variable Group API, task output или HTTP/Allure diagnostics.

Build/Deploy-набор создаёт связанную пару Ansible templates и вручную выбирает успешную build-задачу
при запуске deploy. Semaphore назначает build `start_version`, передаёт её как
`SEMAPHORE_TASK_TARGET_VERSION`, сохраняет `build_task_id` у deploy и передаёт ту же версию как
`SEMAPHORE_TASK_INCOMING_VERSION`. Оба playbook сверяют API metadata с executor environment и выводят
безопасные version markers. В detail API deploy хранится связь, а отображаемая версия берётся из
вложенного `build_task` в task history — собственного поля `version` у deploy-задачи нет.

Survey/task override-набор сохраняет в template enum, integer, string, env-target и secret survey variables, затем запускает `survey-overrides.yml` с переопределёнными значениями, template/task arguments и Ansible `limit`/`tags`/`skip_tags`/`diff`/`skip_galaxy_install`. Task действительно выполняется на SQLite и PostgreSQL с local execution, secret проверяется по SHA-256 под `no_log` и отсутствует в structured/raw output. Persistent runner на `v2.19.8` теряет survey secret перед dispatch; это покрыто отдельным canary до перехода на upstream #4086. Неподдерживаемый survey target получает `400`. Также зафиксирован gap `v2.19.8`: enum default вне списка принимается backend-ом; исправление уже есть в upstream `v2.20.0-alpha1`.

Webhook integration-набор создаёт token-authenticated searchable integration, project alias, header matcher и extractors из JSON body/header. Запросы с неверным token или event не запускают task, а валидный webhook возвращает task identifiers, сохраняет связь через `integration_id` и реально передаёт extracted значения в Ansible playbook. Token хранится в `login_password` access key и редактируется в API/Allure diagnostics.

Project backup/restore-набор экспортирует конфигурацию с access keys, repository, inventory, template и schedule после реального task execution. Backup не содержит plaintext authentication secret и task history; восстановленный проект получает новые ID с корректно перелинкованными ресурсами, после чего его template снова успешно выполняется. Workflows и external Secret Storage management не имитируются на Community image: обе возможности отключены feature flags и требуют Pro test subscription для честного e2e.

Negative restore checks подтверждают запрет операции для non-admin и отклонение отсутствующей repository-ссылки. На `v2.19.8` найден общий off-by-one дефект duplicate validation: документ с двумя одинаковыми именами repository принимается и создаёт оба ресурса; canary и source boundary описаны в `test-environment/project-backup-restore-validation-defect.md`.

Concurrency-набор создаёт template с `allow_parallel_tasks=true`, чтобы не смешивать project limit с template-lock. При `max_parallel_tasks=1` первая задача доходит до marker, а вторая стабильно остаётся в `waiting`; после освобождения слота она запускается. После API-обновления проекта до лимита `2` две задачи одновременно достигают marker и обе корректно останавливаются.

Git-набор проверяет выполнение задачи из явно выбранной ветки, диагностируемый отказ для отсутствующей ветки и недоступного HTTPS remote. Для authenticated clone дополнительно проверяется, что login/password не попадают в structured и raw task output.

SSH-набор использует отдельный typed fixture и проверяет успешный Git clone по SSH, выполнение playbook на SSH target, безопасный отказ с неверным ключом и ротацию секрета без замены key ID. Две зашифрованные тестовые пары ключей генерируются в игнорируемом `build/test-fixtures/ssh`; private keys не входят ни в Git, ни в Docker build context. Строгая проверка `known_hosts` не тестируется на закреплённом `v2.19.8`, потому что соответствующая конфигурация присутствует только в более новом upstream `develop`.

Task lifecycle-набор запускает безопасный long-running playbook, дожидается marker фактического выполнения и проверяет обычный stop и force-stop. В обоих случаях задача переходит в `stopped`, а шаг после паузы не выполняется.

Ansible-код берётся только из доверенных `test-environment/fixtures/ansible`, упакованных Compose в локальный read-only Git volume с ветками `main` и `bookwright-fixture-ref`. Внешний код при API-запуске не исполняется.

Полный набор инфраструктурных self-tests Bookwright и продуктовых тестов Semaphore:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
./gradlew spotlessCheck test -DSTAND=semaphore
```

## Материалы исследования

- `semaphore-ui-testing-assessment-plan.md` — общий план;
- `semaphore-testing-component-map.md` — карта компонентов;
- `outputs/issues-assessment/` — полный реестр issues;
- `test-environment/api-map.md` — карта API и приоритеты автоматизации;
- `test-environment/legacy-qa-review.md` — разбор старых UI-тестов и ручных сценариев;
- `test-environment/configuration-testing-overview.md` — матрица клиентских конфигураций и опорные профили;
- `test-environment/smoke-report.md` — результаты проверки стенда.
- `test-environment/schedule-execution-defect.md` — воспроизводимый дефект cron/run-at execution.
- `test-environment/dynamic-runner-one-off-exit-defect.md` — воспроизводимый дефект завершения one-off runner.
- `test-environment/runner-unavailable-routing-defect.md` — fail-fast вместо recoverable queue при отсутствии matching runner.
- `test-environment/remote-runner-survey-secrets-defect.md` — потеря secret survey variables при remote dispatch.
- `test-environment/survey-default-validation-defect.md` — отсутствие enum default validation в `v2.19.8`.

Исходный код Semaphore хранится локально в `/semaphore/` и исключён из этого репозитория.
