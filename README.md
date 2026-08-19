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

Runner регистрируется автоматически, сохраняет долгоживущий token в отдельном volume и через admin API назначается default runner. Отдельный API-тест подтверждает `active`, `registered`, `is_default`, `online` и heartbeat до запуска task-сценариев.

Проверка обновления опубликованных образов на сохранённой SQLite или PostgreSQL запускается отдельной командой:

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

Текущий N−1 путь — `v2.19.7 → v2.19.8`. Сохранённые данные и access keys читаются на SQLite и PostgreSQL; оба upgrade-профиля прошли в Linux CI 2026-08-19. Локальные прогоны ранее ловили неполный task output после terminal status, поэтому сценарий остаётся отдельным наблюдаемым gate. Диагностика зафиксирована в `test-environment/v2.19.8-regression-report.md`; исторический schema-дефект пары `v2.19.6 → v2.19.7` — в `test-environment/upgrade-report.md`.

## CI

GitHub Actions разделены по стоимости и назначению:

- `CI` запускается для каждого pull request и push в `main`: сначала выполняет framework quality gate, затем core API suite на `core-sqlite-local`;
- `Configuration matrix` ежедневно в `01:30 UTC` и вручную проверяет PostgreSQL, MySQL, MariaDB и production-like PostgreSQL с persistent runner;
- `Release upgrade` еженедельно по воскресеньям в `03:30 UTC` и вручную проверяет обновление `v2.19.7 → v2.19.8` на SQLite и PostgreSQL.

Matrix jobs используют отдельные GitHub-hosted runners и выполняются параллельно с `fail-fast: false`. JUnit, HTML-отчёты, Allure results и диагностика контейнеров при падении сохраняются как artifacts. Upgrade workflow не входит в PR gate; зелёный job должен означать и сохранность данных, и полную финализацию task output.

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

Отдельный security smoke создаёт `login_password` access key с уникальным маркером, использует его как inventory credential при выполнении задачи и проверяет отсутствие plaintext в create/get/list API, структурированном и raw task output, Allure и JUnit artifacts.

Git-набор проверяет выполнение задачи из явно выбранной ветки, диагностируемый отказ для отсутствующей ветки и недоступного HTTPS remote. Для authenticated clone дополнительно проверяется, что login/password не попадают в structured и raw task output.

Task lifecycle-набор запускает безопасный long-running playbook, дожидается marker фактического выполнения и проверяет обычный stop и force-stop. В обоих случаях задача переходит в `stopped`, а шаг после паузы не выполняется.

Ansible-код берётся только из доверенных fixtures `test-environment/fixtures/ansible/smoke.yml` и `long-running.yml`, упакованных Compose в локальный read-only Git volume с ветками `main` и `bookwright-fixture-ref`. Внешний код при API-запуске не исполняется.

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

Исходный код Semaphore хранится локально в `/semaphore/` и исключён из этого репозитория.
