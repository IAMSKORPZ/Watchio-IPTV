#!/usr/bin/env python3
"""Fail-closed host preflight. Fixture servers must already be running."""

import json
import sys
import urllib.parse
import urllib.request


ACCOUNTS = (
    ("watchio_test_a", "synthetic_only_a", "watchio-synthetic-a"),
    ("watchio_test_b", "synthetic_only_b", "watchio-synthetic-b"),
)
PORTS = (18081, 18082)


def authenticate(port, username, password):
    query = urllib.parse.urlencode({"username": username, "password": password})
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/player_api.php?{query}", timeout=2) as response:
        if response.status != 200:
            raise RuntimeError(f"port {port}: HTTP {response.status}")
        return json.loads(response.read())


def main():
    seen = set()
    for port in PORTS:
        for username, password, account_id in ACCOUNTS:
            payload = authenticate(port, username, password)
            info = payload.get("user_info", {})
            if info.get("auth") != 1 or info.get("status") != "Active":
                raise RuntimeError(f"port {port}: {username} rejected")
            if info.get("account_id") != account_id:
                raise RuntimeError(f"port {port}: {username} identity mismatch")
            seen.add(username)
    if len(seen) != 2:
        raise RuntimeError("accounts are not distinct")
    print("PASS: two distinct active synthetic accounts authenticate on 127.0.0.1:18081 and 127.0.0.1:18082")


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        raise SystemExit(1)
