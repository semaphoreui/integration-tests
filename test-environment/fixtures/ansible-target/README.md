# Ansible target container

An SSH server with Python, so Ansible can run ordinary modules (`setup`, `copy`, `command`) on it.
Used by `feature-host-config`: only the public half of the generated
`build/test-fixtures/ssh/id_ed25519` pair is mounted, and the private key reaches Ansible only
through a Semaphore Host mapping.
