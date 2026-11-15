#!/usr/bin/env python3
"""Host-only tests for the loopback Watchio synthetic Xtream fixture."""

import importlib.util
import json
import pathlib
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request


FIXTURE_PATH = pathlib.Path(__file__).with_name("watchio-synthetic-provider-safe.py")
SPEC = importlib.util.spec_from_file_location("watchio_synthetic_provider_safe", FIXTURE_PATH)
FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURE)


class RunningFixture:
    def __init__(self, port, mode="healthy"):
        self.server = FIXTURE.ThreadingHTTPServer(("127.0.0.1", port), FIXTURE.Handler)
        self.server.media = str(FIXTURE_PATH)
        self.server.mode = mode
        self.server.stream_status = 200
        self.server.throttle_delay = 0.0
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)


def request(port, username, password, action=None):
    query = {"username": username, "password": password}
    if action:
        query["action"] = action
    url = f"http://127.0.0.1:{port}/player_api.php?{urllib.parse.urlencode(query)}"
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as failure:
        try:
            return failure.code, json.loads(failure.read())
        finally:
            failure.close()


class SyntheticFixtureTest(unittest.TestCase):
    def test_two_accounts_authenticate_deterministically_on_both_endpoints(self):
        with RunningFixture(18081), RunningFixture(18082):
            first = {}
            for port in (18081, 18082):
                for username, password, account_id in (
                    ("watchio_test_a", "synthetic_only_a", "watchio-synthetic-a"),
                    ("watchio_test_b", "synthetic_only_b", "watchio-synthetic-b"),
                ):
                    status, payload = request(port, username, password)
                    self.assertEqual(200, status)
                    self.assertEqual(1, payload["user_info"]["auth"])
                    self.assertEqual("Active", payload["user_info"]["status"])
                    self.assertEqual(account_id, payload["user_info"]["account_id"])
                    key = (port, username)
                    first[key] = payload
                    self.assertEqual(first[key], request(port, username, password)[1])

    def test_invalid_username_and_password_are_rejected(self):
        with RunningFixture(18081):
            for username, password in (
                ("unknown", "synthetic_only_a"),
                ("watchio_test_a", "wrong"),
                ("watchio_test_b", "synthetic_only_a"),
            ):
                status, payload = request(18081, username, password)
                self.assertEqual(200, status)
                self.assertEqual(0, payload["user_info"]["auth"])

    def test_catalogue_response_is_stable_for_each_independent_account(self):
        expected = [
            {
                "stream_id": "201",
                "name": "Synthetic Movie Fixture",
                "category_id": "2",
                "container_extension": "mp4",
                "genre": "Test",
            },
        ]
        with RunningFixture(18081):
            for username, password in (
                ("watchio_test_a", "synthetic_only_a"),
                ("watchio_test_b", "synthetic_only_b"),
            ):
                status, payload = request(18081, username, password, "get_vod_streams")
                self.assertEqual(200, status)
                self.assertEqual(expected, payload)

    def test_disabled_and_expired_modes_are_rejected(self):
        for mode, expected_status in (("disabled", "Disabled"), ("expired", "Expired")):
            with RunningFixture(0, mode) as fixture:
                port = fixture.server.server_port
                status, payload = request(port, "watchio_test_a", "synthetic_only_a")
                self.assertEqual(200, status)
                self.assertEqual(0, payload["user_info"]["auth"])
                self.assertEqual(expected_status, payload["user_info"]["status"])

    def test_primary_failure_allows_secondary_success(self):
        with RunningFixture(18081, "http500"), RunningFixture(18082):
            self.assertEqual(500, request(18081, "watchio_test_a", "synthetic_only_a")[0])
            self.assertEqual(1, request(18082, "watchio_test_a", "synthetic_only_a")[1]["user_info"]["auth"])

    def test_unknown_route_fails_safely_and_bind_is_loopback(self):
        with RunningFixture(0) as fixture:
            self.assertEqual("127.0.0.1", fixture.server.server_address[0])
            with self.assertRaises(urllib.error.HTTPError) as failure:
                urllib.request.urlopen(f"http://127.0.0.1:{fixture.server.server_port}/unknown", timeout=2)
            self.assertEqual(404, failure.exception.code)
            failure.exception.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
