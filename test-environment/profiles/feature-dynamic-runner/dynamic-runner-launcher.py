#!/usr/bin/env python3

import json
import os
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


token_file = os.environ["SEMAPHORE_RUNNER_TOKEN_FILE"]
events = []
events_lock = threading.Lock()
launch_lock = threading.Lock()
current_process = None


def record(event_type, payload, **details):
    event = {
        "type": event_type,
        "task_id": int(payload.get("task_id", 0)),
        "runner_id": int(payload.get("runner_id", 0)),
        "exit_code": details.get("exit_code"),
    }
    with events_lock:
        events.append(event)
    print(json.dumps(event), flush=True)


def register_runner():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if os.path.isfile(token_file) and os.path.getsize(token_file) > 0:
            return
        result = subprocess.run(
            ["/usr/local/bin/semaphore", "runner", "register", "--no-config"],
            check=False,
        )
        if result.returncode == 0 and os.path.isfile(token_file):
            return
        time.sleep(2)
    raise RuntimeError("dynamic runner registration did not produce a token")


def remember_exit(process, payload):
    exit_code = process.wait()
    record("runner_exited", payload, exit_code=exit_code)


def launch_runner(payload):
    global current_process
    with launch_lock:
        if current_process is not None and current_process.poll() is None:
            current_process.wait(timeout=20)
        current_process = subprocess.Popen(
            ["/usr/local/bin/semaphore", "runner", "start", "--no-config"]
        )
        record("runner_started", payload)
        threading.Thread(
            target=remember_exit,
            args=(current_process, payload),
            daemon=True,
        ).start()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Length", "0")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
            return
        if self.path == "/state":
            with events_lock:
                body = json.dumps({"events": list(events)}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.close_connection = True
            return
        self.send_error(404)

    def do_POST(self):
        if self.path != "/webhook":
            self.send_error(404)
            return
        try:
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            action = payload["action"]
            if action == "start":
                record("webhook_start", payload)
                launch_runner(payload)
            elif action == "finish":
                record("webhook_finish", payload)
            else:
                raise ValueError(f"unsupported action: {action}")
            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
        except (KeyError, TypeError, ValueError, OSError, subprocess.SubprocessError) as error:
            print(f"Webhook handling failed: {error}", flush=True)
            self.send_error(500, str(error))

    def log_message(self, message_format, *args):
        print(message_format % args, flush=True)


register_runner()
ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
