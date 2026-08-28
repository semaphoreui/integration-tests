#!/bin/bash

set -eu

# Запуск тестов против запущенного экземпляра Semaphore UI
#
# Использование:
#   ./run-external-tests.sh [--host ХОСТ] [--port ПОРТ] [--username ПОЛЬЗОВАТЕЛЬ] [--password ПАРОЛЬ] [аргументы gradle...]
#
# Переменные окружения (переопределяют аргументы CLI):
#   API_BASE_URL          - Полный URL API, например http://my-semaphore:3000/api/
#   API_USERNAME          - Пользователь API (по умолчанию: admin)
#   API_PASSWORD          - Пароль API (по умолчанию: test-password)
#   UI_BASE_URL           - Полный URL UI, например http://my-semaphore:3000
#   UI_USER               - Пользователь UI (по умолчанию: admin)
#   UI_PASSWORD           - Пароль UI (по умолчанию: test-password)
#   UI_HEADLESS           - Запуск Playwright в режиме headless (по умолчанию: true)
#
# Примеры:
#   # Тестирование против локального Semaphore
#   ./run-external-tests.sh
#
#   # Тестирование против удаленного Semaphore
#   ./run-external-tests.sh --host my-semaphore.com --port 3000
#
#   # Использование переменных окружения
#   export API_BASE_URL=http://prod-semaphore:3000/api/
#   export API_USERNAME=qa_user
#   export API_PASSWORD=qa_password
#   ./run-external-tests.sh
#
#   # Передача пользовательских аргументов Gradle
#   ./run-external-tests.sh -i includeTags=@smoke

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Разбор аргументов командной строки
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

# Использование переопределений переменных окружения, если они установлены
api_base_url="${API_BASE_URL:-http://${host}:${port}/api/}"
ui_base_url="${UI_BASE_URL:-http://${ui_host}:${ui_port}}"

# Конфигурация Java, если необходимо
if ! java -version >/dev/null 2>&1; then
    macos_java_home=/opt/homebrew/opt/openjdk@21
    if [ -x "$macos_java_home/bin/java" ]; then
        export JAVA_HOME=$macos_java_home
    else
        echo "Java 21 недоступна; установите JAVA_HOME перед запуском тестов" >&2
        exit 1
    fi
fi

echo "Запуск тестов против Semaphore: $api_base_url"
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
