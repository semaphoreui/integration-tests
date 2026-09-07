"""Behaviour tests for scripts/app-source.sh.

The script talks to the GitHub API through `gh` and to the registry through `docker`. Both are
replaced by stubs on PATH, so the tests cover the resolution logic, the reuse decision and the
error handling without any network access or Docker build.

The link between a test run and an application pull request is declared in the description of the
test pull request, which reaches the script as APP_LINK_BODY.
"""

import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest


REPOSITORY_ROOT = Path(__file__).parents[2]
SCRIPT = REPOSITORY_ROOT / "scripts" / "app-source.sh"
HEAD_SHA = "ffdb25923c69e3d6e3f62c555fd339014ae03864"


class AppSourceTestCase(unittest.TestCase):
    def setUp(self):
        self._temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self._temporary_directory.name)
        self.stub_dir = self.root / "bin"
        self.stub_dir.mkdir()
        self.addCleanup(self._temporary_directory.cleanup)

    def write_stub(self, name, body):
        path = self.stub_dir / name
        path.write_text("#!/bin/sh\n" + body, encoding="utf-8")
        path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    def stub_gh(self, sha=HEAD_SHA, pull_status=0, repository_status=0, state="open"):
        self.write_stub(
            "gh",
            f"""
case "$2" in
  */pulls/*)
    if [ {pull_status} -ne 0 ]; then
      printf 'gh: Not Found (HTTP 404)\\n' >&2
      exit {pull_status}
    fi
    printf '{sha}\\t{state}\\n'
    ;;
  *)
    exit {repository_status}
    ;;
esac
""",
        )

    def stub_docker(self, manifest_status=1, local_status=1):
        self.write_stub(
            "docker",
            f"""
case "$1" in
  manifest) exit {manifest_status} ;;
  image) exit {local_status} ;;
  *) exit 0 ;;
esac
""",
        )

    def body_file(self, content):
        path = self.root / "pull-request-body.md"
        path.write_text(content, encoding="utf-8")
        return path

    def run_script(self, action="env", environment=None, expect_success=True):
        env = {
            "PATH": f"{self.stub_dir}:{os.environ['PATH']}",
            "HOME": str(self.root),
        }
        env.update(environment or {})
        completed = subprocess.run(
            [str(SCRIPT), action],
            capture_output=True,
            text=True,
            env=env,
            check=False,
        )
        if expect_success:
            self.assertEqual(
                0, completed.returncode, msg=f"stdout={completed.stdout}\nstderr={completed.stderr}"
            )
        return completed

    @staticmethod
    def parse(output):
        values = {}
        for line in output.splitlines():
            if "=" in line and line.split("=", 1)[0].isupper():
                key, value = line.split("=", 1)
                values[key] = value
        return values


class NormalModeTest(AppSourceTestCase):
    def test_without_a_link_nothing_is_resolved_or_built(self):
        self.stub_gh()
        self.stub_docker()

        result = self.run_script("resolve")
        values = self.parse(result.stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("", values["APP_IMAGE"])
        self.assertEqual("", values["APP_SHA"])
        self.assertEqual("false", values["APP_BUILD_REQUIRED"])
        self.assertIn("Application source: Docker image", result.stdout)
        self.assertIn("Application build: skipped", result.stdout)

    def test_a_description_without_the_trailer_stays_in_normal_mode(self):
        self.stub_gh()
        self.stub_docker()
        body = "Adds a regression test for schedules.\n\nSee the linked issue for context."

        values = self.parse(self.run_script(environment={"APP_LINK_BODY": body}).stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("", values["APP_PR"])

    def test_an_empty_description_stays_in_normal_mode(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(self.run_script(environment={"APP_LINK_BODY": ""}).stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])

    def test_a_commented_out_template_example_is_not_a_declaration(self):
        self.stub_gh()
        self.stub_docker()
        body = (
            "<!--\n"
            "Link an application pull request like this:\n"
            "Application-PR: semaphoreui/semaphore#999\n"
            "-->\n"
            "Ordinary test change.\n"
        )

        values = self.parse(self.run_script(environment={"APP_LINK_BODY": body}).stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("", values["APP_PR"])

    def test_the_trailer_must_start_a_line(self):
        self.stub_gh()
        self.stub_docker()
        body = "We considered the Application-PR: semaphoreui/semaphore#999 approach but did not."

        values = self.parse(self.run_script(environment={"APP_LINK_BODY": body}).stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])

    def test_an_explicit_app_image_is_preserved(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(
            self.run_script(environment={"APP_IMAGE": "semaphoreui/semaphore:v2.19.12"}).stdout
        )

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("semaphoreui/semaphore:v2.19.12", values["APP_IMAGE"])


class DescriptionLinkTest(AppSourceTestCase):
    def test_owner_name_and_number(self):
        self.stub_gh()
        self.stub_docker()
        body = "Covers the new runner isolation.\n\nApplication-PR: semaphoreui/semaphore#123\n"

        values = self.parse(
            self.run_script(
                environment={
                    "APP_LINK_BODY": body,
                    "APP_IMAGE_REPOSITORY": "ghcr.io/semaphoreui/integration-tests/semaphore-ci",
                }
            ).stdout
        )

        self.assertEqual("pull-request", values["APP_SOURCE"])
        self.assertEqual("semaphoreui/semaphore", values["APP_REPOSITORY"])
        self.assertEqual("123", values["APP_PR"])
        self.assertEqual("pull-request-body", values["APP_LINK_SOURCE"])
        self.assertEqual(
            f"ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-{HEAD_SHA}",
            values["APP_IMAGE"],
        )

    def test_every_accepted_reference_form(self):
        self.stub_gh()
        self.stub_docker()

        for reference in (
            "semaphoreui/semaphore#123",
            "#123",
            "123",
            "https://github.com/semaphoreui/semaphore/pull/123",
            "https://github.com/semaphoreui/semaphore/pull/123/files",
        ):
            with self.subTest(reference=reference):
                values = self.parse(
                    self.run_script(
                        environment={"APP_LINK_BODY": f"Application-PR: {reference}"}
                    ).stdout
                )
                self.assertEqual("pull-request", values["APP_SOURCE"])
                self.assertEqual("123", values["APP_PR"])
                self.assertEqual("semaphoreui/semaphore", values["APP_REPOSITORY"])

    def test_the_key_is_case_and_separator_insensitive(self):
        self.stub_gh()
        self.stub_docker()

        for key in ("Application-PR", "application pr", "APPLICATION_PR", "  Application-Pr"):
            with self.subTest(key=key):
                values = self.parse(
                    self.run_script(environment={"APP_LINK_BODY": f"{key}: #123"}).stdout
                )
                self.assertEqual("123", values["APP_PR"])

    def test_a_windows_style_description_is_accepted(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(
            self.run_script(
                environment={"APP_LINK_BODY": "Summary\r\n\r\nApplication-PR: #123\r\n"}
            ).stdout
        )

        self.assertEqual("123", values["APP_PR"])

    def test_the_description_can_be_supplied_as_a_file(self):
        self.stub_gh()
        self.stub_docker()
        body = self.body_file("Application-PR: semaphoreui/semaphore#123\n")

        values = self.parse(
            self.run_script(environment={"APP_LINK_BODY_FILE": str(body)}).stdout
        )

        self.assertEqual("123", values["APP_PR"])

    def test_ci_inputs_win_over_the_description(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(
            self.run_script(
                environment={"APP_LINK_BODY": "Application-PR: #111", "APP_PR": "222"}
            ).stdout
        )

        self.assertEqual("222", values["APP_PR"])
        self.assertEqual("ci-input", values["APP_LINK_SOURCE"])

    def test_link_action_resolves_without_gh_or_docker(self):
        values = self.parse(
            self.run_script("link", environment={"APP_LINK_BODY": "Application-PR: #123"}).stdout
        )

        self.assertEqual("pull-request", values["APP_SOURCE"])
        self.assertEqual("123", values["APP_PR"])
        self.assertEqual("", values["APP_SHA"])
        self.assertEqual("", values["APP_IMAGE"])


class ImageReuseTest(AppSourceTestCase):
    def test_an_existing_image_is_reused_without_building(self):
        self.stub_gh()
        self.stub_docker(manifest_status=0)

        result = self.run_script("ensure", environment={"APP_PR": "123"})
        values = self.parse(result.stdout)

        self.assertEqual("true", values["APP_IMAGE_EXISTS"])
        self.assertEqual("false", values["APP_BUILD_REQUIRED"])
        self.assertEqual("false", values["APP_BUILD_PERFORMED"])
        self.assertIn("Application image already exists", result.stdout)
        self.assertIn("Application build: skipped", result.stdout)

    def test_a_missing_image_requests_a_build(self):
        self.stub_gh()
        self.stub_docker(manifest_status=1)

        result = self.run_script("resolve", environment={"APP_PR": "123"})
        values = self.parse(result.stdout)

        self.assertEqual("false", values["APP_IMAGE_EXISTS"])
        self.assertEqual("true", values["APP_BUILD_REQUIRED"])
        self.assertIn("Application image not found", result.stdout)
        self.assertIn("Building application...", result.stdout)

    def test_the_registry_decides_when_the_image_is_pushed(self):
        self.stub_gh()
        self.stub_docker(manifest_status=1, local_status=0)

        values = self.parse(self.run_script(environment={"APP_PR": "123"}).stdout)

        self.assertEqual("false", values["APP_IMAGE_EXISTS"])
        self.assertEqual("true", values["APP_BUILD_REQUIRED"])

    def test_the_local_store_decides_when_the_image_is_not_pushed(self):
        self.stub_gh()
        self.stub_docker(manifest_status=1, local_status=0)

        values = self.parse(
            self.run_script(environment={"APP_PR": "123", "APP_BUILD_PUSH": "false"}).stdout
        )

        self.assertEqual("true", values["APP_IMAGE_EXISTS"])
        self.assertEqual("false", values["APP_BUILD_REQUIRED"])

    def test_a_new_commit_of_the_same_pull_request_yields_a_new_image(self):
        other_sha = "abc123456789abc123456789abc123456789abcd"
        self.stub_docker()

        self.stub_gh(sha=HEAD_SHA)
        first = self.parse(self.run_script(environment={"APP_PR": "123"}).stdout)["APP_IMAGE"]
        self.stub_gh(sha=other_sha)
        second = self.parse(self.run_script(environment={"APP_PR": "123"}).stdout)["APP_IMAGE"]

        self.assertNotEqual(first, second)
        self.assertTrue(first.endswith(HEAD_SHA))
        self.assertTrue(second.endswith(other_sha))

    def test_different_pull_requests_do_not_share_an_image(self):
        self.stub_gh()
        self.stub_docker()

        images = {
            self.parse(self.run_script(environment={"APP_PR": number}).stdout)["APP_IMAGE"]
            for number in ("100", "101", "102")
        }

        self.assertEqual(3, len(images))

    def test_temporary_images_use_their_own_namespace(self):
        self.stub_gh()
        self.stub_docker()

        image = self.parse(
            self.run_script(
                environment={"APP_PR": "123", "GITHUB_REPOSITORY": "semaphoreui/integration-tests"}
            ).stdout
        )["APP_IMAGE"]

        self.assertEqual(
            f"ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-{HEAD_SHA}", image
        )
        self.assertNotIn("semaphoreui/semaphore:", image)


class ClosedApplicationPullRequestTest(AppSourceTestCase):
    def test_a_merged_application_pull_request_falls_back_to_normal_mode(self):
        self.stub_gh(state="closed")
        self.stub_docker()

        result = self.run_script(
            "resolve", environment={"APP_LINK_BODY": "Application-PR: #123"}
        )
        values = self.parse(result.stdout)

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("closed", values["APP_PR_STATE"])
        self.assertEqual("", values["APP_IMAGE"])
        self.assertEqual("false", values["APP_BUILD_REQUIRED"])
        self.assertIn("no version left to test", result.stdout)
        self.assertIn("Remove the Application-PR line", result.stdout)
        self.assertIn("Application build: skipped", result.stdout)

    def test_an_open_application_pull_request_still_builds(self):
        self.stub_gh(state="open")
        self.stub_docker()

        values = self.parse(self.run_script(environment={"APP_PR": "123"}).stdout)

        self.assertEqual("pull-request", values["APP_SOURCE"])
        self.assertEqual("open", values["APP_PR_STATE"])
        self.assertEqual("true", values["APP_BUILD_REQUIRED"])


class ErrorHandlingTest(AppSourceTestCase):
    def test_a_missing_pull_request_fails(self):
        self.stub_gh(pull_status=1, repository_status=0)
        self.stub_docker()

        result = self.run_script(environment={"APP_PR": "123"}, expect_success=False)

        self.assertEqual(1, result.returncode)
        self.assertIn("Application PR #123 not found", result.stderr)

    def test_an_unreachable_repository_fails(self):
        self.stub_gh(pull_status=1, repository_status=1)
        self.stub_docker()

        result = self.run_script(environment={"APP_PR": "123"}, expect_success=False)

        self.assertEqual(1, result.returncode)
        self.assertIn("Unable to access application repository", result.stderr)

    def test_an_unusable_sha_fails(self):
        self.stub_gh(sha="null")
        self.stub_docker()

        result = self.run_script(environment={"APP_PR": "123"}, expect_success=False)

        self.assertEqual(1, result.returncode)
        self.assertIn("Unable to resolve the HEAD SHA", result.stderr)

    def test_two_declarations_fail_instead_of_picking_one(self):
        self.stub_gh()
        self.stub_docker()
        body = "Application-PR: #111\nApplication-PR: #222\n"

        result = self.run_script(
            environment={"APP_LINK_BODY": body}, expect_success=False
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("declares Application-PR 2 times", result.stderr)

    def test_an_unusable_reference_fails(self):
        self.stub_gh()
        self.stub_docker()

        for reference in ("feature/BOOK-123", "#0", "https://github.com/semaphoreui/semaphore"):
            with self.subTest(reference=reference):
                result = self.run_script(
                    environment={"APP_LINK_BODY": f"Application-PR: {reference}"},
                    expect_success=False,
                )
                self.assertEqual(1, result.returncode)
                self.assertIn("Application-PR in the pull request description", result.stderr)

    def test_a_missing_body_file_fails(self):
        self.stub_gh()
        self.stub_docker()

        result = self.run_script(
            environment={"APP_LINK_BODY_FILE": str(self.root / "absent.md")},
            expect_success=False,
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("APP_LINK_BODY_FILE does not exist", result.stderr)

    def test_a_non_numeric_ci_input_fails(self):
        self.stub_gh()
        self.stub_docker()

        result = self.run_script(environment={"APP_PR": "feature/BOOK-123"}, expect_success=False)

        self.assertEqual(1, result.returncode)
        self.assertIn("invalid application pull request number", result.stderr)


if __name__ == "__main__":
    unittest.main()
