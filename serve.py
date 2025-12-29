from __future__ import annotations

import argparse
import http.server
import os
import socket
import socketserver
import threading
import webbrowser
from pathlib import Path
from typing import Optional


class _Server(socketserver.TCPServer):
    allow_reuse_address = True


def _find_open_port(host: str = "127.0.0.1", preferred: int = 8000, max_port: int = 9000) -> int:
    port = preferred
    while port <= max_port:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            if sock.connect_ex((host, port)) != 0:
                return port
        port += 1
    raise RuntimeError(f"No open port found between {preferred} and {max_port}.")


def _open_browser(url: str) -> None:
    try:
        webbrowser.open(url)
    except Exception as exc:  # noqa: BLE001
        print(f"Could not open a browser automatically: {exc}")


def serve(
    directory: Optional[Path] = None,
    preferred_port: int = 8000,
    host: str = "127.0.0.1",
    open_browser: bool = True,
    duration: Optional[int] = None,
) -> None:
    root = (directory or Path(__file__).resolve().parent).absolute()
    os.chdir(root)

    port = _find_open_port(host, preferred_port)
    handler = http.server.SimpleHTTPRequestHandler
    handler.extensions_map.update({
        ".css": "text/css",
        ".js": "application/javascript",
        ".html": "text/html",
    })

    url = f"http://{host}:{port}/index.html"
    print(f"Serving {root} at {url}\nPress Ctrl+C to stop.")

    if open_browser:
        _open_browser(url)

    with _Server((host, port), handler) as httpd:
        if duration:
            threading.Timer(duration, httpd.shutdown).start()
        try:
            httpd.serve_forever(poll_interval=0.5)
        except KeyboardInterrupt:
            print("\nServer stopped.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Serve the AirLiftOne static site.")
    parser.add_argument(
        "--directory",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Directory to serve (defaults to the project root).",
    )
    parser.add_argument("--port", type=int, default=8000, help="Preferred port (defaults to 8000).")
    parser.add_argument("--host", type=str, default="127.0.0.1", help="Host to bind (defaults to 127.0.0.1).")
    parser.add_argument(
        "--no-browser",
        action="store_true",
        help="Do not attempt to open the browser automatically.",
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=None,
        help="Optional number of seconds to serve before shutting down automatically.",
    )

    args = parser.parse_args()
    serve(
        directory=args.directory,
        preferred_port=args.port,
        host=args.host,
        open_browser=not args.no_browser,
        duration=args.duration,
    )
