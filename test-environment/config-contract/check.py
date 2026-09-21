#!/usr/bin/env python3
"""Exercise Semaphore's file/env configuration contract in isolated containers."""

import base64
import os
import secrets
import subprocess
import time
from pathlib import Path


IMAGE = os.environ.get("CONFIG_CONTRACT_IMAGE", "semaphoreui/semaphore:v2.19.12")
CONFIG = Path(__file__).resolve().with_name("config.json")
FILE_PORT = 3101
OVERRIDE_PORT = 3102
START_TIMEOUT_SECONDS = 30


class ContractFailure(RuntimeError):
    pass


def docker(
    *args: str, env: dict[str, str] | None = None, timeout: int = 120
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["docker", *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
        env=env,
    )


def required_docker(
    *args: str, env: dict[str, str] | None = None, timeout: int = 120
) -> str:
    result = docker(*args, env=env, timeout=timeout)
    if result.returncode != 0:
        raise ContractFailure(
            f"Docker command failed ({result.returncode}): {result.stderr[-1500:]}"
        )
    return result.stdout.strip()


def start_server(*extra_args: str, env: dict[str, str] | None = None) -> str:
    return required_docker(
        "run",
        "--detach",
        "--network",
        "none",
        "--mount",
        f"type=bind,src={CONFIG},dst=/tmp/config-contract.json,readonly",
        "--entrypoint",
        "semaphore",
        *extra_args,
        IMAGE,
        "server",
        "--config",
        "/tmp/config-contract.json",
        env=env,
        timeout=300,
    )


def container_state(container: str) -> tuple[bool, int]:
    state = required_docker(
        "inspect", "--format", "{{.State.Running}} {{.State.ExitCode}}", container
    )
    running, exit_code = state.split()
    return running == "true", int(exit_code)


def container_logs(container: str, secret: str = "") -> str:
    result = docker("logs", container)
    logs = result.stdout + result.stderr
    return logs.replace(secret, "<redacted>") if secret else logs


def assert_http_port(container: str, port: int, *, reachable: bool) -> None:
    result = docker(
        "exec",
        container,
        "curl",
        "--silent",
        "--show-error",
        "--fail",
        "--max-time",
        "2",
        f"http://127.0.0.1:{port}/api/ping",
        timeout=10,
    )
    if reachable and (result.returncode != 0 or result.stdout.strip() != "pong"):
        raise ContractFailure(f"Port {port} did not serve /api/ping: {result.stderr.strip()}")
    if not reachable and result.returncode == 0:
        raise ContractFailure(f"Port {port} unexpectedly served /api/ping")


def await_server(container: str, port: int) -> None:
    deadline = time.monotonic() + START_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        running, exit_code = container_state(container)
        if not running:
            raise ContractFailure(
                f"Server exited with code {exit_code}: {container_logs(container)[-2000:]}"
            )
        result = docker(
            "exec",
            container,
            "curl",
            "--silent",
            "--fail",
            "--max-time",
            "2",
            f"http://127.0.0.1:{port}/api/ping",
            timeout=10,
        )
        if result.returncode == 0 and result.stdout.strip() == "pong":
            return
        time.sleep(0.25)
    raise ContractFailure(
        f"Server did not become ready on {port}: {container_logs(container)[-2000:]}"
    )


def await_rejected_config(container: str, secret: str) -> None:
    deadline = time.monotonic() + START_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        running, exit_code = container_state(container)
        if not running:
            raw_logs = container_logs(container)
            if secret in raw_logs:
                raise ContractFailure("Invalid configuration exposed the access-key encryption secret")
            diagnostic = raw_logs.lower()
            if (
                exit_code == 0
                or "port" not in diagnostic
                or not any(
                    word in diagnostic for word in ("invalid", "not valid", "must match")
                )
            ):
                raise ContractFailure(
                    f"Invalid port did not fail clearly (exit {exit_code}): "
                    f"{container_logs(container, secret)[-2000:]}"
                )
            return
        time.sleep(0.25)
    raise ContractFailure("Server accepted an invalid port or did not terminate")


def check_file_port() -> None:
    container = start_server()
    try:
        await_server(container, FILE_PORT)
        assert_http_port(container, FILE_PORT, reachable=True)
        assert_http_port(container, OVERRIDE_PORT, reachable=False)
    finally:
        docker("rm", "--force", container)
    print("PASS config.json starts the server on its configured port")


def check_environment_override() -> None:
    container = start_server("--env", f"SEMAPHORE_PORT=:{OVERRIDE_PORT}")
    try:
        await_server(container, OVERRIDE_PORT)
        assert_http_port(container, OVERRIDE_PORT, reachable=True)
        assert_http_port(container, FILE_PORT, reachable=False)
    finally:
        docker("rm", "--force", container)
    print("PASS environment variable overrides the file port")


def check_invalid_value_and_secret() -> None:
    secret = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
    child_env = os.environ.copy()
    child_env["SEMAPHORE_ACCESS_KEY_ENCRYPTION"] = secret
    container = start_server(
        "--env",
        "SEMAPHORE_ACCESS_KEY_ENCRYPTION",
        "--env",
        "SEMAPHORE_PORT=invalid-port",
        env=child_env,
    )
    try:
        await_rejected_config(container, secret)
    finally:
        docker("rm", "--force", container)
    print("PASS invalid value is rejected without exposing a configured secret")


def main() -> None:
    print(f"Checking configuration contract with {IMAGE}")
    check_file_port()
    check_environment_override()
    check_invalid_value_and_secret()


if __name__ == "__main__":
    try:
        main()
    except (ContractFailure, subprocess.TimeoutExpired) as error:
        raise SystemExit(f"Configuration contract failed: {error}") from None
