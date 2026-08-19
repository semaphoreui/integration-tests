# Обзор тестирования конфигураций Semaphore UI

## Зачем нужна отдельная стратегия

Semaphore UI в основном устанавливается в инфраструктуре клиента. Ошибка может зависеть не только от API или UI, но и от сочетания способа установки, СУБД, раннера, сети, авторизации, Git и способа хранения секретов.

Проверить полный декартов продукт этих настроек невозможно и не нужно. Используем три уровня:

1. **Широкие configuration checks** — запуск процесса, readiness, валидация конфигурации и миграции на большом числе вариантов.
2. **Опорные профили** — одинаковый набор критичных API-сценариев на нескольких реалистичных конфигурациях.
3. **Feature profiles** — отдельные проверки только для LDAP, OIDC, HA, remote runner, encryption rotation и других специальных возможностей.

## Подтверждённые варианты конфигурации

### Способ установки

Официально документированы:

- Docker и Docker Compose;
- DEB/RPM через package manager;
- standalone binary и запуск через systemd;
- Kubernetes через официальный Helm chart;
- установка в облачной инфраструктуре как один из вариантов размещения.

Snap помечен в документации как deprecated и не должен входить в основную матрицу.

Источники: [Installation overview](https://semaphoreui.com/docs/admin-guide/installation), [Package manager](https://semaphoreui.com/docs/admin-guide/installation/package-manager), [Binary file](https://semaphoreui.com/docs/admin-guide/installation/binary-file), [deprecated Snap](https://semaphoreui.com/docs/administration-guide/installation/snap).

Релизная конфигурация в `.goreleaser.yml` также выпускает бинарные файлы для нескольких OS/architecture и DEB/RPM-пакеты. Docker CI собирает как минимум `linux/amd64` и `linux/arm64`.

### СУБД и состояние

В `config.schema.yaml` поддержаны три dialect:

- SQLite;
- MySQL;
- PostgreSQL.

MariaDB использует MySQL dialect, но upstream CI запускает её отдельно. Это правильно: совместимость драйвера не гарантирует одинаковое поведение двух серверов.

Отдельная ось — не чистая установка, а обновление существующего состояния:

- предыдущий релиз → текущий релиз;
- миграции каждой поддерживаемой СУБД;
- резервное копирование и восстановление проекта;
- BoltDB → SQLite для оставшегося поддерживаемого migration path.

### Архитектура выполнения задач

Поддерживаются два принципиально разных режима:

- выполнение задач локально процессом сервера;
- выполнение на отдельно зарегистрированном remote runner.

Runner может быть постоянным или one-off. Для динамического one-off runner сервер умеет вызывать webhook, после чего созданный runner регистрируется и забирает задачу. Есть ограничения параллельности и привязка runner к проекту; tags относятся к Pro-возможностям.

В конфигурации runner обнаружены executor:

- `local` — subprocess на машине runner;
- `docker` — отдельный контейнер для задачи;
- `k8s` — ephemeral Kubernetes pod.

Docker/Kubernetes executor реализованы в `pro/`, поэтому их нужно считать отдельной Pro-матрицей и запускать только при наличии доступной Pro-сборки/лицензии. Источник по эксплуатации runner: [Runners](https://semaphoreui.com/docs/admin-guide/cli/runners).

### Источник конфигурации и сеть

Semaphore читает JSON/YAML config и environment variables; путь задаётся через `SEMAPHORE_CONFIG_PATH`/`--config`, также есть запуск без config file. Environment variables могут переопределять поля файла.

Сетевые варианты, влияющие на поведение:

- прямой HTTP;
- встроенный TLS и HTTP redirect;
- reverse proxy с TLS termination;
- публикация в корне домена или под subpath через web root;
- собственный CA между runner и сервером;
- один server node или HA nodes с Redis и общей SQL DB.

Проверять каждый бизнес-тест для JSON, YAML и env не нужно. Достаточно отдельного config-contract набора, который доказывает чтение, override, ошибку неизвестного/некорректного значения и отсутствие секрета в логах.

Источник: [Configuration](https://semaphoreui.com/docs/admin-guide/configuration).

### Авторизация

Возможные режимы:

- локальная учётная запись и пароль;
- LDAP, включая несколько providers и TLS;
- OIDC, включая несколько providers, mapping claims и правила связывания аккаунтов;
- TOTP/email MFA;
- отключение password login.

Авторизацию не смешиваем со всей DB-матрицей. Для LDAP и OIDC нужны самостоятельные профили с негативными сценариями account mapping, callback, logout, TLS и RBAC после входа.

Источники: [LDAP](https://semaphoreui.com/docs/admin-guide/ldap), [OpenID](https://semaphoreui.com/docs/admin-guide/openid).

### Git, ключи и секреты

Оси, которые непосредственно влияют на основной task flow:

- локальный/file repository, HTTPS и SSH;
- `cmd_git` и `go_git` clients;
- ветки, tags/refs, submodules, known_hosts и custom SSH config;
- password, SSH key и другие access key types;
- локальное шифрование access keys: legacy key или keyring file с rotation;
- внешние secret storage implementations, найденные в коде: Vault, environment и file.

Эти варианты выгоднее проверять небольшими feature-наборами поверх одного стабильного DB-профиля, а не умножать на все СУБД.

### Инструмент внутри задачи

В исходном коде есть приложения шаблонов:

- Ansible;
- Terraform;
- OpenTofu;
- Terragrunt;
- shell;
- PowerShell.

Ansible остаётся базовым сквозным fixture. Для остальных инструментов нужны по одному минимальному успешному сценарию и характерные ошибки установки/исполнения; проверять каждый из них на каждой СУБД не требуется.

## Что уже проверяет upstream CI

На исследованном commit upstream отдельно запускает migrate/integration jobs для SQLite, MySQL, MariaDB и PostgreSQL. Это снижает необходимость дублировать всю внутреннюю Go integration suite, но не заменяет black-box проверку опубликованного образа:

- upstream проверяет свой build, а мы сейчас запускаем release image;
- DB jobs не доказывают сквозной task lifecycle с реальным Git/Ansible;
- они не покрывают клиентскую упаковку, reverse proxy, external auth и upgrade сохранённого стенда как продуктовый сценарий.

Версии СУБД нельзя брать «последние» неявно. Каждый профиль должен фиксировать exact image tag, а периодический compatibility job — отдельно проверять заявленные минимальную и актуальную версии после согласования support policy.

## Предлагаемые опорные профили

| ID | Конфигурация | Зачем | Запуск |
|---|---|---|---|
| `core-sqlite-local` | release Docker image, SQLite, local execution, env config, password auth, `cmd_git` | Самый дешёвый smoke и удобная локальная разработка | каждый PR |
| `core-postgres-local` | Docker Compose, PostgreSQL 14.3, local execution | Black-box совместимость PostgreSQL и миграций без runner-specific переменных | nightly |
| `prod-postgres-runner` | Docker Compose, PostgreSQL, отдельный persistent runner с local executor, config file | Наиболее полезная проверка production-like границы server ↔ DB ↔ runner | nightly; после стабилизации — PR gate |
| `core-mysql-local` | Docker Compose, MySQL 8.4, local execution | Black-box совместимость MySQL и миграций | nightly |
| `core-mariadb-local` | Docker Compose, MariaDB 10.11, local execution | Реальная совместимость MySQL dialect с MariaDB | nightly |
| `feature-ssh-local` | SQLite, два локальных SSH server с разными зашифрованными test keys | Git clone и Ansible target по SSH, key rotation, negative auth и защита секретов | nightly |
| `feature-oidc-local` | SQLite и pinned локальный Dex | Discovery, browser login, callback, session/logout, return path, provisioning, repeat login, local-email conflict и provider failure | nightly |
| `feature-ldap-tls` | SQLite и pinned OpenLDAP с TLS | LDAPS service/user bind, search/mapping, provisioning/reuse, logout, invalid password и local-email conflict | nightly |
| `feature-schedule-timezone` | SQLite, `Pacific/Kiritimati`, local execution | Реальное cron/run-at исполнение и связь schedule → task | вручную; defect reproducer |
| `proxy-oidc` | PostgreSQL, reverse proxy TLS, non-root web path, OIDC provider | Callback URL, cookies, redirects, account mapping и RBAC | nightly/по расписанию |
| `ha-two-node` | два server nodes, PostgreSQL, Redis, remote runner | Очередь, session/state consistency и отказ одного node | по расписанию |
| `feature-dynamic-runner` | SQLite и one-off runner, запущенный через webhook | Start/finish webhook, ровно одна задача и завершение runner process | вручную; defect reproducer |
| `pro-docker-executor` | Pro runner с Docker executor | Изоляция task container, лимиты, cleanup, secret hydration | при наличии Pro, nightly |
| `pro-k8s-executor` | Helm/Pro runner с Kubernetes executor | pod lifecycle, service account, pull secret и cleanup | при наличии Pro/K8s, release |

Базовые пять профилей и пять feature-профилей реализованы. `feature-oidc-local` и `feature-ldap-tls` дают зелёные positive и negative auth paths без размножения по DB-матрице. `feature-schedule-timezone` воспроизводит отсутствие cron/run-at tasks, а `feature-dynamic-runner` — незавершающийся one-off runner после успешной task. Оба профиля остаются ручными красными reproducer. Следующий infrastructure-профиль — HA.

CI-распределение также реализовано: `core-sqlite-local` входит в pull-request gate после framework quality checks; остальные четыре базовых профиля, `feature-ssh-local`, `feature-oidc-local` и `feature-ldap-tls` запускаются ежедневной matrix job; два release-upgrade профиля запускаются отдельной еженедельной и ручной проверкой. Upgrade workflow сознательно не входит в PR gate.

## Какие тесты где запускать

| Набор | SQLite | PostgreSQL local | PostgreSQL + runner | MySQL | MariaDB | Feature profile |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| health, login, project CRUD | ✓ | ✓ | ✓ | ✓ | ✓ | короткий smoke |
| Git → template → task → output → cleanup | ✓ | ✓ | ✓ | ✓ | ✓ | если применимо |
| RBAC и project isolation | ✓ | ✓ | ✓ | ✓ | ✓ | auth profiles расширяют набор |
| task stop/force-stop | local | local | remote | local | local | runner profiles |
| runner registration/default/heartbeat | — | — | ✓ | — | — | runner profiles |
| dynamic start/finish webhook и one-off exit | — | — | — | — | — | `feature-dynamic-runner`, defect |
| Git over SSH, SSH inventory и key rotation | — | — | — | — | — | `feature-ssh-local` |
| реальное cron/run-at execution | — | — | — | — | — | `feature-schedule-timezone`, defect |
| constraints, schedules, cleanup, clean migration | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| secrets и отсутствие утечек | ✓ | ✓ | ✓ | ✓ | ✓ | encryption/storage расширяют набор |
| OIDC/LDAP/MFA | — | — | — | — | — | OIDC: `feature-oidc-local`; LDAP: `feature-ldap-tls`; MFA — будущий профиль |
| HA/failover | — | — | — | — | — | только HA profile |

Знак `—` означает сознательное исключение, а не неизвестное покрытие. Это важно фиксировать, иначе матрица со временем снова превратится в неявный полный перебор.

## Отдельный release-набор

Перед релизом важнее не повторить все API-тесты, а проверить клиентский путь установки и обновления:

1. clean install Docker image, DEB/RPM и Helm;
2. создание небольшого, но связанного набора данных;
3. остановка и обновление с N-1 до current;
4. автоматические DB migrations;
5. вход, чтение старых данных и запуск старого template после обновления;
6. проверка schedules, access keys и encryption keys после рестарта;
7. backup/restore;
8. короткий artifact smoke на `amd64` и `arm64`.

Полную business suite достаточно оставить на Docker. Package/binary/Helm проверяют упаковку, persistence, permissions, readiness и обновление.

## Как организовать это в тестовом проекте

Не следует копировать Java-тесты или создавать отдельные классы на каждую СУБД. Инфраструктура выбирает профиль, а один и тот же тестовый набор работает с опубликованными capabilities стенда.

Для каждого профиля нужен manifest со следующими полями:

```yaml
id: prod-postgres-runner
semaphore:
  image: semaphoreui/semaphore:v2.19.7
  source_commit: e9dc41a1de8a747569334f7a2b76c320b945d4f0
  edition: community
installation: docker-compose
architecture: arm64
database:
  type: postgres
  image: postgres:<pinned-version>
execution:
  mode: remote-runner
  executor: local
auth: password
git_client: cmd_git
capabilities:
  - core-api
  - task-execution
  - remote-runner
  - schedules
```

Manifest должен попадать в Allure environment/labels вместе с image digest. Тогда любое падение можно связать с точной конфигурацией, а тесты с unsupported capability можно пропустить с понятной причиной.

Предлагаемый интерфейс запуска:

```bash
./test-environment/profile up core-sqlite-local
./gradlew test -DSTAND=semaphore -DSEMAPHORE_PROFILE=core-sqlite-local
./test-environment/profile down core-sqlite-local
```

Команда `profile` реализована для `core-sqlite-local`, `core-postgres-local`, `core-mysql-local`, `core-mariadb-local`, `prod-postgres-runner`, `feature-ssh-local`, `feature-oidc-local`, `feature-ldap-tls`, `feature-schedule-timezone`, `feature-dynamic-runner` и upgrade-профилей: она управляет Compose lifecycle, ждёт readiness/setup services, запускает выбранный в manifest API/UI test task и записывает manifest/runtime metadata и image digests в Allure. Следующие профили подключаются через тот же интерфейс.

## Обнаруженный риск воспроизводимости

Текущий тестовый Compose закреплён на release image `v2.19.8` (tag commit `3449a04f3bfa2522ec7fd60803f71b578c39f6b4`), а локальная копия upstream `develop` находится на commit `ae12f3acac626f78673b95cc57acd62ed873b089`.

Значит, API schema и детали конфигурации нельзя автоматически считать соответствующими запущенному image. До расширения матрицы нужно выбрать одно из двух правил:

- тестируем release image и берём schema/source из соответствующего tag;
- тестируем сборку текущего source commit и сохраняем commit как версию стенда.

Для регрессионной системы лучше поддержать оба типа профиля: release image для клиентского сценария и source build для ранней проверки будущего релиза.

## Рекомендуемая последовательность

1. Ввести manifest и единый lifecycle профиля.
2. Перенести существующий стенд в `core-sqlite-local` без изменения тестов. Выполнено.
3. Добавить PostgreSQL и remote runner. Выполнено: `core-postgres-local` и `prod-postgres-runner` проходят существующую core suite; runner API дополнительно подтверждает default/online/heartbeat contract.
4. Добавить короткую DB-матрицу MySQL/MariaDB. Выполнено: профили `core-mysql-local` на MySQL 8.4 и `core-mariadb-local` на MariaDB 10.11 проходят ту же core suite после миграции чистой схемы; фактические image digests попадают в Allure.
5. Реализовать N-1 → current upgrade для SQLite и PostgreSQL. Выполнено: `upgrade-sqlite-local` и `upgrade-postgres-local` создают данные на `v2.19.7`, переключают server image на `v2.19.8` с сохранением БД и запускают verify/core suite. Оба профиля прошли в Linux CI; локально сохраняется наблюдение за гонкой финализации task output.
6. Добавить SSH feature-профиль. Выполнено: Git clone, Ansible SSH target, неверный ключ, замена secret у существующего key ID и защита key material проверяются на двух изолированных SSH fixtures. `known_hosts` откладывается до релиза с соответствующей upstream-конфигурацией.
7. Добавить реальное schedule execution. Reproducer реализован для cron и `run_at`; отсутствие task на `v2.19.8` подтверждено локально и в Linux CI. Следующий шаг — upstream issue/fix verification.
8. Добавить OIDC feature-профиль. Выполнено: pinned Dex, discovery, browser login, callback, session/logout, return path, provisioning, repeat login, local-email conflict и provider failure проходят локально на `v2.19.8`.
9. Добавить LDAP с TLS. Выполнено: pinned OpenLDAP, LDAPS service/user bind, search/mapping, provisioning/reuse, logout, invalid password и local-email conflict проходят локально на `v2.19.8`.
10. Добавить dynamic one-off runner. Выполнено как ручной reproducer: webhook запускает runner, task завершается успешно и `finish` webhook приходит, но процесс не выходит на `v2.19.8`. Вероятная недостижимость exit condition зафиксирована в `dynamic-runner-one-off-exit-defect.md`; профиль не добавлен в зелёную CI matrix.
11. Следующий infrastructure-профиль — HA на PostgreSQL/Redis с двумя server nodes и remote runner.

Так мы сначала защищаем типичную установку клиента и самые дорогие точки отказа, но сохраняем окружение понятным для одного инженера.
