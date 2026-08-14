#!/usr/bin/env python3

import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request


api_url = os.environ["SEMAPHORE_API_URL"].rstrip("/")
runner_name = os.environ["SEMAPHORE_TEST_RUNNER_NAME"]
credentials = {
    "auth": os.environ["SEMAPHORE_ADMIN"],
    "password": os.environ["SEMAPHORE_ADMIN_PASSWORD"],
}
opener = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
)


def request(path, method="GET", body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {} if data is None else {"Content-Type": "application/json"}
    call = urllib.request.Request(
        f"{api_url}/{path.lstrip('/')}", data=data, headers=headers, method=method
    )
    with opener.open(call, timeout=5) as response:
        payload = response.read()
        return None if not payload else json.loads(payload)


deadline = time.monotonic() + 90
last_error = "server has not accepted the configuration request"

while time.monotonic() < deadline:
    try:
        request("auth/login", "POST", credentials)
        runner = next(
            item for item in request("runners") if item.get("name") == runner_name
        )
        request(
            f"runners/{runner['id']}",
            "PUT",
            {
                "name": runner["name"],
                "active": True,
                "is_default": True,
                "webhook": runner.get("webhook", ""),
                "max_parallel_tasks": runner.get("max_parallel_tasks", 0),
                "tags": runner.get("tags") or [],
            },
        )
        print(f"Runner {runner['id']} is configured as the default remote runner")
        raise SystemExit(0)
    except (OSError, urllib.error.HTTPError, StopIteration, TypeError, ValueError) as error:
        last_error = str(error)
        time.sleep(2)

raise SystemExit(f"Could not configure runner '{runner_name}': {last_error}")
