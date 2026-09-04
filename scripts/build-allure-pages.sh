#!/bin/sh

set -eu

downloads_dir=${1:?"allure results directory is required"}
report_title=${2:?"report title is required"}
run_url=${3:?"workflow run URL is required"}

allure_bin=build/allure/commandline/bin/allure
site_dir=build/pages-site

[ -d "$downloads_dir" ] || {
  printf 'Allure results directory does not exist: %s\n' "$downloads_dir" >&2
  exit 1
}
[ -x "$allure_bin" ] || {
  printf 'Allure CLI does not exist: %s; run ./gradlew downloadAllure first\n' "$allure_bin" >&2
  exit 1
}

rm -rf "$site_dir"
mkdir -p "$site_dir/reports"
touch "$site_dir/.nojekyll"

index_file="$site_dir/index.html"
generated_at=$(date -u '+%Y-%m-%d %H:%M UTC')

printf '%s\n' \
  '<!doctype html>' \
  '<html lang="en">' \
  '<head>' \
  '  <meta charset="utf-8">' \
  '  <meta name="viewport" content="width=device-width, initial-scale=1">' \
  "  <title>$report_title · Allure</title>" \
  '  <style>' \
  '    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, sans-serif; color: #102a43; background: #f3f7fa; }' \
  '    body { max-width: 980px; margin: 0 auto; padding: 48px 24px 72px; }' \
  '    h1 { margin: 0 0 8px; font-size: clamp(32px, 5vw, 52px); }' \
  '    .meta { color: #627d98; margin-bottom: 32px; }' \
  '    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }' \
  '    .card { display: block; padding: 22px; border: 1px solid #d9e2ec; border-top: 4px solid #2f6bff; border-radius: 10px; background: #fff; color: inherit; text-decoration: none; box-shadow: 0 8px 24px rgba(16,42,67,.06); }' \
  '    .card:hover { border-top-color: #00a7a5; transform: translateY(-1px); }' \
  '    .name { display: block; font-size: 18px; font-weight: 700; margin-bottom: 8px; }' \
  '    .count { color: #627d98; }' \
  '    .run { display: inline-block; margin-top: 30px; color: #2f6bff; }' \
  '  </style>' \
  '</head>' \
  '<body>' \
  "  <h1>$report_title</h1>" \
  "  <p class=\"meta\">Allure reports generated $generated_at</p>" \
  '  <div class="grid">' > "$index_file"

report_count=0
for results_dir in "$downloads_dir"/allure-results-*; do
  [ -d "$results_dir" ] || continue
  report_name=$(basename "$results_dir")
  report_name=${report_name#allure-results-}
  case "$report_name" in
    ''|*[!A-Za-z0-9._-]*)
      printf 'Unsafe Allure artifact name: %s\n' "$report_name" >&2
      exit 1
      ;;
  esac

  first_result=$(find "$results_dir" -maxdepth 1 -type f -name '*-result.json' -print -quit)
  [ -n "$first_result" ] || continue

  "$allure_bin" generate "$results_dir" --clean --single-file --output "$site_dir/reports/$report_name"
  result_count=$(find "$results_dir" -maxdepth 1 -type f -name '*-result.json' | wc -l | tr -d ' ')
  printf '    <a class="card" href="reports/%s/index.html"><span class="name">%s</span><span class="count">%s test results</span></a>\n' \
    "$report_name" "$report_name" "$result_count" >> "$index_file"
  report_count=$((report_count + 1))
done

[ "$report_count" -gt 0 ] || {
  printf 'No Allure result artifacts were found in %s\n' "$downloads_dir" >&2
  exit 1
}

printf '%s\n' \
  '  </div>' \
  "  <a class=\"run\" href=\"$run_url\">Open workflow run</a>" \
  '</body>' \
  '</html>' >> "$index_file"

python3 scripts/allure_pages.py metadata \
  --results-dir "$downloads_dir" \
  --profiles-dir test-environment/profiles \
  --output "$site_dir/report-metadata.json" \
  --title "$report_title" \
  --run-url "$run_url"

printf 'Prepared %s Allure report(s) in %s\n' "$report_count" "$site_dir"
