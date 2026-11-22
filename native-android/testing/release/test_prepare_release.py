import json
import tempfile
import unittest
from pathlib import Path

from prepare_release import APK_NAME, ReleaseValidationError, load_manifest, prepare


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.base = self.root / "stable.json"
        self.apk = self.root / "app-release.apk"
        self.apk.write_bytes(b"signed-apk-fixture")
        self.base.write_text(json.dumps({
            "schemaVersion": 1,
            "channel": "stable",
            "versionCode": 20,
            "versionName": "0.1.7",
            "minimumSupportedVersionCode": 1,
            "mandatory": False,
            "publishedAt": "2026-10-11T18:22:49Z",
            "releaseNotes": ["Previous release"],
            "githubRelease": "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.7",
            "apk": {
                "fileName": APK_NAME,
                "downloadUrl": "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/v0.1.7/Watchio-IPTV.apk",
                "sha256": "A" * 64,
            },
        }), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def run_prepare(self, version="0.1.8", code=21):
        return prepare(
            self.base, self.apk, self.root / "out", version, code,
            "1" * 40, ["Release note"], "2026-11-22T00:00:00Z",
        )

    def test_generates_canonical_asset_checksum_and_immutable_urls(self):
        manifest = self.run_prepare()
        self.assertEqual(APK_NAME, manifest["apk"]["fileName"])
        self.assertIn("/releases/download/v0.1.8/Watchio-IPTV.apk", manifest["apk"]["downloadUrl"])
        self.assertEqual(64, len(manifest["apk"]["sha256"]))
        self.assertEqual(manifest, load_manifest(self.root / "out" / "stable.json"))

    def test_rejects_duplicate_version_name(self):
        with self.assertRaises(ReleaseValidationError):
            self.run_prepare(version="0.1.7", code=21)

    def test_rejects_non_increasing_version_code(self):
        with self.assertRaises(ReleaseValidationError):
            self.run_prepare(version="0.1.8", code=20)

    def test_rejects_empty_apk(self):
        self.apk.write_bytes(b"")
        with self.assertRaises(ReleaseValidationError):
            self.run_prepare()

    def test_rejects_untrusted_manifest_url(self):
        value = json.loads(self.base.read_text(encoding="utf-8"))
        value["apk"]["downloadUrl"] = "https://example.invalid/Watchio-IPTV.apk"
        self.base.write_text(json.dumps(value), encoding="utf-8")
        with self.assertRaises(ReleaseValidationError):
            self.run_prepare()


if __name__ == "__main__":
    unittest.main()
