from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    host: str
    port: int
    data_dir: Path
    phone_token: str
    phone_device_id: str
    discovery_service: str = "_watchintervals-phone._tcp.local."
    connect_timeout: float = 3.0
    read_timeout: float = 25.0

    @classmethod
    def from_environment(cls) -> Settings:
        data_dir = Path(
            os.environ.get(
                "WATCH_MCP_DATA_DIR",
                str(Path(os.environ.get("PROGRAMDATA", ".")) / "Poyi" / "WatchMcp"),
            )
        )
        token = os.environ.get("WATCH_MCP_PHONE_TOKEN", "").strip()
        return cls(
            host=os.environ.get("WATCH_MCP_HOST", "127.0.0.1"),
            port=int(os.environ.get("WATCH_MCP_PORT", "8768")),
            data_dir=data_dir,
            phone_token=token,
            phone_device_id=os.environ.get("WATCH_MCP_PHONE_DEVICE_ID", "").strip(),
        )

    def validate(self) -> None:
        if self.host not in {"127.0.0.1", "localhost", "::1"}:
            raise ValueError("WATCH_MCP_HOST must be loopback")
        if not 1 <= self.port <= 65535:
            raise ValueError("WATCH_MCP_PORT is invalid")
        if len(self.phone_token) < 32:
            raise ValueError("WATCH_MCP_PHONE_TOKEN is missing or too short")
