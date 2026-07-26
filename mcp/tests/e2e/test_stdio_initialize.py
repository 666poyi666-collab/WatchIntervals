from pathlib import Path

from watch_mcp.server import build_server
from watch_mcp.settings import Settings


def test_server_can_be_constructed_for_inspector(tmp_path: Path) -> None:
    server = build_server(Settings("127.0.0.1", 8768, tmp_path, "x" * 43, ""))
    assert server.name == "WatchIntervals"
