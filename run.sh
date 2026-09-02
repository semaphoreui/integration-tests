#!/bin/bash

set -e

SEMAPHORE_CONTAINER_NAME="semaphore-1"

print_help() {
    cat << EOF
Usage: ./run.sh [COMMAND] [PARAMS...]

Commands:
  test:external [PARAMS]            Запуск тестов против запущенного Semaphore UI
                                    Параметры: host=VALUE port=VALUE
                                               api_base_url=URL ui_base_url=URL
                                    (api_base_url/ui_base_url имеют приоритет над host/port)

  sqlite:up                         Запуск SQLite окружения
  sqlite:test                       Запуск тестов SQLite
  sqlite:down                       Остановка SQLite окружения
  sqlite:clean                      Очистка SQLite окружения
  sqlite                            Запуск полного цикла SQLite (up → test → down)

  mysql:up                          Запуск MySQL окружения
  mysql:test                        Запуск тестов MySQL
  mysql:down                        Остановка MySQL окружения
  mysql:clean                       Очистка MySQL окружения
  mysql                             Запуск полного цикла MySQL (up → test → down)

  mariadb:up                        Запуск MariaDB окружения
  mariadb:test                      Запуск тестов MariaDB
  mariadb:down                      Остановка MariaDB окружения
  mariadb:clean                     Очистка MariaDB окружения
  mariadb                           Запуск полного цикла MariaDB (up → test → down)

  prod:up                           Запуск Production Postgres окружения
  prod:test                         Запуск тестов Production
  prod:down                         Остановка Production окружения
  prod:clean                        Очистка Production окружения
  prod                              Запуск полного цикла Production (up → test → down)

  clean:all                         Очистка всех окружений

  external:localhost                Запуск тестов против Semaphore на localhost:3000
  external:docker                   Запуск тестов против Semaphore в Docker на host.docker.internal:3000

  publish:s3 [PARAMS]               Опубликовать результаты тестов в S3

  help                              Показать это сообщение

Examples:
  ./run.sh sqlite
  ./run.sh test:external host=localhost port=3000
  ./run.sh test:external api_base_url=https://demo.example.com/api/ ui_base_url=https://demo.example.com
  ./run.sh publish:s3 bucket=my-bucket

EOF
}

# Extract parameter value by key (supports key=value and --key value)
get_param() {
    local key="$1"
    shift

    local found=0
    for param in "$@"; do
        # Format: key=value
        if [[ "$param" == "${key}="* ]]; then
            echo "${param#${key}=}"
            return 0
        fi
        # Format: --key (next arg is value)
        if [ "$found" -eq 1 ]; then
            echo "$param"
            return 0
        fi
        if [[ "$param" == "--${key}" ]]; then
            found=1
        fi
    done
    return 1
}

check_container() {
    docker ps --filter "name=$SEMAPHORE_CONTAINER_NAME" --filter "status=running" | grep -q "$SEMAPHORE_CONTAINER_NAME"
}

# SQLite tasks
sqlite_up() {
    docker compose run test-runner ./test-environment/profile up core-sqlite-local
}

sqlite_test() {
    sqlite_up
    docker compose run test-runner ./test-environment/profile test core-sqlite-local
}

sqlite_down() {
    docker compose run test-runner ./test-environment/profile down core-sqlite-local
}

sqlite_clean() {
    docker compose run test-runner ./test-environment/profile clean core-sqlite-local --yes
}

sqlite_full() {
    sqlite_up
    sqlite_test
    sqlite_down
}

# MySQL tasks
mysql_up() {
    docker compose run test-runner ./test-environment/profile up core-mysql-local
}

mysql_test() {
    mysql_up
    docker compose run test-runner ./test-environment/profile test core-mysql-local
}

mysql_down() {
    docker compose run test-runner ./test-environment/profile down core-mysql-local
}

mysql_clean() {
    docker compose run test-runner ./test-environment/profile clean core-mysql-local --yes
}

mysql_full() {
    mysql_up
    mysql_test
    mysql_down
}

# MariaDB tasks
mariadb_up() {
    docker compose run test-runner ./test-environment/profile up core-mariadb-local
}

mariadb_test() {
    mariadb_up
    docker compose run test-runner ./test-environment/profile test core-mariadb-local
}

mariadb_down() {
    docker compose run test-runner ./test-environment/profile down core-mariadb-local
}

mariadb_clean() {
    docker compose run test-runner ./test-environment/profile clean core-mariadb-local --yes
}

mariadb_full() {
    mariadb_up
    mariadb_test
    mariadb_down
}

# Production tasks
prod_up() {
    docker compose run test-runner ./test-environment/profile up prod-postgres-runner
}

prod_test() {
    docker compose run test-runner ./test-environment/profile test prod-postgres-runner
}

prod_down() {
    docker compose run test-runner ./test-environment/profile down prod-postgres-runner
}

prod_clean() {
    docker compose run test-runner ./test-environment/profile clean prod-postgres-runner --yes
}

prod_full() {
    prod_up
    prod_test
    prod_down
}

# Clean all
clean_all() {
    sqlite_clean
    mysql_clean
    mariadb_clean
    prod_clean
}

# External tests
external_localhost() {
    docker compose run test-runner ./scripts/run-external-tests.sh --host localhost --port 3000
}

external_docker() {
    docker compose run test-runner ./scripts/run-external-tests.sh --host host.docker.internal --port 3000
}

test_external() {
    local host=$(get_param "host" "$@" 2>/dev/null || echo "host.docker.internal")
    local port=$(get_param "port" "$@" 2>/dev/null || echo "3000")
    local api_base_url=$(get_param "api_base_url" "$@" 2>/dev/null || echo "http://${host}:${port}/api/")
    local ui_base_url=$(get_param "ui_base_url" "$@" 2>/dev/null || echo "http://${host}:${port}")

    echo "Running external tests..."
    echo "  API_BASE_URL: $api_base_url"
    echo "  UI_BASE_URL:  $ui_base_url"

    # Передаём параметры через переменные окружения
    docker compose run \
        -e API_BASE_URL="$api_base_url" \
        -e UI_BASE_URL="$ui_base_url" \
        -e API_USERNAME="$API_USERNAME" \
        -e API_PASSWORD="$API_PASSWORD" \
        -e UI_PASSWORD="$UI_PASSWORD" \
        test-runner ./scripts/run-external-tests.sh
}

# Publish to S3
publish_s3() {
    local cmd_args=""

    for param in "$@"; do
        if [[ "$param" == *"="* ]]; then
            local key="${param%%=*}"
            local value="${param#*=}"
            cmd_args="$cmd_args --$key $value"
        fi
    done

    docker compose run test-runner ./scripts/publish-to-s3.sh $cmd_args
}

# Main command router
main() {
    local command="$1"
    shift || true

    case "$command" in
        test:external)
            test_external "$@"
            ;;
        sqlite:up)
            sqlite_up
            ;;
        sqlite:test)
            sqlite_test
            ;;
        sqlite:down)
            sqlite_down
            ;;
        sqlite:clean)
            sqlite_clean
            ;;
        sqlite)
            sqlite_full
            ;;
        mysql:up)
            mysql_up
            ;;
        mysql:test)
            mysql_test
            ;;
        mysql:down)
            mysql_down
            ;;
        mysql:clean)
            mysql_clean
            ;;
        mysql)
            mysql_full
            ;;
        mariadb:up)
            mariadb_up
            ;;
        mariadb:test)
            mariadb_test
            ;;
        mariadb:down)
            mariadb_down
            ;;
        mariadb:clean)
            mariadb_clean
            ;;
        mariadb)
            mariadb_full
            ;;
        prod:up)
            prod_up
            ;;
        prod:test)
            prod_test
            ;;
        prod:down)
            prod_down
            ;;
        prod:clean)
            prod_clean
            ;;
        prod)
            prod_full
            ;;
        clean:all)
            clean_all
            ;;
        external:localhost)
            external_localhost
            ;;
        external:docker)
            external_docker
            ;;
        publish:s3)
            publish_s3 "$@"
            ;;
        external:test-and-publish)
            # Публикуем результаты в S3 даже если тесты упали,
            # но сохраняем exit-код тестов для CI
            set +e
            test_external "$@"
            local test_exit=$?
            set -e
            publish_s3 "$@"
            exit $test_exit
            ;;
        help|--help|-h)
            print_help
            ;;
        *)
            if [ -z "$command" ]; then
                print_help
            else
                echo "Error: Unknown command '$command'"
                echo ""
                print_help
                exit 1
            fi
            ;;
    esac
}

main "$@"
