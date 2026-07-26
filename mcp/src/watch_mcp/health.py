from __future__ import annotations

import threading
from dataclasses import dataclass, field


@dataclass(slots=True)
class Metrics:
    calls_total: int = 0
    calls_failed: int = 0
    discovery_total: int = 0
    _lock: threading.Lock = field(default_factory=threading.Lock, init=False, repr=False)

    def call(self, failed: bool = False) -> None:
        with self._lock:
            self.calls_total += 1
            if failed:
                self.calls_failed += 1

    def discovery(self) -> None:
        with self._lock:
            self.discovery_total += 1

    def render(self, ready: bool) -> str:
        return (
            "# TYPE watch_mcp_tool_calls_total counter\n"
            f"watch_mcp_tool_calls_total {self.calls_total}\n"
            "# TYPE watch_mcp_tool_failures_total counter\n"
            f"watch_mcp_tool_failures_total {self.calls_failed}\n"
            "# TYPE watch_mcp_discovery_total counter\n"
            f"watch_mcp_discovery_total {self.discovery_total}\n"
            "# TYPE watch_mcp_ready gauge\n"
            f"watch_mcp_ready {1 if ready else 0}\n"
        )
