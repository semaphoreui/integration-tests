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
docker compose -f test-environment/compose.yml up -d
```

Semaphore будет доступен на <http://localhost:3000>.

## Первый API smoke

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
./gradlew apiTest -DSTAND=semaphore
```

Тест проверяет health, неверный и корректный login, создаёт изолированный проект и основную цепочку ресурсов:

```text
project → access key → local Git repository → inventory → task template
→ task execution → success status → output marker
→ inactive cron schedule → schedule verification
→ guest RBAC → assigned project access → forbidden mutation
→ unassigned project isolation
```

После теста Bookwright LIFO cleanup удаляет проектные данные в обратном порядке. Для RBAC используется один стабильный fixture-пользователь `bookwright-rbac-guest`: повторные запуски переиспользуют его, потому что Semaphore v2.19.7 не позволяет удалить пользователя после создания login-сессии.

Ansible-код берётся только из доверенного fixture `test-environment/fixtures/ansible/smoke.yml`, упакованного Compose в локальный read-only Git volume. Внешний код при smoke-запуске не исполняется.

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
- `test-environment/smoke-report.md` — результаты проверки стенда.

Исходный код Semaphore хранится локально в `/semaphore/` и исключён из этого репозитория.
