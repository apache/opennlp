#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Run the workflow's Bash version step and parse its output with OpenNLP."""

import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]


class SnapshotVersionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.work = Path(cls.temp.name)
        workflow = (ROOT / ".github/workflows/publish-snapshots.yml").read_text()
        step = workflow.split("      - id: version\n", 1)[1].split("      - name:", 1)[0]
        cls.script = textwrap.dedent(step.split("        run: |\n", 1)[1])
        mvn = cls.work / "mvn"
        mvn.write_text('#!/bin/sh\nprintf "%s\\n" "$TEST_POM_VERSION"\n')
        mvn.chmod(0o755)
        probe = cls.work / "ParseVersion.java"
        probe.write_text("""
            import opennlp.tools.util.Version;
            public class ParseVersion {
              public static void main(String[] args) {
                Version version = Version.parse(args[0]);
                if (!version.isSnapshot()) {
                  throw new AssertionError("Not a snapshot: " + args[0]);
                }
                System.out.println(version);
              }
            }
            """)
        subprocess.run([
            "javac", "-d", str(cls.work),
            str(ROOT / "opennlp-core/opennlp-runtime/src/main/java/opennlp/tools/util/Version.java"),
            str(probe),
        ], check=True, capture_output=True, text=True)

    def generate(self, pom="3.0.0-SNAPSHOT", branch="main", feature="", event="workflow_dispatch"):
        output = self.work / "output"
        output.write_text("")
        env = dict(os.environ, TEST_POM_VERSION=pom, GITHUB_REF_NAME=branch,
                   FEATURE_INPUT=feature, GITHUB_EVENT_NAME=event, GITHUB_OUTPUT=str(output))
        env["PATH"] = str(self.work) + os.pathsep + env["PATH"]
        result = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", self.script],
                                env=env, capture_output=True, text=True, timeout=10)
        return result, output.read_text().removeprefix("version=").strip()

    def test_generated_versions(self):
        cases = [
            ("3.0.0-SNAPSHOT", "main", "", "3.0.0-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "OPENNLP-1945", "", "3.0.0-OPENNLP-1945-SNAPSHOT"),
            ("2.5.13-SNAPSHOT", "feature/OPENNLP-123-v1.2", "",
             "2.5.13-feature-OPENNLP-123-v1-2-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "branch", "v1.2", "3.0.0-v1-2-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "branch", " -- feature___one.. ", "3.0.0-feature-one-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "3", "", "3.0.0-3-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "SNAPSHOT", "", "3.0.0-SNAPSHOT-SNAPSHOT"),
            ("3.0.0-OPENNLP-1945-SNAPSHOT", "OPENNLP-1945", "",
             "3.0.0-OPENNLP-1945-SNAPSHOT"),
            ("3.0.0-foobar-SNAPSHOT", "foo", "", "3.0.0-foobar-foo-SNAPSHOT"),
            ("3.0.0-SNAPSHOT", "branch", "$(touch injected)", "3.0.0-touch-injected-SNAPSHOT"),
        ]
        for pom, branch, feature, expected in cases:
            with self.subTest(pom=pom, branch=branch, feature=feature):
                result, version = self.generate(pom, branch, feature)
                self.assertEqual(0, result.returncode, result.stderr)
                # Parse the actual output, not a separately calculated test value.
                parsed = subprocess.run(["java", "-cp", str(self.work), "ParseVersion", version],
                                        capture_output=True, text=True, timeout=10)
                self.assertEqual(0, parsed.returncode, parsed.stderr)
                self.assertEqual(pom.split("-", 1)[0] + "-SNAPSHOT", parsed.stdout.strip())
                self.assertEqual(expected, version)
                if branch != "main":
                    self.assertNotEqual("3.0.0-SNAPSHOT", version)

    def test_rejected_inputs(self):
        for pom, branch, feature in [
            ("3.0.0-SNAPSHOT", "main", "feature"),
            ("3.0.0", "main", ""),
            ("3.0.0", "branch", ""),
            ("3.0.0-SNAPSHOT", "branch", "..."),
            ("3.0.0-SNAPSHOT", "branch", "___"),
            ("3.0.0-SNAPSHOT", "branch", "日本語"),
        ]:
            with self.subTest(pom=pom, branch=branch, feature=feature):
                result, version = self.generate(pom, branch, feature)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", version)

    def test_main_push(self):
        result, version = self.generate(event="push")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("3.0.0-SNAPSHOT", version)

    def test_release_push_skips_deploy(self):
        result, version = self.generate(pom="3.0.0", event="push")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", version)
        self.assertIn("::notice::", result.stdout)


if __name__ == "__main__":
    unittest.main()
