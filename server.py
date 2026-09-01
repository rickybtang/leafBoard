#!/usr/bin/env python3
"""Minimal USB dashboard server for a BOOX Leaf2."""

from __future__ import annotations

import json
import re
import subprocess
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


HOST = "127.0.0.1"
PORT = 8787
ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "web" / "index.html"
WEEKDAYS = "一二三四五六日"


def read_battery() -> dict[str, int | bool | None]:
    try:
        result = subprocess.run(
            ["adb", "shell", "dumpsys", "battery"],
            capture_output=True,
            text=True,
            timeout=3,
            check=True,
        )
    except (OSError, subprocess.SubprocessError):
        return {"connected": False, "level": None}

    match = re.search(r"^\s*level:\s*(\d+)\s*$", result.stdout, re.MULTILINE)
    return {
        "connected": True,
        "level": int(match.group(1)) if match else None,
    }


def dashboard_data() -> dict[str, object]:
    now = datetime.now().astimezone()
    return {
        "time": now.strftime("%H:%M"),
        "date": now.strftime("%Y年%m月%d日"),
        "weekday": f"星期{WEEKDAYS[now.weekday()]}",
        "updatedAt": now.strftime("%H:%M:%S"),
        "device": read_battery(),
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        path = urlsplit(self.path).path

        if path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(INDEX.read_bytes())
            return

        if path == "/api/dashboard":
            payload = json.dumps(dashboard_data(), ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(payload)
            return

        self.send_error(404)

    def log_message(self, format: str, *args: object) -> None:
        print(f"[{self.log_date_time_string()}] {format % args}")


if __name__ == "__main__":
    print(f"Leaf2 dashboard: http://{HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
