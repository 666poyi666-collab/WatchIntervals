from __future__ import annotations

import time
import uuid
from typing import Any

from watch_mcp.errors import WatchMcpError


def require_uuid(value: str, field: str) -> str:
    try:
        uuid.UUID(value)
    except (ValueError, AttributeError) as exc:
        raise WatchMcpError("INVALID_ARGUMENT", f"{field} must be a UUID") from exc
    return value


def write_metadata(request_id: str, expected_revision: int) -> dict[str, Any]:
    require_uuid(request_id, "requestId")
    if isinstance(expected_revision, bool) or expected_revision < 0:
        raise WatchMcpError("INVALID_ARGUMENT", "expectedRevision must be non-negative")
    return {"requestId": request_id, "expectedRevision": expected_revision}


def control_metadata(
    request_id: str,
    expected_revision: int,
    command_id: str,
    expected_state: str,
    expires_at: int,
) -> dict[str, Any]:
    value = write_metadata(request_id, expected_revision)
    value["commandId"] = require_uuid(command_id, "commandId")
    if not expected_state.strip():
        raise WatchMcpError("INVALID_ARGUMENT", "expectedState is required")
    if expires_at < int(time.time() * 1000):
        raise WatchMcpError("COMMAND_EXPIRED", "Control command has expired")
    value.update({"expectedState": expected_state, "expiresAt": expires_at})
    return value
