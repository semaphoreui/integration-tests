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
| Task success через UI | Покрыто | Короткий UI smoke запускает API-подготовленный executable template без demo-проекта и подтверждает success через API |
| Stop while waiting | Покрыто через queue/capacity | Waiting admission и dequeue проверены; отдельный UI stop не нужен |
| Stop while cloning | Есть clone failure и stop running, но не их пересечение | Backlog task lifecycle |
| Stop while running | Покрыто обычным stop и force-stop | Старый код не нужен |
| Variable Group с пустым key | Покрыто API | Оставить будущую UI validation-проверку |

## Ручные test cases

Обозначения: **покрыто** — контракт уже защищён автоматизацией; **частично** — защищена основа, но не весь исходный сценарий; **backlog** — полезный пробел; **внешний** — требует отдельной инфраструктуры или сервиса.

| ID | Область | Статус | Решение |
|---|---|---|---|
| TC-001 | Admin login | Покрыто | API login и независимый browser password-login smoke проходят |
| TC-002 | Invalid login / brute force | Покрыто с security gap | Existing/unknown/empty credentials не создают session и не раскрывают account; пять повторов остаются без throttle и warning |
| TC-003 | TOTP | Покрыто | API и browser enrollment/challenge/recovery проходят с управляемым RFC 6238 secret |
| TC-004 | User lifecycle | Покрыто в границах API | Create/update/delete/absence/recreate автоматизированы; deactivate/reactivate отсутствует в текущих router и модели пользователя |
| TC-005 | API token | Покрыто | Create/list, expiry validation, Bearer access, revoke и защита token material автоматизированы |
| TC-006 | Project create | Покрыто | Не дублировать |
| TC-007 | Max parallel tasks | Покрыто | Лимиты 1→2, waiting admission, slot release и одновременный running автоматизированы |
| TC-008 | Backup/restore | Покрыто с дефектом | Round trip, relinking, execution и negative paths автоматизированы; duplicate-name validation содержит отдельный canary |
| TC-009 | Delete project dependencies | Покрыто с дефектом | После `stopped` каскадное удаление проходит; при `running` API ошибочно возвращает `204`, executor продолжает работу и вызывает FK errors |
| TC-010 | SSH Git repository | Покрыто | Локальный SSH Git fixture, negative auth и rotation автоматизированы |
| TC-011 | HTTPS token repository | Покрыто | Локальный private HTTPS remote проверяет trusted TLS, Basic Auth, execution, negative auth и masking |
| TC-012 | SSH inventory key | Покрыто | Тот же SSH fixture подтверждает удалённое Ansible execution |
| TC-013 | Login/password key | Покрыто | Использование и отсутствие plaintext проверяются |
| TC-014 | Vault storage | Внешний | Feature profile с Vault dev server |
| TC-015 | Static inventory | Покрыто | INI `static` и YAML `static-yaml` сохраняются; template `limit` выбирает одну группу, а host второй группы не выполняется |
| TC-016 | File inventory | Покрыто с дефектом | Repository-backed file реально выполняется; create пропускает traversal, а update отклоняет его пустым `400` |
| TC-017 | Terraform inventory | Покрыто | Plan-only Terraform/OpenTofu используют выбранные workspace inventories на локальном module без provider downloads |
| TC-018 | Variable Groups mixed | Покрыто | JSON/ENV/secret var+env, rename, masking и task execution автоматизированы |
| TC-019 | TF_VAR secrets | Покрыто | Secret типа `env` реально становится Terraform/OpenTofu input variable; SHA-256 marker подтверждает injection без plaintext в API/output/Allure |
| TC-020 | Ansible template execution | Покрыто | Не дублировать |
| TC-021 | Build/deploy chain | Покрыто с уточнением | Ручной выбор successful build, `build_task_id`, nested history version и target/incoming executor env автоматизированы; собственное `version` существует только у build task |
| TC-022 | Survey variables | Покрыто API с дефектом | Enum/int/string/env/secret metadata, persistence, local execution и backend target validation; `v2.19.8` теряет secret при remote dispatch; UI widgets/required остаются browser-проверкой |
| TC-023 | Task overrides | Покрыто API | Launch values, template/task arguments и Ansible limit/tags/skip-tags/diff/skip-galaxy реально выполняются |
| TC-024 | Stop task | Покрыто | Обычный stop и force-stop детерминированы marker-ом |
| TC-025 | Cron schedule | Частично | CRUD/validation/toggle добавлены; реальное fire и DST вынести в slow profile |
| TC-026 | Run-at schedule | Частично | Payload/validation добавлены; fire/delete-after-run вынести в slow profile |
| TC-027 | Runner registration | Частично | Registration/status/heartbeat покрыты; offline recovery воспроизводит `error` вместо ожидаемого waiting |
| TC-028 | Runner tags | Покрыто с дефектом | Exact tag и used_runner_id проходят, busy runner requeue работает; unavailable/unmatched tag завершается error |
| TC-029 | GitHub integration | Внешний | Нужен webhook receiver и управляемый GitHub event fixture |
| TC-030 | Task Runner RBAC | Покрыто | Permission mask и запрещённые mutations проверяются |

## MCP-планы

`test/mcp/api` и `test/mcp/e2e` — инструкции для интерактивного agent-run, а не детерминированные regression tests. Они запускают внешний `cursor-agent`, используют demo data и в одном случае выполняют Bash из полного upstream-репозитория. В CI их не переносим. Полезные идеи — update проекта, build/deploy chain и user lifecycle — уже отражены в backlog выше.

## Очерёдность переноса

1. Schedules contract и validation — текущая реализация.
2. Локальный SSH Git/inventory fixture без внешней сети.
3. Variable Groups, survey variables и launch-time overrides — выполнено на API.
4. Queue/max parallel и runner tags — выполнено; unavailable runner recovery и потеря survey secret при remote dispatch зафиксированы отдельными reproducer/canary.
5. Минимальные UI smoke — выполнено: password login, запуск task и client-side project-name validation без POST.
6. Отдельные feature profiles для Vault, Terraform и webhook integration.
