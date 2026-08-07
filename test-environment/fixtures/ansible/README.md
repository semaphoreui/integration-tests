# Ansible fixture

Доверенный минимальный playbook для проверки task lifecycle и output.

Он не изменяет систему и выводит только детерминированный маркер `semaphore-bookwright-smoke-ok`.

Перед использованием fixture будет упакован в локальный Git repository и смонтирован в контейнер Semaphore read-only. Это исключит выполнение кода из внешнего репозитория.
