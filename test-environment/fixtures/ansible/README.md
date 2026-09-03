# Ansible fixture

Доверенный минимальный playbook для проверки task lifecycle и output.

Он не изменяет систему и выводит только детерминированные безопасные маркеры. `file-inventory.yml` вместе с `inventories/localhost.ini` доказывает выполнение через inventory-файл из Git repository; `project-deletion.yml` коротко удерживает task для проверки project deletion boundary; `variables.yml` проверяет обычные и секретные значения Variable Group через `no_log`; `survey-overrides.yml` проверяет survey values, env-target, template/task arguments и Ansible launch params; `integration-webhook.yml` доказывает передачу значений из webhook body и headers в Ansible variables задачи. `build-version.yml` и `deploy-version.yml` сверяют target/incoming artifact version из task metadata с executor environment. `terraform-workspace/main.tf` — provider-free module для plan-only проверки Terraform/OpenTofu workspace inventory и передачи Variable Group secret через `TF_VAR_*`. Секреты сравниваются по SHA-256 и не записываются в module или task output.

При запуске Compose сервис `fixture-init` упаковывает эту папку в локальный Git repository. Semaphore получает repository через отдельный read-only volume и использует URL `file:///fixtures/ansible`. Это исключает выполнение кода из внешнего репозитория.

После изменения fixture нужно выполнить `profile down` и `profile up`: завершившийся контейнер `fixture-init` должен пересоздаться, чтобы закоммитить новую версию в сохранённый Git volume.
