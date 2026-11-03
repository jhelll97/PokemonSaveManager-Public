"""Guard automatic validation against accidental APK production (stdlib only)."""
from pathlib import Path
import re
import unittest

def validate(text):
    # The only packaging/upload job must be the explicitly dispatched release.
    before, separator, rest = text.partition("\n  signed-release:\n")
    assert separator, "Missing explicitly gated signed-release job"
    signed, separator, after = rest.partition("\n  slack-result:\n")
    assert separator, "Missing single Slack reporter"
    assert "github.event_name == 'workflow_dispatch' && inputs.build_signed_release" in signed
    automatic = before + after
    for forbidden in ("assemble", "bundleDebug", "bundleRelease", "packageDebug",
                      "packageRelease", "upload-artifact", "apksigner", "PSE_KEYSTORE"):
        assert forbidden.lower() not in automatic.lower(), forbidden
    commands = re.findall(r"^        run: gradle (.+)$", before, re.M)
    assert len(commands) == 1, "Expected one automatic Gradle invocation"
    tasks = [word for word in commands[0].split() if word.startswith(":")]
    assert tasks == [":app:compileDebugSources", ":app:lintDebug"], tasks
    assert "python3 tools/tests/test_public_workflow_policy.py" in before
    assert "needs: [debug, signed-release]" in after
    assert "github.event_name == 'push'" in after
    assert "github.ref_name == 'pse-phone-auto-v2-test'" in after
    assert text.count("https://slack.com/api/chat.postMessage") == 1
    assert "DEBUG_RESULT: ${{ needs.debug.result }}" in after

class PolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[2] / ".github/workflows/android-build.yml").read_text()

    def test_current_workflow(self):
        validate(self.text)

    def test_packaging_task_is_rejected(self):
        with self.assertRaises(AssertionError):
            validate(self.text.replace(":app:compileDebugSources", ":app:assembleDebug"))

    def test_upload_in_automatic_job_is_rejected(self):
        with self.assertRaises(AssertionError):
            validate(self.text.replace("  signed-release:", "      - uses: actions/upload-artifact@v4\n\n  signed-release:"))

    def test_ungated_signing_is_rejected(self):
        with self.assertRaises(AssertionError):
            validate(self.text.replace("github.event_name == 'workflow_dispatch' && inputs.build_signed_release", "always()"))

    def test_duplicate_reporter_is_rejected(self):
        with self.assertRaises(AssertionError):
            validate(self.text + "\n# https://slack.com/api/chat.postMessage\n")

if __name__ == "__main__":
    unittest.main()
