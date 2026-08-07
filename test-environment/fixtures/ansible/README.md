# Ansible fixture

Доверенный минимальный playbook для проверки task lifecycle и output.

Он не изменяет систему и выводит только детерминированный маркер `semaphore-bookwright-smoke-ok`.

При запуске Compose сервис `fixture-init` упаковывает эту папку в локальный Git repository. Semaphore получает repository через отдельный read-only volume и использует URL `file:///fixtures/ansible`. Это исключает выполнение кода из внешнего репозитория.
