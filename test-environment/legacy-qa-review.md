# Ревью тестового наследия Semaphore UI

**Дата:** 2026-08-19
**Источник:** upstream `semaphore/test/` на commit `ae12f3acac626f78673b95cc57acd62ed873b089`

## Решение

Старый код не переносится в Bookwright как готовая реализация. Сценарии сохраняются как источник требований и постепенно переписываются на подходящем уровне: API для бизнес-правил и lifecycle, UI только для критичных пользовательских путей и клиентской валидации.

## Playwright `test/e2e`

В наборе пять тестов: успешный запуск task, stop в состояниях waiting/cloning/running и запрет Variable Group с пустым именем.

Причины не использовать реализацию напрямую:

- `package.json` не содержит команды запуска;
- `baseURL` закреплён на `http://localhost:8080`, тогда как воспроизводимый стенд работает на `3000`;
- тесты создают внешний demo-проект и зависят от его содержимого;
- ожидания привязаны к английскому UI и конкретным строкам Ansible output;
- `afterEach` предполагает открытый dialog, поэтому первоначальная ошибка может быть скрыта падением cleanup;
- три worker запускают stateful UI-сценарии параллельно без доказанной изоляции;
- trace, video и screenshots при падении выключены;
- fixture `role` фактически не назначает выбранную роль: соответствующая строка закомментирована.

Что сохраняем:

| Старый сценарий | Текущее покрытие | Решение |
|---|---|---|
| Task success через UI | Бизнес-цепочка полностью покрыта API | Позже переписать один короткий UI smoke без demo-проекта |
| Stop while waiting | Нет точного state-specific покрытия | Backlog task concurrency |
| Stop while cloning | Есть clone failure и stop running, но не их пересечение | Backlog task lifecycle |
| Stop while running | Покрыто обычным stop и force-stop | Старый код не нужен |
| Variable Group с пустым key | Покрыто API | Оставить будущую UI validation-проверку |

## Ручные test cases

Обозначения: **покрыто** — контракт уже защищён автоматизацией; **частично** — защищена основа, но не весь исходный сценарий; **backlog** — полезный пробел; **внешний** — требует отдельной инфраструктуры или сервиса.

| ID | Область | Статус | Решение |
|---|---|---|---|
| TC-001 | Admin login | Частично | API login есть; оставить один будущий UI login smoke |
| TC-002 | Invalid login / brute force | Частично | Wrong password покрыт; lockout добавить после уточнения контракта |
| TC-003 | TOTP | Покрыто | API и browser enrollment/challenge/recovery проходят с управляемым RFC 6238 secret |
| TC-004 | User lifecycle | Частично | Create/reuse fixture есть; добавить deactivate/reactivate |
| TC-005 | API token | Backlog | P1 auth/security |
| TC-006 | Project create | Покрыто | Не дублировать |
| TC-007 | Max parallel tasks | Backlog | P1 concurrency/queue |
| TC-008 | Backup/restore | Backlog | P2 migration/recovery |
| TC-009 | Delete project dependencies | Частично | Cleanup есть; добавить негативный контракт зависимостей |
| TC-010 | SSH Git repository | Покрыто | Локальный SSH Git fixture, negative auth и rotation автоматизированы |
| TC-011 | HTTPS token repository | Частично | Безопасный failure покрыт; нужен успешный локальный HTTPS remote |
| TC-012 | SSH inventory key | Покрыто | Тот же SSH fixture подтверждает удалённое Ansible execution |
| TC-013 | Login/password key | Покрыто | Использование и отсутствие plaintext проверяются |
| TC-014 | Vault storage | Внешний | Feature profile с Vault dev server |
| TC-015 | Static inventory | Частично | Localhost покрыт; multi-group semantics оставить как низкий риск |
| TC-016 | File inventory | Backlog | P1 inventory/repository integration |
| TC-017 | Terraform inventory | Внешний | Отдельный Terraform feature profile |
| TC-018 | Variable Groups mixed | Покрыто | JSON/ENV/secret var+env, rename, masking и task execution автоматизированы |
| TC-019 | TF_VAR secrets | Внешний | После Terraform profile |
| TC-020 | Ansible template execution | Покрыто | Не дублировать |
| TC-021 | Build/deploy chain | Backlog | P2 workflows |
| TC-022 | Survey variables | Покрыто API | Enum/int/string/env/secret metadata, persistence, execution и backend target validation; UI widgets/required остаются browser-проверкой |
| TC-023 | Task overrides | Покрыто API | Launch values, template/task arguments и Ansible limit/tags/skip-tags/diff/skip-galaxy реально выполняются |
| TC-024 | Stop task | Покрыто | Обычный stop и force-stop детерминированы marker-ом |
| TC-025 | Cron schedule | Частично | CRUD/validation/toggle добавлены; реальное fire и DST вынести в slow profile |
| TC-026 | Run-at schedule | Частично | Payload/validation добавлены; fire/delete-after-run вынести в slow profile |
| TC-027 | Runner registration | Покрыто | Persistent runner проверяет status и heartbeat |
| TC-028 | Runner tags | Backlog | P1 runner routing/capacity |
| TC-029 | GitHub integration | Внешний | Нужен webhook receiver и управляемый GitHub event fixture |
| TC-030 | Task Runner RBAC | Покрыто | Permission mask и запрещённые mutations проверяются |

## MCP-планы

`test/mcp/api` и `test/mcp/e2e` — инструкции для интерактивного agent-run, а не детерминированные regression tests. Они запускают внешний `cursor-agent`, используют demo data и в одном случае выполняют Bash из полного upstream-репозитория. В CI их не переносим. Полезные идеи — update проекта, build/deploy chain и user lifecycle — уже отражены в backlog выше.

## Очерёдность переноса

1. Schedules contract и validation — текущая реализация.
2. Локальный SSH Git/inventory fixture без внешней сети.
3. Variable Groups, survey variables и launch-time overrides — выполнено на API.
4. Queue/max parallel и runner tags — следующий блок.
5. Минимальные UI smoke: login, запуск task, одна клиентская validation.
6. Отдельные feature profiles для Vault, Terraform и webhook integration.
