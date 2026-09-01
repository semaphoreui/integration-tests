# Ansible fixture

Доверенный минимальный playbook для проверки task lifecycle и output.

Он не изменяет систему и выводит только детерминированный маркер `semaphore-bookwright-smoke-ok`.

При запуске Compose сервис `fixture-init` упаковывает `test-environment/fixtures` в локальный Git repository с сохранением путей относительно корня проекта. Semaphore получает repository через отдельный read-only volume и использует URL `file:///repository`. Это исключает выполнение кода из внешнего репозитория.
