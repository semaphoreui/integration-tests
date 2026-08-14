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

Тест проверяет health, неверный и корректный login, создаёт изолированный проект и основную цепочку ресурсов:

```text
project → access key → local Git repository → inventory → task template
→ task execution → success status → output marker
→ inactive cron schedule → schedule verification
→ guest RBAC → assigned project access → forbidden mutation
→ unassigned project isolation
```

После теста Bookwright LIFO cleanup удаляет проектные данные в обратном порядке. Для RBAC используется один стабильный fixture-пользователь `bookwright-rbac-guest`: повторные запуски переиспользуют его, потому что Semaphore v2.19.7 не позволяет удалить пользователя после создания login-сессии.

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
- `test-environment/configuration-testing-overview.md` — матрица клиентских конфигураций и опорные профили;
- `test-environment/smoke-report.md` — результаты проверки стенда.

Исходный код Semaphore хранится локально в `/semaphore/` и исключён из этого репозитория.
