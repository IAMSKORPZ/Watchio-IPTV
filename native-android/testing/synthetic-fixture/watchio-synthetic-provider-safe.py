#!/usr/bin/env python3
"""Local-only credential-safe Xtream fixture for Watchio failover testing."""

import argparse
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


SYNTHETIC_ACCOUNTS = {
    "watchio_test_a": {
        "password": "synthetic_only_a",
        "account_id": "watchio-synthetic-a",
    },
    "watchio_test_b": {
        "password": "synthetic_only_b",
        "account_id": "watchio-synthetic-b",
    },
}

CATALOGUE_ACTIONS = {
    "get_live_categories",
    "get_live_streams",
    "get_vod_categories",
    "get_vod_streams",
    "get_series_categories",
    "get_series",
    "get_series_info",
}


def auth_payload(port: int, username: str, mode: str, accepted: bool = True) -> dict:
    account = SYNTHETIC_ACCOUNTS.get(username)
    status = "Active"
    expiry = "4102444800"
    if mode == "disabled":
        accepted = False
        status = "Disabled"
    elif mode == "expired":
        accepted = False
        status = "Expired"
        expiry = "1"
    return {
        "user_info": {
            "username": username if accepted else "",
            "account_id": account["account_id"] if accepted and account else "",
            "auth": 1 if accepted else 0,
            "status": status if accepted or mode in ("disabled", "expired") else "Disabled",
            "exp_date": expiry,
            "is_trial": "0",
            "active_cons": "0",
            "max_connections": "2",
            "allowed_output_formats": ["ts", "m3u8", "mp4"],
        },
        "server_info": {
            "url": "127.0.0.1",
            "port": str(port),
            "server_protocol": "http",
            "timezone": "UTC",
        },
    }


def safe_route(path: str) -> str:
    if path == "/player_api.php":
        return "api"
    match = re.match(r"^/(live|movie|series)/[^/]+/[^/]+/(101|102|201|401)\.(ts|mp4)$", path)
    if match:
        return f"media:{match.group(1)}:{match.group(2)}"
    if path == "/xmltv.php":
        return "epg"
    return "unknown"


class Handler(BaseHTTPRequestHandler):
    server_version = "WatchioSyntheticProvider/1"

    def log_message(self, fmt, *args):
        print(
            f"t={time.monotonic():.6f} port={self.server.server_port} method={self.command} route={safe_route(urlparse(self.path).path)}",
            flush=True,
        )

    def send_json(self, value, status=200):
        body = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/__watchio_test__/catalogue/status":
            with self.server.catalogue_gate_lock:
                self.send_json({
                    "in_flight": self.server.catalogue_in_flight,
                    "action": self.server.catalogue_in_flight_action,
                    "released": self.server.catalogue_release.is_set(),
                })
            return
        if parsed.path == "/__watchio_test__/catalogue/release":
            self.server.catalogue_release.set()
            self.send_json({"released": True})
            return
        if parsed.path == "/player_api.php":
            query = parse_qs(parsed.query)
            username = query.get("username", [""])[0]
            password = query.get("password", [""])[0]
            account = SYNTHETIC_ACCOUNTS.get(username)
            valid_credentials = account is not None and password == account["password"]
            account_enabled = valid_credentials and self.server.mode not in ("reject", "disabled", "expired")
            if self.server.mode == "http500":
                self.send_json({"error": "temporary"}, 500)
                return
            action = query.get("action", [""])[0]
            self.delay_catalogue_response(action)
            if not action:
                self.send_json(auth_payload(self.server.server_port, username, self.server.mode, account_enabled))
            elif not account_enabled:
                self.send_json({"user_info": {"auth": 0, "status": "Disabled"}}, 401)
            elif action == "get_live_categories":
                self.send_json([{"category_id": "1", "category_name": "Synthetic Live", "parent_id": "0"}])
            elif action == "get_live_streams":
                self.send_json([
                    {"stream_id": "101", "name": "Synthetic Live Fixture", "category_id": "1", "container_extension": "ts"},
                    {"stream_id": "102", "name": "Synthetic Live Recovery Fixture", "category_id": "1", "container_extension": "ts"},
                ])
            elif action == "get_vod_categories":
                self.send_json([{"category_id": "2", "category_name": "Synthetic Movies", "parent_id": "0"}])
            elif action == "get_vod_streams":
                self.send_json([{"stream_id": "201", "name": "Synthetic Movie Fixture", "category_id": "2", "container_extension": "mp4", "genre": "Test"}])
            elif action == "get_vod_info":
                self.send_json({"info": {"name": "Synthetic Movie Fixture", "plot": "Authorised local test video.", "genre": "Test", "duration": "00:10:34", "container_extension": "mp4"}})
            elif action == "get_series_categories":
                self.send_json([{"category_id": "3", "category_name": "Synthetic Series", "parent_id": "0"}])
            elif action == "get_series":
                self.send_json([{"series_id": "301", "name": "Synthetic Series Fixture", "category_id": "3", "plot": "Authorised local test series."}])
            elif action == "get_series_info":
                self.send_json({
                    "info": {"name": "Synthetic Series Fixture", "plot": "Authorised local test series.", "genre": "Test"},
                    "seasons": [{"id": "1", "season_number": 1, "name": "Season 1", "episode_count": 1}],
                    "episodes": {"1": [{"id": "401", "episode_num": 1, "season": 1, "title": "Synthetic Episode Fixture", "container_extension": "mp4", "info": {"plot": "Authorised local test episode.", "duration": "00:10:34"}}]},
                })
            else:
                self.send_json([])
            return

        if re.match(r"^/(live|movie|series)/[^/]+/[^/]+/(101|102|201|401)\.(ts|mp4)$", parsed.path):
            if self.server.stream_status != 200:
                self.send_error(self.server.stream_status)
                return
            if self.server.mode == "drop-stream":
                self.connection.shutdown(2)
                self.connection.close()
                return
            if self.server.mode == "broken-stream":
                self.send_error(500)
                return
            self.send_media()
            return

        self.send_error(404)

    def delay_catalogue_response(self, action: str):
        if not self.server.delay_catalogue or action not in CATALOGUE_ACTIONS:
            return
        with self.server.catalogue_gate_lock:
            if self.server.catalogue_delayed_once:
                return
            self.server.catalogue_delayed_once = True
            self.server.catalogue_in_flight = True
            self.server.catalogue_in_flight_action = action
            self.server.catalogue_started.set()
        released = self.server.catalogue_release.wait(self.server.catalogue_delay_timeout)
        with self.server.catalogue_gate_lock:
            self.server.catalogue_in_flight = False
        if not released:
            raise TimeoutError("Synthetic catalogue gate timed out before release.")

    def send_media(self):
        size = os.path.getsize(self.server.media)
        start, end = 0, size - 1
        range_header = self.headers.get("Range")
        if range_header:
            match = re.match(r"bytes=(\d+)-(\d*)", range_header)
            if match:
                start = int(match.group(1))
                if match.group(2):
                    end = min(int(match.group(2)), end)
        if start >= size:
            self.send_response(416)
            self.send_header("Content-Range", f"bytes */{size}")
            self.end_headers()
            return
        length = end - start + 1
        self.send_response(206 if range_header else 200)
        self.send_header("Content-Type", "video/mp4")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if range_header:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        with open(self.server.media, "rb") as source:
            source.seek(start)
            remaining = length
            while remaining:
                chunk = source.read(min(16 * 1024, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
                remaining -= len(chunk)
                if self.server.throttle_delay > 0:
                    time.sleep(self.server.throttle_delay)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--media", required=True)
    parser.add_argument(
        "--mode",
        choices=("healthy", "http500", "reject", "disabled", "expired", "broken-stream", "drop-stream"),
        default="healthy",
    )
    parser.add_argument("--stream-status", type=int, default=200)
    parser.add_argument("--throttle-delay", type=float, default=0.1)
    parser.add_argument("--delay-catalogue", action="store_true")
    parser.add_argument("--catalogue-delay-timeout", type=float, default=60.0)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.media = os.path.abspath(args.media)
    server.mode = args.mode
    server.stream_status = args.stream_status
    server.throttle_delay = max(0.0, args.throttle_delay)
    server.delay_catalogue = args.delay_catalogue
    server.catalogue_delay_timeout = max(1.0, args.catalogue_delay_timeout)
    server.catalogue_release = threading.Event()
    server.catalogue_started = threading.Event()
    server.catalogue_gate_lock = threading.Lock()
    server.catalogue_delayed_once = False
    server.catalogue_in_flight = False
    server.catalogue_in_flight_action = None
    print(f"t={time.monotonic():.6f} READY port={args.port} mode={args.mode} stream_status={args.stream_status}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
