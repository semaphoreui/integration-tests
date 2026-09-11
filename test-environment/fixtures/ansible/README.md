# Ansible fixture

A trusted minimal playbook for verifying the task lifecycle and output.

It does not modify the system and prints only deterministic, safe markers. `external-managed-a.yml`
and `external-managed-b.yml` are the public-repository fixtures used by the explicitly enabled
managed external check. `file-inventory.yml` together with `inventories/localhost.ini` proves
execution via an inventory file from the Git repository; `project-deletion.yml` briefly holds a task
to verify the project deletion boundary; `variables.yml` verifies regular and secret Variable Group
values via `no_log`; `survey-overrides.yml` verifies survey values, the env target, template/task
arguments, and Ansible launch params; `integration-webhook.yml` proves that values from the webhook
body and headers are passed into the task's Ansible variables. `build-version.yml` and
`deploy-version.yml` compare the target/incoming artifact version from the task metadata with the
executor environment. `terraform-workspace/main.tf` is a provider-free module for plan-only
verification of Terraform/OpenTofu workspace inventory and passing a Variable Group secret via
`TF_VAR_*`. Secrets are compared by SHA-256 and are not written to the module or task output.

When Compose starts, the `fixture-init` service packages this folder into a local Git repository. Semaphore receives the repository through a separate read-only volume and uses the URL `file:///fixtures/ansible`. This rules out executing code from an external repository.

After changing the fixture, run `profile down` and `profile up`: the exited `fixture-init` container must be recreated so that the new version is committed to the persisted Git volume.
