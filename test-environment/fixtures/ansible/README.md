# Ansible fixture

A trusted minimal playbook for verifying the task lifecycle and output.

It does not modify the system and prints only deterministic, safe markers. `file-inventory.yml` together with `inventories/localhost.ini` proves execution via an inventory file from the Git repository; `project-deletion.yml` briefly holds a task to verify the project deletion boundary; `variables.yml` verifies regular and secret Variable Group values via `no_log`; `survey-overrides.yml` verifies survey values, the env target, template/task arguments, and Ansible launch params; `integration-webhook.yml` proves that values from the webhook body and headers are passed into the task's Ansible variables. `build-version.yml` and `deploy-version.yml` compare the target/incoming artifact version from the task metadata with the executor environment. `terraform-workspace/main.tf` is a provider-free module for plan-only verification of Terraform/OpenTofu workspace inventory and passing a Variable Group secret via `TF_VAR_*`. Secrets are compared by SHA-256 and are not written to the module or task output.

When Compose starts, the `fixture-init` service packages the contents of `test-environment/fixtures` into the root of a local Git repository (fixture paths are therefore relative to that folder, e.g. `ansible/smoke.yml`) and `fixture-git-init` publishes a bare clone of it through the `fixture-git` nginx service. Semaphore and the runners clone `http://fixture-git/fixtures.git`, reachable only inside the Compose network. This rules out executing code from an external repository.

After changing the fixture, run `profile down` and `profile up`: the exited `fixture-init` and `fixture-git-init` containers must be recreated so that the new version is committed and served.
