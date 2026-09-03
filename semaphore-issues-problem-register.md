# Реестр проблем Semaphore UI

**Дата начала анализа:** 2026-08-06  
**Репозиторий:** [semaphoreui/semaphore](https://github.com/semaphoreui/semaphore)  
**Текущий охват:** первичная проверенная выборка — свежие bug-issues, открытые тикеты с меткой `critical` и несколько закрытых/исправленных дефектов для проверки формата.

## Как читать таблицу

- **Дата** — дата создания issue.
- **Что сломалось** — наблюдаемое поведение, а не предполагаемая корневая причина.
- **Что/как починили** заполняется только при наличии PR, commit, diff или однозначного комментария сопровождающего.
- `Не установлено` означает, что исправление может существовать, но его нельзя надёжно связать с тикетом по доступным данным.
- Компоненты соответствуют [карте компонентов](./semaphore-testing-component-map.md).

## Первичная таблица

| Дата | Тикет / статус | На что жаловался пользователь | Что сломалось | Что починили | Как починили | Компонент |
|---|---|---|---|---|---|---|
| 2026-07-21 | [#4075](https://github.com/semaphoreui/semaphore/issues/4075) · open | При создании Cron или Run once schedule указанные template variables исчезают сразу после сохранения | Переменные пусты при повторном открытии формы и не передаются в scheduled job | Пока не починено | Fix/PR не найден | `SCHEDULES`; дополнительно `TEMPLATES`, `UI`, `Regression` |
| 2025-01-08 | [#2682](https://github.com/semaphoreui/semaphore/issues/2682) · open · critical | Встроенный в Docker image Ansible 2.18 несовместим с Python 3.6 на управляемых RHEL 8-хостах | Ansible modules завершаются с `SyntaxError: future feature annotations is not defined`; стандартный image нельзя использовать для таких targets | Полного решения в тикете нет; появился вариант image с Ansible 2.16, пользователи также используют custom image | Сопровождающий предложил custom Dockerfile; обсуждается несколько образов/Execution Environments. Тикет остаётся открытым | `EXECUTORS`; дополнительно `DEPLOYMENT`, `Compatibility`, `Ansible` |
| 2025-01-08 | [#2681](https://github.com/semaphoreui/semaphore/issues/2681) · open · critical | После успешного OIDC login при пустом `web_host` пользователь попадает на 404 вместо UI | Сессия создаётся, но redirect строится как относительный путь и браузер открывает `/api/auth/oidc/.../project/1` | Подготовлено исправление, но на дату среза не влито | [PR #3831](https://github.com/semaphoreui/semaphore/pull/3831): выделяет `buildOidcRedirectURL`, принудительно добавляет начальный `/` при пустом `web_host`, добавляет table-driven tests в `api/login_test.go` | `AUTH`; дополнительно `CONFIG`, `API`, `Regression` |
| 2024-08-19 | [#2294](https://github.com/semaphoreui/semaphore/issues/2294) · open · critical | Semaphore самопроизвольно и слишком часто запускает задачи; часть задач работает бесконечно | Нарушен lifecycle scheduled tasks: неожиданные запуски и зависшие выполнения | Пока не починено; причина не установлена | В issue нет ответа сопровождающего, PR или воспроизводимых шагов | `SCHEDULES`; дополнительно `TASKS`, `Concurrency`, `needs reproduction` |
| 2024-08-19 | [#2293](https://github.com/semaphoreui/semaphore/issues/2293) · open · critical | В Environment нельзя переименовать secret: UI показывает успех, но старое имя остаётся | Изменение имени секрета не сохраняется, ошибки в browser console нет | Пока не починено | Fix/PR не найден; требуется проверить request payload, API update и сохранение в БД | `SECRETS`; дополнительно `UI`, `API`, `Postgres` |
| 2024-07-01 | [#2152](https://github.com/semaphoreui/semaphore/issues/2152) · open · critical | `SEMAPHORE_DB_PORT` игнорируется в контейнере при нестандартном порте Postgres | Приложение продолжает использовать порт 5432 независимо от env var | Пока не починено | Fix/PR не найден; вероятная область проверки — преобразование env/config и Docker entrypoint, но причина ещё не подтверждена | `CONFIG`; дополнительно `DEPLOYMENT`, `DB`, `Postgres` |
| 2024-06-20 | [#2125](https://github.com/semaphoreui/semaphore/issues/2125) · open · critical | После версии v2.10.7 все schedules перестали выполняться | Cron schedules игнорируются, задачи не создаются | Пока не починено; недостаточно данных | В issue нет логов, ответа сопровождающего или связанного PR | `SCHEDULES`; дополнительно `TASKS`, `Regression`, `needs reproduction` |
| 2024-06-13 | [#2097](https://github.com/semaphoreui/semaphore/issues/2097) · open · critical | После обновления ссылка на Task Log в email стала некликабельной и не содержит настроенный `web_host` | Alert формирует неправильный URL задачи | Пока не починено | Fix/PR не найден; тикет указывает на регрессию после v2.9.112 и возможную связь с #2084 | `OUTPUT`; дополнительно `CONFIG`, `Alerts`, `Regression` |
| 2024-05-07 | [#1999](https://github.com/semaphoreui/semaphore/issues/1999) · open · critical | После обновления с 2.9.37 до 2.9.75 один шаблон с cron `*/2 * * * *` ежедневно зависает в Waiting | Очередной scheduled run не начинает выполнение; помогает остановка waiting job и reboot | Не установлено; сопровождающий не смог воспроизвести | Исправление или PR не указаны; нужны конфигурация template, ограничения parallel tasks, DB и логи scheduler/task pool | `TASKS`; дополнительно `SCHEDULES`, `Concurrency`, `Regression`, `needs reproduction` |
| 2023-09-05 | [#1459](https://github.com/semaphoreui/semaphore/issues/1459) · closed | Project Manager мог повысить себя до Owner, понизить Owner или удалить его из проекта | Нарушена серверная модель проектных ролей и граница повышения привилегий | Тикет закрыт, но доказательство конкретного исправления пока не найдено | Нет связанного PR/commit; перед признанием исправленным нужно проверить историю `api/projects/users.go` и написать прямые API-тесты матрицы ролей | `PROJECTS`; дополнительно `RBAC`, `Security`, `Privilege escalation` |
| 2023-04-19 | [#1216](https://github.com/semaphoreui/semaphore/issues/1216) · closed | После завершения задачи строки output меняли порядок; `PLAY RECAP` оказывался среди предыдущих TASK | Строки с одинаковой секундой сортировались неоднозначно, из-за чего сохранённая история отличалась от live output | Сопровождающий отметил тикет как fixed, но связанный diff не указан | Предложенный в issue способ — сортировать `task__output` по `id`, а не по времени. Нужно отдельно подтвердить реализацию в текущем query path | `OUTPUT`; дополнительно `DB`, `Data integrity` |
| 2023-04-12 | [#1211](https://github.com/semaphoreui/semaphore/issues/1211) · open, fix найден | При одновременном запуске двух cron-задач Semaphore с BoltDB падал с `panic: Connection schedule already exists` | Параллельные `ScheduleRunner` использовали одинаковое имя непостоянного соединения `schedule` | Исправлено в commit [`e2f43bee`](https://github.com/semaphoreui/semaphore/commit/e2f43bee7e4bb13bc0553d371f9ea162e3861c22); пользователь подтвердил отсутствие проблемы начиная с v2.9.75 | Имя соединения изменено с общего `schedule` на уникальное `schedule <scheduleID>`, чтобы параллельные schedules не конфликтовали | `SCHEDULES`; дополнительно `DB`, `BoltDB`, `Concurrency` |

## Первые выводы

### 1. Schedules и lifecycle задач — самый заметный кластер в стартовой выборке

В небольшой проверенной выборке сразу встречаются:

- потеря variables при сохранении schedule (#4075);
- слишком частые/самопроизвольные запуски (#2294);
- полностью игнорируемые cron schedules (#2125);
- задача, зависающая в Waiting (#1999);
- падение BoltDB при одновременном запуске (#1211).

Это уже обосновывает ранний набор тестов на `create schedule -> persist params -> trigger -> create exactly one task -> complete`, включая два schedule на одно время и разные DB modes.

### 2. Ошибки конфигурации часто проявляются далеко от конфигурации

Пустой `web_host` ломает OIDC redirect (#2681), тот же класс настройки участвует в ссылках email alerts (#2097), а DB port из окружения не доходит до подключения (#2152). Нужны table-driven config tests плюс несколько интеграционных smoke-сценариев.

### 3. GitHub status нельзя использовать как единственный источник истины

- #1211 открыт, хотя есть fix-коммит и пользовательское подтверждение.
- #2681 открыт и имеет готовый, но не влитый PR.
- #1459 закрыт, но связь с исправлением не указана.

При дальнейшей обработке для каждого закрытого тикета нужно проверять timeline, связанные PR/commit и актуальный код.

### 4. Старые `critical` issues требуют повторного триажа

Часть из них не имеет логов, воспроизводимых шагов или реакции сопровождающих. Их ценность для тест-плана всё равно высока как сигнал риска, но они не должны автоматически считаться подтверждёнными текущими дефектами.

## Следующий проход

### Локально найдено: survey enum default не валидируется в `v2.19.8`

Black-box API-прогон подтвердил, что template с enum `default_value` вне `values` создаётся с `201` и сохраняет некорректное значение. В upstream это уже исправлено commit [`eb29c3e8`](https://github.com/semaphoreui/semaphore/commit/eb29c3e802df4890dc803709954dc373ae8968b2), входящим в `v2.20.0-alpha1`: добавлена backend-валидация совместимости survey type/default/values. Полный reproducer и критерий переключения регрессионной проверки находятся в `test-environment/survey-default-validation-defect.md`.

### Локально найдено: task завершается error при отсутствии matching runner

На `v2.19.8` matching runner с исчерпанной capacity корректно оставляет task в `waiting`, но временно inactive runner или неизвестный required tag дают terminal `error: no runners available`. Новый runner позже не может подобрать уже завершённую задачу, что противоречит ожидаемому offline recovery в TC-027/TC-028. Persistent-runner reproducer, impact и source-level boundary описаны в `test-environment/runner-unavailable-routing-defect.md`.

### Локально найдено: remote runner теряет secret survey variables

На `v2.19.8` один и тот же survey/task launch успешно выполняется локальным executor, но на persistent runner завершается ошибкой undefined variable: server очищает неперсистентный `task.secret` и не передаёт отдельную in-memory копию remote job. Значение секрета в output не раскрывается. Дефект исправлен upstream PR [#4086](https://github.com/semaphoreui/semaphore/pull/4086), commit [`081425d2`](https://github.com/semaphoreui/semaphore/commit/081425d2bc20d5fe41def47ec6a429e2e43cf715), и входит в `v2.20.0-alpha1`, но отсутствует в `v2.19.8`. Reproducer и regression criterion находятся в `test-environment/remote-runner-survey-secrets-defect.md`.

### Локально найдено: project restore принимает дублирующиеся имена ресурсов

На `v2.19.8` backup с двумя repositories одного имени восстанавливается с `200`, и оба объекта сохраняются. Связи backup задаются именами, поэтому template получает неоднозначную ссылку. Причина находится в общем `verifyDuplicate`: дубликат отклоняется только при `n > 2`, а два совпадения проходят. Canary также подтверждает корректные соседние границы — `401` для non-admin и `400` для отсутствующей repository-ссылки. Полный reproducer, impact и дополнительный gap пустой error response находятся в `test-environment/project-backup-restore-validation-defect.md`.

### Regression status: #2293

API baseline для rename Variable Group secret добавлен и проходит на SQLite и PostgreSQL `v2.19.8`: новое имя сохраняется, старое значение продолжает работать в реальной Ansible task, plaintext отсутствует в API и output. Это подтверждает backend persistence, но исходная жалоба была на UI, поэтому issue нельзя считать полностью закрытым без отдельной browser-проверки payload формы Environment.

1. Разобрать оставшиеся открытые bug-issues по компонентам без глубокого анализа fix.
2. Выбрать по 3–5 наиболее содержательных тикетов из кластеров `TASKS/SCHEDULES`, `AUTH/RBAC`, `SECRETS`, `REPOSITORIES`, `RUNNERS` и `MIGRATIONS`.
3. Для выбранных закрытых тикетов проверить timeline, PR, commit и текущий код.
4. Добавить поля `дата закрытия`, `версия`, `СУБД/способ установки`, `достоверность причины` в машиночитаемый CSV, когда формат стабилизируется.
