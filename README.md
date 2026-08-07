# Semaphore UI test automation

Тестовый проект для [Semaphore UI](https://github.com/semaphoreui/semaphore), построенный на основе Bookwright.

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

Тест проверяет health, неверный и корректный login, создаёт изолированный проект, проверяет роль `owner` и удаляет проект через Bookwright LIFO cleanup.

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
