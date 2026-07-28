from __future__ import annotations

import json
import logging
from typing import Any, cast

LOGGER = logging.getLogger("watch_mcp.audit")
SENSITIVE_KEYS = {"token", "authorization", "pairingCode", "latitude", "longitude", "route"}


def _redact(value: Any) -> Any:
    if isinstance(value, dict):
        items = cast(dict[str, object], value)
        return {
            key: "[REDACTED]" if key in SENSITIVE_KEYS else _redact(item)
            for key, item in items.items()
        }
    if isinstance(value, list):
        items = cast(list[object], value)
        return f"[{len(items)} items]"
    return value


def event(name: str, **fields: Any) -> None:
    LOGGER.info(json.dumps({"event": name, **_redact(fields)}, separators=(",", ":")))
