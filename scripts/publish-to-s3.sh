#!/bin/bash

set -eu

# Publish test results to S3 storage
# Uses curl to upload files to S3
#
# Usage:
#   ./publish-to-s3.sh \
#     --bucket my-bucket \
#     --region us-east-1 \
#     --access-key AKIAIOSFODNN7EXAMPLE \
#     --secret-key wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
#
# Or via environment variables:
#   export AWS_S3_BUCKET=my-bucket
#   export AWS_S3_REGION=us-east-1
#   export AWS_ACCESS_KEY_ID=AKIA...
#   export AWS_SECRET_ACCESS_KEY=...
#   ./publish-to-s3.sh
#
# Arguments:
#   --bucket BUCKET              S3 bucket (or AWS_S3_BUCKET)
#   --region REGION              AWS region (or AWS_S3_REGION, default: us-east-1)
#   --access-key KEY             Access key (or AWS_ACCESS_KEY_ID)
#   --secret-key SECRET          Secret key (or AWS_SECRET_ACCESS_KEY)
#   --endpoint URL               Custom S3 endpoint (or AWS_S3_ENDPOINT)
#   --skip-validate              Skip the access check
#   --title "Title"              Report title in the index
#
# Examples:
#   # AWS S3
#   ./publish-to-s3.sh --bucket test-results --region us-east-1
#
#   # MinIO (local)
#   ./publish-to-s3.sh \
#     --bucket test-results \
#     --endpoint http://localhost:9000 \
#     --access-key minioadmin \
#     --secret-key minioadmin

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir="$script_dir/.."
echo "📂 Repository: $repo_dir"

# Default values
s3_bucket="${AWS_S3_BUCKET:-}"
s3_region="${AWS_S3_REGION:-us-east-1}"
aws_access_key="${AWS_ACCESS_KEY_ID:-}"
aws_secret_key="${AWS_SECRET_ACCESS_KEY:-}"
s3_endpoint="${AWS_S3_ENDPOINT:-}"
report_public_url="${REPORT_PUBLIC_URL:-}"
skip_validate=false
report_title="Test Results"

# Parse arguments
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
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# Check required parameters
if [ -z "$s3_bucket" ]; then
    echo "❌ Error: S3 bucket not specified" >&2
    echo "   Use: --bucket BUCKET or the AWS_S3_BUCKET variable" >&2
    exit 1
fi

if [ -z "$aws_access_key" ] || [ -z "$aws_secret_key" ]; then
    echo "❌ Error: AWS keys not specified" >&2
    echo "   Use: --access-key and --secret-key" >&2
    echo "   Or the AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY variables" >&2
    exit 1
fi

# Check that test results exist
if [ ! -d "$repo_dir/build/allure-results" ] || [ -z "$(find "$repo_dir/build/allure-results" -maxdepth 1 -type f -name '*-result.json' -print -quit)" ]; then
    echo "❌ Test results not found in build/allure-results/" >&2
    echo "   Run the tests first" >&2
    exit 1
fi

# Determine the S3 host
if [ -n "$s3_endpoint" ]; then
    # Custom endpoint (MinIO, etc.)
    s3_host="$s3_endpoint"
    s3_url_base="$s3_endpoint"
else
    # AWS S3
    s3_host="${s3_bucket}.s3.${s3_region}.amazonaws.com"
    s3_url_base="https://${s3_host}"
fi

# Strip the protocol if present
s3_host=$(echo "$s3_host" | sed 's|^https://||;s|^http://||')

# Determine the protocol
if [[ "$s3_endpoint" == http://* ]]; then
    protocol="http"
else
    protocol="https"
fi

echo "📊 Preparing to upload to S3..."
echo "   Bucket: $s3_bucket"
echo "   Region: $s3_region"
echo "   Host: $s3_host"

# Check S3 access
if [ "$skip_validate" = false ]; then
    echo "🔍 Checking S3 access..."

    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: AWS4-HMAC-SHA256 Credential=$aws_access_key/$(date +%Y%m%d)/${s3_region}/s3/aws4_request" \
        "${protocol}://${s3_host}/" 2>/dev/null || echo "000")

    if [ "$response" = "000" ] || [ "$response" = "403" ] || [ "$response" = "404" ]; then
        echo "⚠️  Could not verify S3 access"
        echo "   Check the keys and bucket, continuing..."
    else
        echo "✓ S3 access OK"
    fi
fi

# Download Allure CLI if needed
if [ ! -x "$repo_dir/build/allure/commandline/bin/allure" ]; then
    echo "⬇️  Downloading Allure CLI..."
    download_log=$("$repo_dir/gradlew" --no-daemon downloadAllure 2>&1) || {
        echo "❌ Failed to download Allure CLI" >&2
        echo "$download_log" >&2
        exit 1
    }
fi

allure_bin="$repo_dir/build/allure/commandline/bin/allure"

# Build the Allure report
echo "📝 Building the Allure report..."
temp_report=$(mktemp -d)
trap "rm -rf '$temp_report'" EXIT

"$allure_bin" generate "$repo_dir/build/allure-results" \
    --clean \
    --single-file \
    --output "$temp_report/report" >/dev/null 2>&1

# S3 upload structure
timestamp=$(date '+%Y/%m/%d/%H-%M-%S')
s3_path="reports/$timestamp"

echo "📤 Uploading files to S3..."
echo "   Path: s3://$s3_bucket/$s3_path/"

# Function for uploading a file to S3
upload_to_s3() {
    local file_path="$1"
    local s3_key="$2"
    local content_type="${3:-text/html}"

    # If a MinIO endpoint is used, upload with mc (MinIO Client)
    if [ -n "$s3_endpoint" ]; then
        # Make sure mc is installed
        if ! command -v mc &> /dev/null; then
            echo "  ✗ mc (MinIO Client) is not installed. Use: brew install minio-mc" >&2
            return 1
        fi

        # Set up the MinIO alias if it does not exist
        if ! mc alias list 2>/dev/null | grep -q "^minio "; then
            mc alias set minio "$s3_endpoint" "$aws_access_key" "$aws_secret_key" >/dev/null 2>&1
        fi

        # Upload via mc
        local mc_output
        if mc_output=$(mc cp "$file_path" "minio/$s3_bucket/$s3_key" 2>&1 >/dev/null); then
            echo "  ✓ Uploaded: $s3_key"
            return 0
        else
            echo "  ✗ Failed to upload: $s3_key" >&2
            [ -n "$mc_output" ] && echo "    $mc_output" >&2
            return 1
        fi
    fi

    # For AWS S3 use AWS Signature V4
    local amz_date=$(date -u '+%Y%m%dT%H%M%SZ')
    local datestamp=$(date -u '+%Y%m%d')

    # File hash
    local payload_hash=$(openssl dgst -sha256 -hex "$file_path" | awk '{print $2}')

    # Canonical request
    local tmp_canonical=$(mktemp)
    cat > "$tmp_canonical" << CANONICAL
PUT
/${s3_key}

host:${s3_host}
x-amz-content-sha256:${payload_hash}
x-amz-date:${amz_date}

host;x-amz-content-sha256;x-amz-date
${payload_hash}
CANONICAL

    local canonical_hash=$(openssl dgst -sha256 -hex "$tmp_canonical" | awk '{print $2}')
    rm -f "$tmp_canonical"

    # String to sign
    local tmp_string=$(mktemp)
    cat > "$tmp_string" << SIGN
AWS4-HMAC-SHA256
${amz_date}
${datestamp}/${s3_region}/s3/aws4_request
${canonical_hash}
SIGN

    # Calculate signature
    local kSecret="AWS4${aws_secret_key}"
    local kDate=$(printf '%s' "$datestamp" | openssl dgst -sha256 -mac HMAC -macopt key:"$kSecret" -hex | awk '{print $2}')
    local kRegion=$(printf '%s' "${s3_region}" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kDate" -hex | awk '{print $2}')
    local kService=$(printf '%s' "s3" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kRegion" -hex | awk '{print $2}')
    local kSigning=$(printf '%s' "aws4_request" | openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kService" -hex | awk '{print $2}')
    local signature=$(openssl dgst -sha256 -mac HMAC -macopt hexkey:"$kSigning" -hex "$tmp_string" | awk '{print $2}')
    rm -f "$tmp_string"

    # Authorization header
    local auth_header="AWS4-HMAC-SHA256 Credential=${aws_access_key}/${datestamp}/${s3_region}/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=${signature}"

    # Upload file
    local response_body=$(mktemp)
    local http_code=$(curl -s -w "%{http_code}" -o "$response_body" \
        -X PUT \
        -H "host: ${s3_host}" \
        -H "x-amz-content-sha256: ${payload_hash}" \
        -H "x-amz-date: ${amz_date}" \
        -H "Authorization: ${auth_header}" \
        --data-binary @"$file_path" \
        "${protocol}://${s3_host}/${s3_key}")

    if [ "$http_code" = "200" ] || [ "$http_code" = "204" ]; then
        rm -f "$response_body"
        echo "  ✓ Uploaded: $s3_key"
        return 0
    else
        echo "  ✗ Error $http_code while uploading: $s3_key" >&2
        if [ -s "$response_body" ]; then
            echo "    Response:" >&2
            sed 's/^/    /' "$response_body" >&2
        fi
        rm -f "$response_body"
        return 1
    fi
}

# Upload the HTML report
echo ""
upload_to_s3 "$temp_report/report/index.html" "$s3_path/index.html" "text/html; charset=utf-8"

# Create an index page with links
echo "📋 Creating the index..."

cat > "$temp_report/index-page.html" << 'INDEX_PAGE'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Semaphore Test Results</title>
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
        <h1>📊 Semaphore Test Results</h1>
        <p>Automated test results uploaded to S3</p>
        <div id="reports" class="report-list"></div>
        <div id="empty" class="no-reports">
            <p>No published results yet</p>
        </div>
    </div>
</body>
</html>
INDEX_PAGE

upload_to_s3 "$temp_report/index-page.html" "index.html" "text/html; charset=utf-8"

echo ""
echo "✅ Results published to S3!"
echo ""

# Print access information
if [ -n "$report_public_url" ]; then
    echo "📊 Report URL:"
    echo "   ${report_public_url}/${s3_path}"
    echo ""
    echo "📋 Main page:"
    echo "   ${report_public_url}"
elif [ -n "$s3_endpoint" ]; then
    # Custom endpoint
    echo "📊 Report URL:"
    echo "   ${protocol}://${s3_host}/${s3_path}/index.html"
    echo ""
    echo "📋 Main page:"
    echo "   ${protocol}://${s3_host}/index.html"
else
    # AWS S3
    echo "📊 Report URL (S3):"
    echo "   s3://${s3_bucket}/${s3_path}/index.html"
    echo ""
    echo "📊 Report URL (HTTP, if public):"
    echo "   https://${s3_bucket}.s3.${s3_region}.amazonaws.com/${s3_path}/index.html"
    echo ""
    echo "📋 Main page:"
    echo "   https://${s3_bucket}.s3.${s3_region}.amazonaws.com/index.html"
fi

echo ""
echo "💡 Tip: For public access, set a public ACL on the S3 objects"
