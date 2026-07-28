from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(slots=True)
class WatchMcpError(RuntimeError):
    code: str
    message: str
    retryable: bool = False
    details: dict[str, Any] | None = None

    def __post_init__(self) -> None:
        RuntimeError.__init__(self, f"{self.code}: {self.message}")

    def as_dict(self) -> dict[str, Any]:
        value: dict[str, Any] = {
            "code": self.code,
            "message": self.message,
            "retryable": self.retryable,
        }
        if self.details:
            value["details"] = self.details
        return value
