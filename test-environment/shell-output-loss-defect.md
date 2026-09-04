# Дефект: короткая Bash-задача теряет `stdout` или `stderr`

## Статус

- severity: High;
- затронутая версия: Semaphore UI `v2.19.12` (`012ed06d`);
- конфигурация: Community, Docker Compose, SQLite, local task execution;
- воспроизведено: 2026-09-04 в Linux CI;
- GitHub Actions run: [33866188839](https://github.com/semaphoreui/integration-tests/actions/runs/33866188839);
- исправлено в `develop`, но исправление ещё не входит в текущий stable release `v2.19.12`.

## Описание

После успешного выполнения короткой Bash-команды Semaphore может сохранить только один из двух
потоков процесса. Статус task уже равен `success`, но API output не содержит либо `stdout`, либо
`stderr`. Потерянный поток не появляется при последующем чтении.

Это делает успешный статус недостаточным доказательством завершённого сбора логов. Пользователь
может потерять диагностические сообщения, значения, которые скрипт намеренно пишет в конкретный
поток, и часть аудита выполнения.

## Шаги воспроизведения

1. Поднять ручной профиль:

   ```bash
   test-environment/profile up feature-shell-output
   ```

2. Запустить строгий reproducer:

   ```bash
   test-environment/profile test feature-shell-output \
     --tests io.bookwright.tests.semaphore.ShellOutputTest
   ```

3. Тест создаёт локальный Git repository, inventory и template, выполняющий:

   ```bash
   printf 'semaphore-shell-stdout-marker'
   printf 'semaphore-shell-stderr-marker' >&2
   ```

4. Дождаться `success` и прочитать task output через API.

Второй сценарий повторяет проверку с background child (`sleep 60 &`), чтобы дополнительно
контролировать завершение команды, от которой дочерний процесс унаследовал output pipes.

## Ожидаемый результат

Task быстро переходит в `success`; сохранённый output содержит оба маркера:

```text
semaphore-shell-stdout-marker
semaphore-shell-stderr-marker
```

## Фактический результат

В одном CI-сценарии остался только `stdout`:

```text
... installing static inventory
semaphore-shell-stdout-marker
```

В другом остался только `stderr`:

```text
... installing static inventory
semaphore-shell-stderr-marker
```

Оба task при этом завершились со статусом `success`. Воспроизведение относится к API/process
lifecycle, поэтому screenshot не добавляет диагностической ценности; доказательства находятся в
JUnit XML, Allure results и Compose logs указанного CI run.

## Причина и upstream-исправления

В `v2.19.12` `TaskRunner.LogCmd` увеличивает `WaitGroup` внутри reader goroutine. `WaitLog` может
успеть увидеть нулевой счётчик и продолжить финализацию до чтения обоих потоков. Кроме того,
`ShellApp.Run` вызывает `cmd.Wait()` до гарантированного drain `StdoutPipe`/`StderrPipe`, хотя
`Wait` освобождает связанные с `Cmd` ресурсы.

Upstream исправил обе части после выпуска `v2.19.12`:

- [`5c2d6e34` — preserve output from short shell commands](https://github.com/semaphoreui/semaphore/commit/5c2d6e34bed587b3cbea029c0799c5577e781800): регистрирует readers до запуска goroutine и дожидается drain pipes перед `cmd.Wait()`;
- [`4976e916` — prevent hangs from inherited output pipes](https://github.com/semaphoreui/semaphore/commit/4976e91699886157184d04fa2069e912e40156b9): вводит command-scoped finalizer, `io.Pipe` и ограниченный `WaitDelay` для descendants, удерживающих output pipes.

## Автоматизированный контроль

`ShellOutputTest` сохраняет строгие проверки обоих маркеров и времени завершения background
сценария. До появления исправления в stable он запускается только профилем
`feature-shell-output`, не маскируется retry и не входит в зелёный PR/nightly gate. В ручном
`Configuration matrix` его можно включить input-параметром
`include_shell_output_investigation=true` и получить стандартные CI artifacts.
