from __future__ import annotations

import argparse
import logging

from watch_mcp.server import build_server
from watch_mcp.settings import Settings


def main() -> None:
    parser = argparse.ArgumentParser(prog="poyi-watch-mcp")
    parser.add_argument("command", choices=["serve", "stdio", "doctor"], nargs="?", default="serve")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    settings = Settings.from_environment()
    settings.validate()
    if args.command == "doctor":
        print(f"PoyiWatchMcp ready on http://{settings.host}:{settings.port}/mcp")
        return
    server = build_server(settings)
    server.run(transport="stdio" if args.command == "stdio" else "streamable-http")


if __name__ == "__main__":
    main()
