from __future__ import annotations

import hashlib
import json
from typing import Any

from watch_mcp.errors import WatchMcpError


def payload_hash(value: dict[str, Any]) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def validate_replay(original_hash: str, value: dict[str, Any]) -> None:
    if original_hash and original_hash != payload_hash(value):
        raise WatchMcpError(
            "REQUEST_ID_REUSED",
            "The requestId was already used with a different payload",
        )
