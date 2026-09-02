#!/bin/bash

set -eu

# Run tests against a running Semaphore UI instance
#
# Usage:
#   ./run-external-tests.sh [--host HOST] [--port PORT] [--username USER] [--password PASSWORD] [gradle args...]
#
# Environment variables (override CLI arguments):
#   API_BASE_URL          - Full API URL, e.g. http://my-semaphore:3000/api/
#   API_USERNAME          - API user (default: admin)
#   API_PASSWORD          - API password (default: test-password)
#   UI_BASE_URL           - Full UI URL, e.g. http://my-semaphore:3000
#   UI_USER               - UI user (default: admin)
#   UI_PASSWORD           - UI password (default: test-password)
#   UI_HEADLESS           - Run Playwright in headless mode (default: true)
#
# Examples:
#   # Testing against a local Semaphore
#   ./run-external-tests.sh
#
#   # Testing against a remote Semaphore
#   ./run-external-tests.sh --host my-semaphore.com --port 3000
#
#   # Using environment variables
#   export API_BASE_URL=http://prod-semaphore:3000/api/
#   export API_USERNAME=qa_user
#   export API_PASSWORD=qa_password
#   ./run-external-tests.sh
#
#   # Passing custom Gradle arguments
#   ./run-external-tests.sh -i includeTags=@smoke

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Parse command-line arguments
host="${API_HOST:-localhost}"
port="${API_PORT:-3000}"
username="${API_USERNAME:-admin}"
password="${API_PASSWORD:-test-password}"
ui_host="${UI_HOST:-localhost}"
ui_port="${UI_PORT:-3000}"
ui_user="${UI_USER:-admin}"
ui_password="${UI_PASSWORD:-test-password}"
ui_headless="${UI_HEADLESS:-true}"

gradle_args=()

while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            host="$2"
            shift 2
            ;;
        --port)
            port="$2"
            shift 2
            ;;
        --username)
            username="$2"
            shift 2
            ;;
        --password)
            password="$2"
            shift 2
            ;;
        --ui-host)
            ui_host="$2"
            shift 2
            ;;
        --ui-port)
            ui_port="$2"
            shift 2
            ;;
        --ui-user)
            ui_user="$2"
            shift 2
            ;;
        --ui-password)
            ui_password="$2"
            shift 2
            ;;
        *)
            gradle_args+=("$1")
            shift
            ;;
    esac
done

# Use environment variable overrides if they are set
api_base_url="${API_BASE_URL:-http://${host}:${port}/api/}"
ui_base_url="${UI_BASE_URL:-http://${ui_host}:${ui_port}}"

# Configure Java if necessary
if ! java -version >/dev/null 2>&1; then
    macos_java_home=/opt/homebrew/opt/openjdk@21
    if [ -x "$macos_java_home/bin/java" ]; then
        export JAVA_HOME=$macos_java_home
    else
        echo "Java 21 is not available; set JAVA_HOME before running the tests" >&2
        exit 1
    fi
fi

echo "Running tests against Semaphore: $api_base_url"
echo "UI: $ui_base_url"

cd "$script_dir/.."
echo $(pwd)

./gradlew externalTest \
    -DSTAND=external \
    -Dapi.base.url="$api_base_url" \
    -Dapi.username="$username" \
    -Dapi.password="$password" \
    -Dui.base.url="$ui_base_url" \
    -Dui.user="$ui_user" \
    -Dui.password="$ui_password" \
    -Dui.headless="$ui_headless" \
    ${gradle_args[@]+"${gradle_args[@]}"}
