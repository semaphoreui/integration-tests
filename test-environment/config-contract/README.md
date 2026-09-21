# Semaphore configuration contract

This small black-box check runs the published Community server image in three isolated SQLite
containers. Each container has no external network or published host port; HTTP requests use
`127.0.0.1` inside the container. The check verifies:

1. `config.json` starts the server on its configured port.
2. `SEMAPHORE_PORT` overrides the file value; the old port no longer responds.
3. An invalid port prevents startup with a useful field diagnostic, while a generated
   `SEMAPHORE_ACCESS_KEY_ENCRYPTION` value is absent from the logs.

Run locally with Docker and Python 3.10+:

```bash
python3 test-environment/config-contract/check.py
```

The default image is `semaphoreui/semaphore:v2.19.12`. Set `CONFIG_CONTRACT_IMAGE` to run the
same contract against another published image. This does not test every configuration option,
config-file format, deployment platform, or secret storage backend. It is intentionally a fast
startup/precedence/validation check rather than another end-to-end API suite.
