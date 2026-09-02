# Ansible fixture

A trusted minimal playbook for verifying the task lifecycle and output.

It does not modify the system and outputs only the deterministic marker `semaphore-bookwright-smoke-ok`.

On Compose startup, the `fixture-init` service packs this folder into a local Git repository. Semaphore receives the repository via a separate read-only volume and uses the URL `file:///fixtures/ansible`. This rules out executing code from an external repository.
