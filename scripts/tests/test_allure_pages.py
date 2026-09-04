import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


MODULE_PATH = Path(__file__).parents[1] / "allure_pages.py"
SPEC = importlib.util.spec_from_file_location("allure_pages", MODULE_PATH)
allure_pages = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(allure_pages)


class AllurePagesTest(unittest.TestCase):
    def test_collects_profile_versions_and_result_counts(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            results = root / "results" / "allure-results-upgrade-sqlite-local"
            results.mkdir(parents=True)
            (results / "passed-result.json").write_text('{"status":"passed"}', encoding="utf-8")
            (results / "failed-result.json").write_text('{"status":"failed"}', encoding="utf-8")
            manifest = root / "profiles" / "upgrade-sqlite-local" / "profile.yaml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                "id: upgrade-sqlite-local\n"
                "semaphore_version: v2.19.8\n"
                "previous_semaphore_version: v2.19.7\n",
                encoding="utf-8",
            )

            reports = allure_pages.collect_report_metadata(root / "results", root / "profiles")

            self.assertEqual(1, len(reports))
            self.assertEqual("v2.19.7 → v2.19.8", reports[0]["versionLabel"])
            self.assertEqual(2, reports[0]["tests"])
            self.assertEqual(1, reports[0]["counts"]["passed"])
            self.assertEqual(1, reports[0]["counts"]["failed"])

    def test_archives_groups_and_prunes_reports(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "current"
            source.mkdir()
            (source / "index.html").write_text("current report", encoding="utf-8")
            (source / "report-metadata.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "title": "Semaphore CI",
                        "runUrl": "https://github.com/example/repo/actions/runs/200",
                        "versions": ["v2.19.8"],
                        "reports": [
                            {
                                "name": "core-sqlite-local",
                                "profile": "core-sqlite-local",
                                "version": "v2.19.8",
                                "previousVersion": "",
                                "versionLabel": "v2.19.8",
                                "tests": 2,
                                "counts": {"failed": 0, "broken": 0, "passed": 2, "skipped": 0, "unknown": 0},
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            site = root / "site"
            old_run = site / "reports" / "2025-12-01" / "ci" / "100-1"
            old_run.mkdir(parents=True)
            (old_run / "run.json").write_text(
                json.dumps(
                    {
                        "workflow": "CI",
                        "displayTitle": "old",
                        "conclusion": "success",
                        "runId": 100,
                        "createdAt": "2025-12-01T10:00:00Z",
                        "date": "2025-12-01",
                    }
                ),
                encoding="utf-8",
            )
            args = type(
                "Args",
                (),
                {
                    "source_dir": source,
                    "site_dir": site,
                    "workflow": "CI",
                    "display_title": "Fix <output>",
                    "event": "push",
                    "conclusion": "success",
                    "run_id": 200,
                    "run_attempt": 1,
                    "run_number": 20,
                    "created_at": "2026-01-15T11:30:00Z",
                    "head_sha": "1234567890abcdef",
                    "repository": "example/repo",
                    "run_url": "https://github.com/example/repo/actions/runs/200",
                    "retention_days": 30,
                    "max_runs": 60,
                    "now": "2026-01-15T12:00:00Z",
                },
            )()

            allure_pages.archive_run(args)

            current = site / "reports" / "2026-01-15" / "ci" / "200-1"
            self.assertTrue((current / "index.html").is_file())
            self.assertFalse(old_run.exists())
            index = (site / "index.html").read_text(encoding="utf-8")
            self.assertIn("Fix &lt;output&gt;", index)
            self.assertIn("v2.19.8", index)
            self.assertTrue((site / "latest" / "ci" / "index.html").is_file())
            manifest = json.loads((site / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual([200], [run["runId"] for run in manifest["runs"]])


if __name__ == "__main__":
    unittest.main()
