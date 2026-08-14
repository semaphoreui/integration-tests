# Проверка обновления Semaphore v2.19.6 → v2.19.7

**Дата проверки:** 2026-08-14

**Статус:** воспроизводимый блокирующий дефект продукта
**СУБД:** SQLite и PostgreSQL 14.3

## Что проверяется

На release image `semaphoreui/semaphore:v2.19.6` создаётся связанный fixture:

```text
project → encrypted access key → repository → inventory → template
→ successful task/output → inactive schedule
```

После успешной seed-фазы server container заменяется на `semaphoreui/semaphore:v2.19.7`. Database и Git fixture volumes не меняются. Verify-фаза входит старой admin-учётной записью, находит сохранённый проект и должна проверить все ресурсы, старый task output и повторное выполнение template.

## Фактический результат

Для SQLite и PostgreSQL результат одинаков:

1. `v2.19.6` стартует на чистой БД, создаёт fixture и успешно выполняет Ansible task.
2. `v2.19.7` стартует на той же БД и успешно выполняет login.
3. Сохранённый проект, repository, inventory, template, schedule и успешный task доступны.
4. `GET /api/project/1/keys` возвращает `400` с пустым response body.
5. Server log содержит: `gorp: no fields [task_id expire_at] in type AccessKey`.

Из-за отказа access key API нельзя подтвердить доступность сохранённых credentials и безопасно продолжить выполнение шаблонов, которые от них зависят. Upgrade считается неуспешным.

## Причина

Tag `v2.19.6` (`ff0cf4cbaa5760ea57fb02973b9f909e619b1856`) содержит и применяет миграции `v2.20.0` и `v2.20.1`. Миграция `v2.20.1` добавляет:

- `access_key.task_id`;
- `access_key.expire_at`;
- индекс `access_key__task_id`.

Tag `v2.19.7` (`e9dc41a1de8a747569334f7a2b76c320b945d4f0`) удаляет эти migration entries и файлы, а также поля `TaskID` и `ExpireAt` из Go-модели `AccessKey`. Уже применённая схема не откатывается. Gorp получает дополнительные колонки из существующей таблицы и не может сопоставить их с моделью текущего release.

Это не расхождение тестовой DTO: ошибка возникает внутри server при чтении БД и подтверждается на двух dialect.

## Воспроизведение

```bash
test-environment/profile upgrade-test upgrade-sqlite-local
test-environment/profile down upgrade-sqlite-local
test-environment/profile upgrade-test upgrade-postgres-local
```

При падении команда сохраняет текущие containers и volumes и выводит последние server logs. Повторный запуск начинает с чистых volumes только выбранного upgrade-профиля.

## Критерии исправления

- `v2.19.7` или следующий исправленный release корректно открывает БД, созданную `v2.19.6`;
- список и отдельное чтение старых access keys возвращают успешный ответ без plaintext secrets;
- связи repository/inventory с сохранённым key не меняются;
- старый task output доступен;
- сохранённый template повторно выполняется успешно;
- после upgrade проходит обычная core API suite;
- сценарий зелёный для SQLite и PostgreSQL.

MySQL и MariaDB используют ту же общую миграцию, но отдельно не прогонялись: двух подтверждённых dialect достаточно для первичной локализации. После исправления их следует добавить в release compatibility job, если support policy требует полный upgrade gate для каждой СУБД.
