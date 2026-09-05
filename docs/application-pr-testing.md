# Тестирование Pull Request основного репозитория

Тестовый и основной репозитории остаются независимыми: submodule не используются, тесты не
переносятся в основной репозиторий, а приложение — в тестовый. Разделены две независимые
настройки.

| Что определяет | Настройка | Где задаётся |
| --- | --- | --- |
| **Какие тесты запускать** | `TEST_REPOSITORY` / `TEST_BRANCH` (`git.fixtures.repository` / `git.fixtures.branch`) | [MainConfig.java](../src/main/java/io/bookwright/config/MainConfig.java), stand properties, `-D`-параметры |
| **Какую версию приложения тестировать** | `APP_REPOSITORY` / `APP_PR` либо [`application-under-test.yml`](../application-under-test.yml) | CI-переменные, `workflow_dispatch`, декларативный файл |

Семантика `TEST_REPOSITORY` / `TEST_BRANCH` не изменилась.

## Два режима

### Обычный режим (по умолчанию)

Application PR не указан. Основной репозиторий не клонируется, приложение не собирается,
временный Docker image не создаётся. Используется image из манифеста профиля
(`test-environment/profiles/<profile>/profile.yaml`, ключ `semaphore_image`) — ровно как раньше.

```text
clone tests → pull semaphore_image → start application → run tests
```

Никаких дополнительных действий при обычной разработке тестов не требуется.

### PR-режим

Тестовый прогон явно связан с Pull Request основного репозитория. Pipeline определяет HEAD SHA
этого PR, вычисляет тег временного image, переиспользует его при наличии и собирает только при
отсутствии.

```text
APP_PR → HEAD SHA → image exists? → (нет: checkout PR → build → push) → start application → run tests
```

## Связывание тестового PR с PR приложения

Связь всегда **явная**. Она никогда не выводится из названия ветки, слова `feature`, совпадения
названий веток или самого факта изменения тестовой ветки.

### Вариант 1 — декларативный файл (предпочтительный)

В корне тестового репозитория лежит [`application-under-test.yml`](../application-under-test.yml).
По умолчанию содержимое закомментировано, что соответствует обычному режиму. В тестовом PR
достаточно раскомментировать блок:

```yaml
application:
  repository: semaphoreui/semaphore
  pull_request: 123
```

`repository` необязателен и по умолчанию равен `semaphoreui/semaphore`. Принимаются как
`owner/name`, так и полные URL (`https://github.com/semaphoreui/semaphore.git`,
`git@github.com:semaphoreui/semaphore.git`).

Файл со сломанным синтаксисом приводит к ошибке pipeline, а не к молчаливому откату в обычный
режим.

### Вариант 2 — CI-переменные

`APP_REPOSITORY` и `APP_PR` имеют приоритет над файлом. В GitHub Actions они задаются входами
`workflow_dispatch` у workflow **CI**:

```bash
gh workflow run ci.yml --ref feature/BOOK-123 \
  --field application_repository=semaphoreui/semaphore \
  --field application_pull_request=123
```

## Идентификация и изоляция временных images

Тег временного image содержит номер PR и полный SHA его HEAD commit:

```text
ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-abc123456789...
```

* два разных commit одного PR дают разные images;
* несколько пар application/test PR никогда не делят один image;
* временные images лежат в отдельном namespace GHCR тестового репозитория, поэтому release-теги
  `semaphoreui/semaphore` не читаются, не перезаписываются и вообще не затрагиваются.

Namespace переопределяется переменной `APP_IMAGE_REPOSITORY`, префикс тега — `APP_IMAGE_TAG_PREFIX`.

## Повторное использование образа

Перед сборкой проверяется наличие image для вычисленного SHA:

| Ситуация | Поведение |
| --- | --- |
| Изменился только тестовый PR, SHA приложения прежний | image существует → `pull → test`, сборка не выполняется |
| В application PR появился новый commit | новый тег → `build → push → test` |
| Application PR не указан | ни клонирования, ни сборки, ни временного image |

## Автоматический запуск

### При изменении PR приложения

Workflow [`application-pr.yml`](../.github/workflows/application-pr.yml) принимает событие
`repository_dispatch` типа `application-pr-updated`, находит **все открытые тестовые PR, явно
связанные с этим PR приложения**, и запускает для них CI. Тестовые PR без связи или связанные с
другим application PR не запускаются, изменение произвольной ветки основного репозитория не
запускает ничего.

Чтобы включить автозапуск, в основной репозиторий `semaphoreui/semaphore` нужно один раз добавить
`.github/workflows/notify-integration-tests.yml`:

```yaml
name: Notify integration tests

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read

jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Notify the test repository
        env:
          GH_TOKEN: ${{ secrets.INTEGRATION_TESTS_DISPATCH_TOKEN }}
        run: |
          gh api repos/semaphoreui/integration-tests/dispatches \
            --field event_type=application-pr-updated \
            --field 'client_payload[repository]=${{ github.repository }}' \
            --field 'client_payload[pull_request]=${{ github.event.pull_request.number }}' \
            --field 'client_payload[sha]=${{ github.event.pull_request.head.sha }}'
```

`INTEGRATION_TESTS_DISPATCH_TOKEN` — токен с правом `contents: write` на тестовый репозиторий
(fine-grained PAT или GitHub App installation token). Токен хранится только в secrets и не
передаётся через параметры командной строки.

Тот же workflow запускается вручную:

```bash
gh workflow run application-pr.yml \
  --field application_repository=semaphoreui/semaphore \
  --field application_pull_request=123
```

**Ограничение fork**: у тестового PR из fork `GITHUB_TOKEN` доступен только на чтение, поэтому
такой PR нельзя ни запустить через `workflow_dispatch` (его ветки нет в тестовом репозитории), ни
использовать для push временного image. Такие PR продолжают проверяться собственным событием
`pull_request` в обычном режиме; в логе `Application PR trigger` они отмечаются явно. Для
PR-режима ветку тестового PR нужно держать в самом тестовом репозитории.

### При изменении тестового PR

Обычное событие `pull_request` workflow [`ci.yml`](../.github/workflows/ci.yml). Job
`Application source` резолвит связь, переиспользует существующий image и запускает тесты. Если
SHA приложения не изменился, сборка не выполняется.

## Авторизация

| Секрет / переменная | Назначение | Обязателен |
| --- | --- | --- |
| `GITHUB_TOKEN` (встроенный) | чтение публичного основного репозитория, push временного image в GHCR тестового репозитория | да, выдаётся автоматически |
| `APPLICATION_REPOSITORY_TOKEN` | чтение и checkout основного репозитория, если он private | только для private |
| `GHCR_CLEANUP_TOKEN` | удаление временных images (`delete:packages`) | только для очистки |
| `vars.APPLICATION_REPOSITORY` | основной репозиторий для очистки, по умолчанию `semaphoreui/semaphore` | нет |

Токены передаются только через переменные окружения и secrets. При checkout PR используется
git credential helper, читающий токен из окружения, поэтому токен не попадает ни в командную
строку, ни в репозиторий.

## Очистка временных images

Workflow [`cleanup-pr-images.yml`](../.github/workflows/cleanup-pr-images.yml) выполняется
ежедневно и удаляет версии пакета `semaphore-ci`, чей тег соответствует закрытому или
смерженному application PR, спустя окно ожидания (`RETENTION_HOURS`, по умолчанию 24 часа).
Обрабатываются только теги вида `ci-pr-<number>-<sha>` в namespace тестового репозитория —
release images не затрагиваются. Без секрета `GHCR_CLEANUP_TOKEN` workflow работает в режиме
dry-run и только сообщает кандидатов на удаление.

## Локальный запуск

Резолв без каких-либо побочных эффектов:

```bash
APP_PR=123 scripts/app-source.sh resolve
```

Сборка локального образа без публикации и прогон профиля против него:

```bash
export APP_PR=123
export APP_IMAGE_REPOSITORY=local/semaphore-ci
export APP_BUILD_PUSH=false
eval "$(scripts/app-source.sh ensure | grep '^APP_')"

test-environment/profile up core-sqlite-local
test-environment/profile test core-sqlite-local
```

`test-environment/profile` берёт image из `APP_IMAGE`, если переменная задана, и из манифеста
профиля в противном случае. Полезные переменные сборки: `APP_BUILD_PLATFORM` (по умолчанию
`linux/amd64`), `APP_DOCKERFILE` (по умолчанию `deployment/docker/server/Dockerfile`),
`APP_BUILD_PUSH`.

## Логирование

Обычный режим:

```text
Application source: Docker image
Application image: semaphoreui/semaphore:v2.19.12
Application build: skipped
```

PR-режим с переиспользованием:

```text
Application source: Pull Request
Application repository: semaphoreui/semaphore
Application PR: #123
Application SHA: abc123456789...
Application image: ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-abc123456789...
Application image already exists
Application build: skipped
```

PR-режим со сборкой:

```text
Application image not found
Building application...
Application build: completed
```

Режим также попадает в Allure environment: `application.source`, `application.repository`,
`application.pull.request`, `semaphore.image`, `semaphore.source.commit`.

## Обработка ошибок

| Ситуация | Поведение |
| --- | --- |
| PR приложения не существует | `Application PR #123 not found in <repo>`, pipeline падает |
| Нет доступа к репозиторию | `Unable to access application repository <repo>`, pipeline падает |
| Не удалось определить SHA | `Unable to resolve the HEAD SHA of application PR #123`, pipeline падает |
| PR получил новый commit во время сборки | сборка прерывается с явным сообщением о рассинхронизации |
| Не удалось собрать image | pipeline падает, Docker build logs остаются в выводе шага |
| Не удалось push-нуть image | pipeline падает после проверки, что image действительно отсутствует в registry |
| Image недоступен для pull | тег считается отсутствующим, выполняется сборка и push |
