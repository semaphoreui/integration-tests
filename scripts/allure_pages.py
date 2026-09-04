#!/usr/bin/env python3
"""Build metadata and a bounded GitHub Pages archive for Allure reports."""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import html
import json
from pathlib import Path
import re
import shutil
from typing import Any


STATUS_ORDER = ("failed", "broken", "passed", "skipped", "unknown")
VALID_CONCLUSIONS = {
    "action_required",
    "cancelled",
    "failure",
    "neutral",
    "skipped",
    "stale",
    "success",
    "timed_out",
}


def read_profile_manifest(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        key, value = line.split(":", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def test_counts(results_dir: Path) -> dict[str, int]:
    counts: Counter[str] = Counter()
    for result_file in results_dir.glob("*-result.json"):
        try:
            result = json.loads(result_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as error:
            raise ValueError(f"Cannot parse Allure result {result_file}: {error}") from error
        status = str(result.get("status", "unknown")).lower()
        counts[status if status in STATUS_ORDER else "unknown"] += 1
    return {status: counts.get(status, 0) for status in STATUS_ORDER}


def collect_report_metadata(results_root: Path, profiles_root: Path) -> list[dict[str, Any]]:
    reports: list[dict[str, Any]] = []
    for results_dir in sorted(results_root.glob("allure-results-*")):
        if not results_dir.is_dir():
            continue
        counts = test_counts(results_dir)
        total = sum(counts.values())
        if total == 0:
            continue
        name = results_dir.name.removeprefix("allure-results-")
        manifest = read_profile_manifest(profiles_root / name / "profile.yaml")
        current_version = manifest.get("semaphore_version", "")
        previous_version = manifest.get("previous_semaphore_version", "")
        version_label = current_version
        if previous_version and current_version:
            version_label = f"{previous_version} → {current_version}"
        reports.append(
            {
                "name": name,
                "profile": manifest.get("id", name),
                "version": current_version,
                "previousVersion": previous_version,
                "versionLabel": version_label,
                "tests": total,
                "counts": counts,
            }
        )
    return reports


def write_report_metadata(args: argparse.Namespace) -> None:
    reports = collect_report_metadata(args.results_dir, args.profiles_dir)
    if not reports:
        raise ValueError(f"No Allure test results found in {args.results_dir}")
    versions = sorted({report["versionLabel"] for report in reports if report["versionLabel"]})
    payload = {
        "schemaVersion": 1,
        "title": args.title,
        "runUrl": args.run_url,
        "versions": versions,
        "reports": reports,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def slug(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    if not normalized:
        raise ValueError(f"Cannot create a safe slug from {value!r}")
    return normalized


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        raise ValueError(f"Cannot parse {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return value


def discover_runs(site_dir: Path) -> list[dict[str, Any]]:
    runs: list[dict[str, Any]] = []
    for metadata_file in site_dir.glob("reports/*/*/*/run.json"):
        metadata = load_json(metadata_file)
        metadata["path"] = metadata_file.parent.relative_to(site_dir).as_posix()
        runs.append(metadata)
    return sorted(runs, key=lambda run: (run["createdAt"], run["runId"]), reverse=True)


def remove_run(site_dir: Path, run: dict[str, Any]) -> None:
    run_path = site_dir / str(run["path"])
    if run_path.is_dir():
        shutil.rmtree(run_path)


def prune_runs(site_dir: Path, now: datetime, retention_days: int, max_runs: int) -> None:
    cutoff = now - timedelta(days=retention_days)
    runs = discover_runs(site_dir)
    for run in runs:
        if parse_timestamp(str(run["createdAt"])) < cutoff:
            remove_run(site_dir, run)
    runs = discover_runs(site_dir)
    for run in runs[max_runs:]:
        remove_run(site_dir, run)
    reports_root = site_dir / "reports"
    if reports_root.is_dir():
        for directory in sorted(reports_root.glob("**/*"), reverse=True):
            if directory.is_dir() and not any(directory.iterdir()):
                directory.rmdir()


def conclusion_label(conclusion: str) -> str:
    return conclusion.replace("_", " ").title()


def report_totals(run: dict[str, Any]) -> Counter[str]:
    totals: Counter[str] = Counter()
    for report in run.get("reports", []):
        for status, count in report.get("counts", {}).items():
            totals[status] += int(count)
    return totals


def render_index(site_dir: Path, runs: list[dict[str, Any]], retention_days: int) -> None:
    workflows = sorted({str(run["workflow"]) for run in runs})
    versions = sorted({str(version) for run in runs for version in run.get("versions", [])})
    grouped: dict[str, list[dict[str, Any]]] = {}
    for run in runs:
        grouped.setdefault(str(run["date"]), []).append(run)

    workflow_options = "".join(
        f'<option value="{html.escape(slug(workflow))}">{html.escape(workflow)}</option>'
        for workflow in workflows
    )
    version_options = "".join(
        f'<option value="{html.escape(version)}">{html.escape(version)}</option>' for version in versions
    )

    sections: list[str] = []
    for date, date_runs in grouped.items():
        cards: list[str] = []
        for run in date_runs:
            totals = report_totals(run)
            total = sum(totals.values())
            versions_text = ", ".join(run.get("versions", [])) or "version not recorded"
            profiles = [str(report.get("profile", report.get("name", ""))) for report in run.get("reports", [])]
            profile_preview = ", ".join(profiles[:4])
            if len(profiles) > 4:
                profile_preview += f" +{len(profiles) - 4}"
            created = parse_timestamp(str(run["createdAt"]))
            status = str(run["conclusion"])
            status_class = "success" if status == "success" else "failure" if status in {"failure", "timed_out"} else "other"
            counts_text = " · ".join(
                f"{totals[status_name]} {status_name}" for status_name in STATUS_ORDER if totals[status_name]
            )
            cards.append(
                f'''<article class="run-card" data-workflow="{html.escape(slug(str(run['workflow'])))}" data-versions="{html.escape('|'.join(run.get('versions', [])))}">
  <div class="card-top"><span class="workflow">{html.escape(str(run['workflow']))}</span><span class="status {status_class}">{html.escape(conclusion_label(status))}</span></div>
  <h3>{html.escape(str(run['displayTitle']))}</h3>
  <p class="version">{html.escape(versions_text)}</p>
  <p class="profiles">{html.escape(profile_preview)}</p>
  <p class="counts">{total} tests{(' · ' + html.escape(counts_text)) if counts_text else ''}</p>
  <div class="card-bottom"><span>{created:%H:%M} UTC · <a href="{html.escape(str(run['commitUrl']))}">{html.escape(str(run['shortSha']))}</a> · <a href="{html.escape(str(run['runUrl']))}">Actions</a></span><a class="report-link" href="{html.escape(str(run['path']))}/index.html">Open report →</a></div>
</article>'''
            )
        sections.append(
            f'<section class="day"><h2><time datetime="{html.escape(date)}">{html.escape(date)}</time><span>{len(date_runs)} run{"s" if len(date_runs) != 1 else ""}</span></h2><div class="runs">{"".join(cards)}</div></section>'
        )

    empty_message = '<p class="empty">No published reports match these filters.</p>'
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    document = f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="Semaphore integration test Allure report history">
  <title>Semaphore test reports</title>
  <style>
    :root {{ color-scheme: light; --ink:#17202a; --muted:#667085; --line:#d8dee8; --paper:#fff; --wash:#f5f7fb; --accent:#5b43d6; --success:#137a4f; --danger:#b42318; font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; }}
    * {{ box-sizing:border-box; }} body {{ margin:0; color:var(--ink); background:var(--wash); }}
    header {{ color:#fff; background:linear-gradient(135deg,#17142d 0%,#372785 58%,#177e89 130%); padding:54px max(24px,calc((100vw - 1120px)/2)) 42px; }}
    .eyebrow {{ margin:0 0 12px; color:#b9f3ea; font-size:13px; font-weight:800; letter-spacing:.13em; text-transform:uppercase; }}
    h1 {{ margin:0; max-width:780px; font-size:clamp(36px,6vw,66px); line-height:1.02; letter-spacing:-.04em; }}
    .intro {{ max-width:730px; margin:18px 0 0; color:#e5e2ff; font-size:18px; line-height:1.55; }}
    .summary {{ display:flex; flex-wrap:wrap; gap:10px; margin-top:26px; }} .summary span {{ padding:8px 12px; border:1px solid rgba(255,255,255,.22); border-radius:999px; background:rgba(255,255,255,.09); font-size:13px; }}
    main {{ max-width:1120px; margin:0 auto; padding:28px 24px 72px; }}
    .filters {{ display:flex; flex-wrap:wrap; gap:14px; align-items:end; padding:18px; border:1px solid var(--line); border-radius:16px; background:var(--paper); box-shadow:0 12px 34px rgba(30,41,59,.06); }}
    label {{ display:grid; gap:6px; color:var(--muted); font-size:12px; font-weight:750; letter-spacing:.04em; text-transform:uppercase; }}
    select {{ min-width:220px; padding:10px 34px 10px 12px; border:1px solid #c8d0dc; border-radius:9px; color:var(--ink); background:#fff; font:inherit; }}
    .day {{ margin-top:34px; }} .day h2 {{ display:flex; align-items:baseline; justify-content:space-between; margin:0 0 13px; font-size:24px; }} .day h2 span {{ color:var(--muted); font-size:13px; font-weight:500; }}
    .runs {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(310px,1fr)); gap:15px; }}
    .run-card {{ display:flex; min-height:245px; flex-direction:column; padding:20px; border:1px solid var(--line); border-radius:15px; background:var(--paper); box-shadow:0 9px 26px rgba(30,41,59,.05); }}
    .card-top,.card-bottom {{ display:flex; align-items:center; justify-content:space-between; gap:12px; }} .workflow {{ color:var(--accent); font-size:13px; font-weight:800; text-transform:uppercase; letter-spacing:.05em; }}
    .status {{ padding:5px 9px; border-radius:999px; background:#eef1f5; color:#475467; font-size:12px; font-weight:800; }} .status.success {{ color:var(--success); background:#e8f7ef; }} .status.failure {{ color:var(--danger); background:#fff0ee; }}
    h3 {{ margin:21px 0 10px; font-size:20px; line-height:1.3; }} .version {{ margin:0 0 8px; font-weight:750; }} .profiles,.counts {{ margin:0 0 8px; color:var(--muted); font-size:14px; line-height:1.45; }}
    .card-bottom {{ margin-top:auto; padding-top:18px; border-top:1px solid #edf0f4; color:var(--muted); font-size:13px; }} a {{ color:var(--accent); }} .report-link {{ font-weight:800; text-decoration:none; white-space:nowrap; }}
    .empty {{ display:none; padding:60px 0; color:var(--muted); text-align:center; }} footer {{ max-width:1120px; margin:0 auto; padding:0 24px 36px; color:var(--muted); font-size:12px; }}
    @media (prefers-color-scheme:dark) {{ :root {{ color-scheme:dark; --ink:#f3f4f6; --muted:#aeb7c4; --line:#354052; --paper:#171d27; --wash:#0d1118; }} select {{ color:var(--ink); background:#171d27; border-color:#465165; }} .status {{ background:#2a3341; color:#d0d5dd; }} }}
    @media (max-width:620px) {{ header {{ padding-top:38px; }} main {{ padding-inline:15px; }} .filters,label,select {{ width:100%; }} .runs {{ grid-template-columns:1fr; }} .card-bottom {{ align-items:flex-end; }} }}
  </style>
</head>
<body>
  <header><p class="eyebrow">Semaphore UI · Integration tests</p><h1>Allure report history</h1><p class="intro">Reports from trusted main-branch CI, configuration-matrix and release-upgrade runs, grouped by day and retained for {retention_days} days.</p><div class="summary"><span>{len(runs)} published runs</span><span>{len(versions)} version labels</span><span>Updated {generated_at}</span></div></header>
  <main>
    <div class="filters"><label>Workflow<select id="workflow"><option value="">All workflows</option>{workflow_options}</select></label><label>Semaphore version<select id="version"><option value="">All versions</option>{version_options}</select></label></div>
    <div id="history">{''.join(sections)}</div>{empty_message}
  </main>
  <footer>Generated from GitHub Actions artifacts. Pull-request and external-environment runs are intentionally excluded.</footer>
  <script>
    const workflow=document.querySelector('#workflow'),version=document.querySelector('#version'),empty=document.querySelector('.empty');
    function filterRuns(){{let visible=0;document.querySelectorAll('.run-card').forEach(card=>{{const show=(!workflow.value||card.dataset.workflow===workflow.value)&&(!version.value||card.dataset.versions.split('|').includes(version.value));card.hidden=!show;if(show)visible++;}});document.querySelectorAll('.day').forEach(day=>day.hidden=![...day.querySelectorAll('.run-card')].some(card=>!card.hidden));empty.style.display=visible?'none':'block';}}
    workflow.addEventListener('change',filterRuns);version.addEventListener('change',filterRuns);
  </script>
</body>
</html>
'''
    (site_dir / "index.html").write_text(document, encoding="utf-8")


def write_latest_redirects(site_dir: Path, runs: list[dict[str, Any]]) -> None:
    latest_dir = site_dir / "latest"
    if latest_dir.exists():
        shutil.rmtree(latest_dir)
    seen: set[str] = set()
    for run in runs:
        workflow_slug = slug(str(run["workflow"]))
        if workflow_slug in seen:
            continue
        seen.add(workflow_slug)
        target = f"../../{run['path']}/index.html"
        destination = latest_dir / workflow_slug
        destination.mkdir(parents=True, exist_ok=True)
        destination.joinpath("index.html").write_text(
            "<!doctype html><html><head><meta charset=\"utf-8\">"
            f"<meta http-equiv=\"refresh\" content=\"0; url={html.escape(target)}\">"
            f"<title>Latest {html.escape(str(run['workflow']))} report</title></head>"
            f"<body><a href=\"{html.escape(target)}\">Open latest report</a></body></html>\n",
            encoding="utf-8",
        )


def archive_run(args: argparse.Namespace) -> None:
    if args.conclusion not in VALID_CONCLUSIONS:
        raise ValueError(f"Unsupported workflow conclusion: {args.conclusion}")
    report_metadata = load_json(args.source_dir / "report-metadata.json")
    created_at = parse_timestamp(args.created_at)
    workflow_slug = slug(args.workflow)
    relative_path = Path("reports") / created_at.date().isoformat() / workflow_slug / f"{args.run_id}-{args.run_attempt}"
    destination = args.site_dir / relative_path
    if destination.exists():
        shutil.rmtree(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(args.source_dir, destination)

    run_metadata = {
        **report_metadata,
        "workflow": args.workflow,
        "workflowSlug": workflow_slug,
        "displayTitle": args.display_title,
        "event": args.event,
        "conclusion": args.conclusion,
        "runId": args.run_id,
        "runAttempt": args.run_attempt,
        "runNumber": args.run_number,
        "createdAt": created_at.isoformat().replace("+00:00", "Z"),
        "date": created_at.date().isoformat(),
        "headSha": args.head_sha,
        "shortSha": args.head_sha[:7],
        "commitUrl": f"https://github.com/{args.repository}/commit/{args.head_sha}",
        "runUrl": args.run_url,
    }
    destination.joinpath("run.json").write_text(
        json.dumps(run_metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    now = parse_timestamp(args.now) if args.now else datetime.now(timezone.utc)
    prune_runs(args.site_dir, now, args.retention_days, args.max_runs)
    runs = discover_runs(args.site_dir)
    args.site_dir.mkdir(parents=True, exist_ok=True)
    args.site_dir.joinpath("manifest.json").write_text(
        json.dumps({"schemaVersion": 1, "runs": runs}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    args.site_dir.joinpath(".nojekyll").touch()
    render_index(args.site_dir, runs, args.retention_days)
    write_latest_redirects(args.site_dir, runs)


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    metadata = commands.add_parser("metadata", help="Describe one generated Allure report bundle")
    metadata.add_argument("--results-dir", type=Path, required=True)
    metadata.add_argument("--profiles-dir", type=Path, required=True)
    metadata.add_argument("--output", type=Path, required=True)
    metadata.add_argument("--title", required=True)
    metadata.add_argument("--run-url", required=True)
    metadata.set_defaults(handler=write_report_metadata)

    archive = commands.add_parser("archive", help="Add one bundle to the bounded Pages history")
    archive.add_argument("--source-dir", type=Path, required=True)
    archive.add_argument("--site-dir", type=Path, required=True)
    archive.add_argument("--workflow", required=True)
    archive.add_argument("--display-title", required=True)
    archive.add_argument("--event", required=True)
    archive.add_argument("--conclusion", required=True)
    archive.add_argument("--run-id", type=int, required=True)
    archive.add_argument("--run-attempt", type=int, required=True)
    archive.add_argument("--run-number", type=int, required=True)
    archive.add_argument("--created-at", required=True)
    archive.add_argument("--head-sha", required=True)
    archive.add_argument("--repository", required=True)
    archive.add_argument("--run-url", required=True)
    archive.add_argument("--retention-days", type=int, default=30)
    archive.add_argument("--max-runs", type=int, default=60)
    archive.add_argument("--now", help=argparse.SUPPRESS)
    archive.set_defaults(handler=archive_run)
    return root


def main() -> None:
    args = parser().parse_args()
    if getattr(args, "retention_days", 1) < 1 or getattr(args, "max_runs", 1) < 1:
        raise ValueError("Retention days and maximum run count must be positive")
    args.handler(args)


if __name__ == "__main__":
    main()
