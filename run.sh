#!/bin/bash

set -e

SEMAPHORE_CONTAINER_NAME="semaphore-1"

print_help() {
    cat << EOF
Usage: ./run.sh [COMMAND] [PARAMS...]

Commands:
  test:external [PARAMS]            Run tests against a running Semaphore UI
                                    Parameters: host=VALUE port=VALUE
                                               api_base_url=URL ui_base_url=URL
                                    (api_base_url/ui_base_url take priority over host/port)

  sqlite:up                         Start the SQLite environment
  sqlite:test                       Run SQLite tests
  sqlite:down                       Stop the SQLite environment
  sqlite:clean                      Clean the SQLite environment
  sqlite                            Run the full SQLite cycle (up → test → down)

  mysql:up                          Start the MySQL environment
  mysql:test                        Run MySQL tests
  mysql:down                        Stop the MySQL environment
  mysql:clean                       Clean the MySQL environment
  mysql                             Run the full MySQL cycle (up → test → down)

  mariadb:up                        Start the MariaDB environment
  mariadb:test                      Run MariaDB tests
  mariadb:down                      Stop the MariaDB environment
  mariadb:clean                     Clean the MariaDB environment
  mariadb                           Run the full MariaDB cycle (up → test → down)

  prod:up                           Start the Production Postgres environment
  prod:test                         Run Production tests
  prod:down                         Stop the Production environment
  prod:clean                        Clean the Production environment
  prod                              Run the full Production cycle (up → test → down)

  clean:all                         Clean all environments

  external:localhost                Run tests against Semaphore on localhost:3000
  external:docker                   Run tests against Semaphore in Docker on host.docker.internal:3000

  publish:s3 [PARAMS]               Publish test results to S3

  help                              Show this message

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
    local port=$(bget_param "port" "$@" 2>/dev/null || echo "3000")
    local api_base_url=$(get_param "api_base_url" "$@" 2>/dev/null || echo "http://${host}:${port}/api/")
    local ui_base_url=$(get_param "ui_base_url" "$@" 2>/dev/null || echo "http://${host}:${port}")

    echo "Running external tests..."
    echo "  API_BASE_URL: $api_base_url"
    echo "  UI_BASE_URL:  $ui_base_url"

    # Pass parameters via environment variables
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

    docker compose run \
        -e AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID" \
        -e AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY" \
        -e AWS_S3_BUCKET="$AWS_S3_BUCKET" \
        -e AWS_S3_REGION="$AWS_S3_REGION" \
        -e AWS_S3_ENDPOINT="$AWS_S3_ENDPOINT" \
        test-runner ./scripts/publish-to-s3.sh $cmd_args
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
            # set +e
            # echo "Running external tests..."
            # test_external "$@"
            # local test_exit=$?
            set -e
            echo "Publishing test results to S3..."
            publish_s3
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
