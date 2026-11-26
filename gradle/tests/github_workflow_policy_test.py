"""Offline regression test for the workflow policy. Run after populating the Gradle cache."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class WorkflowPolicyTest(unittest.TestCase):
    def test_current_workflows_and_policy_violations(self):
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory)
            gradle = project / "gradle"
            gradle.mkdir()
            for name in ("github-workflow-policy.gradle", "verification-metadata.xml", "verification-keyring.keys"):
                shutil.copy(ROOT / "gradle" / name, gradle)
            workflows = project / ".github/workflows"
            shutil.copytree(ROOT / ".github/workflows", workflows)
            (project / "settings.gradle").touch()
            (project / "build.gradle").write_text(
                "tasks.register('verifyGithubActionsSecurity')\n"
                "apply from: 'gradle/github-workflow-policy.gradle'\n"
            )
            notice = workflows / "contribution-notice.yml"
            notice.write_bytes(notice.read_text(encoding="utf-8").replace("\n", "\r\n").encode())
            wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
            command = [str(wrapper), "--offline", "-p", directory, "verifyGithubActionsSecurity"]
            subprocess.run(command, check=True)

            with notice.open("a", encoding="utf-8") as stream:
                stream.write("\n# Changed privileged workflow\n")
            (workflows / "fixture.yml").write_text(
                "on: [workflow_run]\n"
                "jobs:\n"
                "  example:\n"
                "    runs-on: ubuntu-24.04\n"
                "    steps:\n"
                "      - uses: gradle/actions/setup-gradle@" + "0" * 40 + "\n"
                "      - run: echo \"${{ github.head_ref }}\"\n"
            )
            (workflows / "fixture-comment.yml").write_text(
                "on: issue_comment\n"
                "permissions: {}\n"
                "jobs:\n"
                "  comment:\n"
                "    runs-on: ubuntu-24.04\n"
                "    steps:\n"
                "      - uses: actions/github-script@" + "0" * 40 + "\n"
                "        with:\n"
                "          script: console.log('${{ github.head_ref }}')\n"
            )
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            self.assertNotEqual(result.returncode, 0)
            for expected in (
                "contribution-notice.yml: privileged trigger",
                "fixture.yml: privileged trigger",
                "fixture.yml: declare top-level permissions",
                "fixture.yml: example: enable validate-wrappers",
                "fixture.yml: example: pass expressions through env",
                "fixture-comment.yml: privileged trigger",
                "fixture-comment.yml: comment: pass expressions through env",
            ):
                self.assertIn(expected, result.stdout)


if __name__ == "__main__":
    unittest.main()
