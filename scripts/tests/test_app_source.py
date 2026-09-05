"""Behaviour tests for scripts/app-source.sh.

The script talks to the GitHub API through `gh` and to the registry through `docker`. Both are
replaced by stubs on PATH, so the tests cover the resolution logic, the reuse decision and the
error handling without any network access or Docker build.
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

    def stub_gh(self, sha=HEAD_SHA, pull_status=0, repository_status=0):
        self.write_stub(
            "gh",
            f"""
case "$2" in
  */pulls/*)
    if [ {pull_status} -ne 0 ]; then
      printf 'gh: Not Found (HTTP 404)\\n' >&2
      exit {pull_status}
    fi
    printf '{sha}\\n'
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

    def declaration(self, content):
        path = self.root / "application-under-test.yml"
        path.write_text(content, encoding="utf-8")
        return path

    def run_script(self, action="env", environment=None, expect_success=True):
        env = {
            "PATH": f"{self.stub_dir}:{os.environ['PATH']}",
            "HOME": str(self.root),
            "APP_SOURCE_FILE": str(self.root / "missing.yml"),
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

    def test_commented_out_declaration_stays_in_normal_mode(self):
        self.stub_gh()
        self.stub_docker()
        declaration = self.declaration(
            "# application:\n#   repository: semaphoreui/semaphore\n#   pull_request: 123\n"
        )

        values = self.parse(
            self.run_script(environment={"APP_SOURCE_FILE": str(declaration)}).stdout
        )

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("", values["APP_PR"])

    def test_the_shipped_declaration_template_keeps_normal_mode(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(
            self.run_script(
                environment={
                    "APP_SOURCE_FILE": str(REPOSITORY_ROOT / "application-under-test.yml")
                }
            ).stdout
        )

        self.assertEqual("docker-image", values["APP_SOURCE"])

    def test_an_explicit_app_image_is_preserved(self):
        self.stub_gh()
        self.stub_docker()

        values = self.parse(
            self.run_script(environment={"APP_IMAGE": "semaphoreui/semaphore:v2.19.12"}).stdout
        )

        self.assertEqual("docker-image", values["APP_SOURCE"])
        self.assertEqual("semaphoreui/semaphore:v2.19.12", values["APP_IMAGE"])


class PullRequestModeTest(AppSourceTestCase):
    def test_environment_link_resolves_sha_and_image(self):
        self.stub_gh()
        self.stub_docker(manifest_status=1)

        values = self.parse(
            self.run_script(
                environment={
                    "APP_PR": "123",
                    "APP_IMAGE_REPOSITORY": "ghcr.io/semaphoreui/integration-tests/semaphore-ci",
                }
            ).stdout
        )

        self.assertEqual("pull-request", values["APP_SOURCE"])
        self.assertEqual("semaphoreui/semaphore", values["APP_REPOSITORY"])
        self.assertEqual(HEAD_SHA, values["APP_SHA"])
        self.assertEqual(
            f"ghcr.io/semaphoreui/integration-tests/semaphore-ci:ci-pr-123-{HEAD_SHA}",
            values["APP_IMAGE"],
        )

    def test_declarative_link_resolves_the_same_way(self):
        self.stub_gh()
        self.stub_docker()
        declaration = self.declaration(
            "application:\n  repository: semaphoreui/semaphore\n  pull_request: 123\n"
        )

        values = self.parse(
            self.run_script(environment={"APP_SOURCE_FILE": str(declaration)}).stdout
        )

        self.assertEqual("pull-request", values["APP_SOURCE"])
        self.assertEqual("123", values["APP_PR"])
        self.assertEqual("semaphoreui/semaphore", values["APP_REPOSITORY"])

    def test_repository_urls_are_normalised(self):
        self.stub_gh()
        self.stub_docker()

        for value in (
            "semaphoreui/semaphore",
            "https://github.com/semaphoreui/semaphore.git",
            "git@github.com:semaphoreui/semaphore.git",
        ):
            with self.subTest(repository=value):
                values = self.parse(
                    self.run_script(
                        environment={"APP_PR": "123", "APP_REPOSITORY": value}
                    ).stdout
                )
                self.assertEqual("semaphoreui/semaphore", values["APP_REPOSITORY"])

    def test_ci_inputs_win_over_the_declarative_file(self):
        self.stub_gh()
        self.stub_docker()
        declaration = self.declaration("application:\n  pull_request: 111\n")

        values = self.parse(
            self.run_script(
                environment={"APP_SOURCE_FILE": str(declaration), "APP_PR": "222"}
            ).stdout
        )

        self.assertEqual("222", values["APP_PR"])

    def test_link_action_resolves_without_gh_or_docker(self):
        declaration = self.declaration("application:\n  pull_request: 123\n")

        values = self.parse(
            self.run_script("link", environment={"APP_SOURCE_FILE": str(declaration)}).stdout
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

    def test_a_malformed_declaration_fails_instead_of_degrading(self):
        self.stub_gh()
        self.stub_docker()
        declaration = self.declaration("application:\n  pull_request 123\n")

        result = self.run_script(
            environment={"APP_SOURCE_FILE": str(declaration)}, expect_success=False
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("unexpected line", result.stderr)

    def test_a_non_numeric_pull_request_fails(self):
        self.stub_gh()
        self.stub_docker()

        result = self.run_script(environment={"APP_PR": "feature/BOOK-123"}, expect_success=False)

        self.assertEqual(1, result.returncode)
        self.assertIn("invalid application pull request number", result.stderr)


if __name__ == "__main__":
    unittest.main()
