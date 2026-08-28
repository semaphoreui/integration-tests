#!/bin/bash

set -eu

# Публикация результатов тестов в S3 хранилище
# Использует curl для загрузки файлов в S3
#
# Использование:
#   ./publish-to-s3.sh \
#     --bucket my-bucket \
#     --region us-east-1 \
#     --access-key AKIAIOSFODNN7EXAMPLE \
#     --secret-key wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
#
# Или через переменные окружения:
#   export AWS_S3_BUCKET=my-bucket
#   export AWS_S3_REGION=us-east-1
#   export AWS_ACCESS_KEY_ID=AKIA...
#   export AWS_SECRET_ACCESS_KEY=...
#   ./publish-to-s3.sh
#
# Аргументы:
#   --bucket BUCKET              S3 бакет (или AWS_S3_BUCKET)
#   --region REGION              AWS регион (или AWS_S3_REGION, default: us-east-1)
#   --access-key KEY             Access key (или AWS_ACCESS_KEY_ID)
#   --secret-key SECRET          Secret key (или AWS_SECRET_ACCESS_KEY)
#   --endpoint URL               Custom S3 endpoint (для MinIO, etc.)
#   --skip-validate              Пропустить проверку доступа
#   --title "Заголовок"          Заголовок отчета в индексе
#
# Примеры:
#   # AWS S3
#   ./publish-to-s3.sh --bucket test-results --region us-east-1
#
#   # MinIO (локальный)
#   ./publish-to-s3.sh \
#     --bucket test-results \
#     --endpoint http://localhost:9000 \
#     --access-key minioadmin \
#     --secret-key minioadmin

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
script_dir=$(cd ..)
repo_dir="$script_dir"

# Значения по умолчанию
s3_bucket="${AWS_S3_BUCKET:-}"
s3_region="${AWS_S3_REGION:-us-east-1}"
aws_access_key="${AWS_ACCESS_KEY_ID:-}"
aws_secret_key="${AWS_SECRET_ACCESS_KEY:-}"
s3_endpoint="${AWS_S3_ENDPOINT:-}"
skip_validate=false
report_title="Test Results"

# Разбор аргументов
while [[ $# -gt 0 ]]; do
    case $1 in
        --bucket)
            s3_bucket="$2"
            shift 2
            ;;
        --region)
            s3_region="$2"
            shift 2
            ;;
        --access-key)
            aws_access_key="$2"
            shift 2
            ;;
        --secret-key)
            aws_secret_key="$2"
            shift 2
            ;;
        --endpoint)
            s3_endpoint="$2"
            shift 2
            ;;
        --skip-validate)
            skip_validate=true
            shift
            ;;
        --title)
            report_title="$2"
            shift 2
            ;;
        *)
            echo "Неизвестный аргумент: $1" >&2
            exit 1
            ;;
    esac
done

# Проверка обязательных параметров
if [ -z "$s3_bucket" ]; then
    echo "❌ Ошибка: Не указан S3 бакет" >&2
    echo "   Используйте: --bucket BUCKET или переменную AWS_S3_BUCKET" >&2
    exit 1
fi

if [ -z "$aws_access_key" ] || [ -z "$aws_secret_key" ]; then
    echo "❌ Ошибка: Не указаны AWS ключи" >&2
    echo "   Используйте: --access-key и --secret-key" >&2
    echo "   Или переменные AWS_ACCESS_KEY_ID и AWS_SECRET_ACCESS_KEY" >&2
    exit 1
fi

# Проверка наличия результатов тестов
if [ ! -d "$repo_dir/build/allure-results" ] || [ -z "$(find "$repo_dir/build/allure-results" -maxdepth 1 -type f -name '*-result.json' -print -quit)" ]; then
    echo "❌ Результаты тестов не найдены в build/allure-results/" >&2
    echo "   Сначала запустите тесты" >&2
    exit 1
fi

# Определение S3 хоста
if [ -n "$s3_endpoint" ]; then
    # Custom endpoint (MinIO, и т.д.)
    s3_host="$s3_endpoint"
    s3_url_base="$s3_endpoint"
else
    # AWS S3
    s3_host="${s3_bucket}.s3.${s3_region}.amazonaws.com"
    s3_url_base="https://${s3_host}"
fi

# Удаление протокола если есть
s3_host=$(echo "$s3_host" | sed 's|^https://||;s|^http://||')

# Определение протокола
if [[ "$s3_endpoint" == http://* ]]; then
    protocol="http"
else
    protocol="https"
fi

echo "📊 Подготовка к загрузке в S3..."
echo "   Бакет: $s3_bucket"
echo "   Регион: $s3_region"
echo "   Хост: $s3_host"

# Проверка доступа к S3
if [ "$skip_validate" = false ]; then
    echo "🔍 Проверка доступа к S3..."

    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: AWS4-HMAC-SHA256 Credential=$aws_access_key/$(date +%Y%m%d)/${s3_region}/s3/aws4_request" \
        "${protocol}://${s3_host}/" 2>/dev/null || echo "000")

    if [ "$response" = "000" ] || [ "$response" = "403" ] || [ "$response" = "404" ]; then
        echo "⚠️  Не удалось проверить доступ к S3"
        echo "   Проверьте ключи и бакет, продолжаю..."
    else
        echo "✓ Доступ к S3 OK"
    fi
fi

# Скачать Allure CLI если нужно
if [ ! -x "$repo_dir/build/allure/commandline/bin/allure" ]; then
    echo "⬇️  Скачивание Allure CLI..."
    JAVA_HOME=/opt/homebrew/opt/openjdk@21 "$repo_dir/gradlew" --no-daemon downloadAllure >/dev/null 2>&1 || {
        echo "❌ Не удалось скачать Allure CLI" >&2
        exit 1
    }
fi

allure_bin="$repo_dir/build/allure/commandline/bin/allure"

# Собрать отчет Allure
echo "📝 Собрание отчета Allure..."
temp_report=$(mktemp -d)
trap "rm -rf '$temp_report'" EXIT

"$allure_bin" generate "$repo_dir/build/allure-results" \
    --clean \
    --single-file \
    --output "$temp_report/report" >/dev/null 2>&1

# Структура загрузки в S3
timestamp=$(date '+%Y/%m/%d/%H-%M-%S')
s3_path="reports/$timestamp"

echo "📤 Загрузка файлов в S3..."
echo "   Путь: s3://$s3_bucket/$s3_path/"

# Функция для загрузки файла в S3 с AWS Signature V4
upload_to_s3() {
    local file_path="$1"
    local s3_key="$2"
    local content_type="${3:-text/html}"

    # Чтение файла
    local file_content=$(cat "$file_path")
    local file_size=${#file_content}

    # AWS Signature Version 4
    local date_value=$(date -u '+%a, %d %b %Y %H:%M:%S GMT')
    local amz_date=$(date -u '+%Y%m%dT%H%M%SZ')
    local datestamp=$(date -u '+%Y%m%d')

    # Hashing
    local payload_hash=$(echo -n "$file_content" | openssl dgst -sha256 -hex | awk '{print $2}')

    # Canonical request
    local canonical_request="PUT
/$s3_key
Content-Type:$content_type
Host:$s3_host
X-Amz-Content-Sha256:$payload_hash
X-Amz-Date:$amz_date

Content-Type;Host;X-Amz-Content-Sha256;X-Amz-Date
$payload_hash"

    local canonical_hash=$(echo -n "$canonical_request" | openssl dgst -sha256 -hex | awk '{print $2}')

    # String to sign
    local string_to_sign="AWS4-HMAC-SHA256
$amz_date
$datestamp/${s3_region}/s3/aws4_request
$canonical_hash"

    # Calculate signature
    local kSecret="AWS4${aws_secret_key}"
    local kDate=$(echo -n "$datestamp" | openssl dgst -sha256 -mac HMAC -macopt key:"$kSecret" -hex | awk '{print $2}')
    local kRegion=$(echo -n "${s3_region}" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kDate" -hex | awk '{print $2}')
    local kService=$(echo -n "s3" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kRegion" -hex | awk '{print $2}')
    local kSigning=$(echo -n "aws4_request" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kService" -hex | awk '{print $2}')
    local signature=$(echo -n "$string_to_sign" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kSigning" -hex | awk '{print $2}')

    # Authorization header
    local auth_header="AWS4-HMAC-SHA256 Credential=${aws_access_key}/${datestamp}/${s3_region}/s3/aws4_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=$signature"

    # Upload file
    local http_code=$(curl -s -w "%{http_code}" -o /dev/null \
        -X PUT \
        -H "Content-Type: $content_type" \
        -H "Authorization: $auth_header" \
        -H "X-Amz-Content-Sha256: $payload_hash" \
        -H "X-Amz-Date: $amz_date" \
        -H "X-Amz-Acl: public-read" \
        --data-binary @"$file_path" \
        "${protocol}://${s3_host}/${s3_key}")

    if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
        echo "  ✓ Загружен: $s3_key"
        return 0
    else
        echo "  ✗ Ошибка $http_code при загрузке: $s3_key" >&2
        return 1
    fi
}

# Загрузить HTML отчет
echo ""
upload_to_s3 "$temp_report/report/index.html" "$s3_path/index.html" "text/html; charset=utf-8"

# Создать index страницу со ссылками
echo "📋 Создание индекса..."

cat > "$temp_report/index-page.html" << 'INDEX_PAGE'
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Результаты тестов Semaphore</title>
    <style>
        :root {
            color-scheme: light dark;
            --bg: #ffffff;
            --fg: #1a1a1a;
            --accent: #0066cc;
            --accent-light: #e6f2ff;
            --border: #e0e0e0;
        }
        @media (prefers-color-scheme: dark) {
            :root {
                --bg: #1a1a1a;
                --fg: #e0e0e0;
                --accent: #66b3ff;
                --accent-light: #1a3a52;
                --border: #404040;
            }
        }
        * { box-sizing: border-box; }
        body {
            background: var(--bg);
            color: var(--fg);
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            margin: 0;
            padding: 20px;
            line-height: 1.6;
        }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 {
            color: var(--accent);
            border-bottom: 3px solid var(--accent);
            padding-bottom: 10px;
        }
        .report-list {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }
        .report-card {
            background: var(--accent-light);
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 20px;
            text-decoration: none;
            color: inherit;
            transition: all 0.3s;
        }
        .report-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        .report-date {
            font-size: 0.9em;
            color: var(--accent);
            font-weight: 600;
        }
        .no-reports {
            color: #666;
            text-align: center;
            padding: 40px 20px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Результаты тестов Semaphore</h1>
        <p>Результаты автоматизированных тестов, загруженные в S3</p>
        <div id="reports" class="report-list"></div>
        <div id="empty" class="no-reports">
            <p>Пока нет опубликованных результатов</p>
        </div>
    </div>
</body>
</html>
INDEX_PAGE

upload_to_s3 "$temp_report/index-page.html" "index.html" "text/html; charset=utf-8"

echo ""
echo "✅ Результаты опубликованы в S3!"
echo ""

# Вывод информации об доступе
if [ -n "$s3_endpoint" ]; then
    # Custom endpoint
    echo "📊 Адрес отчета:"
    echo "   ${protocol}://${s3_host}/${s3_path}/index.html"
    echo ""
    echo "📋 Главная страница:"
    echo "   ${protocol}://${s3_host}/index.html"
else
    # AWS S3
    echo "📊 Адрес отчета (S3):"
    echo "   s3://${s3_bucket}/${s3_path}/index.html"
    echo ""
    echo "📊 Адрес отчета (HTTP, если public):"
    echo "   https://${s3_bucket}.s3.${s3_region}.amazonaws.com/${s3_path}/index.html"
    echo ""
    echo "📋 Главная страница:"
    echo "   https://${s3_bucket}.s3.${s3_region}.amazonaws.com/index.html"
fi

echo ""
echo "💡 Совет: Для публичного доступа установите публичное ACL на объекты S3"
