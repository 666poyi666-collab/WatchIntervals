import time
import uuid

import pytest

from watch_mcp.errors import WatchMcpError
from watch_mcp.schemas import control_metadata, write_metadata


def test_write_metadata_requires_uuid_and_revision() -> None:
    request_id = str(uuid.uuid4())
    assert write_metadata(request_id, 4) == {
        "requestId": request_id,
        "expectedRevision": 4,
    }
    with pytest.raises(WatchMcpError, match="requestId"):
        write_metadata("not-a-uuid", 4)
    with pytest.raises(WatchMcpError, match="expectedRevision"):
        write_metadata(request_id, -1)


def test_expired_control_never_reaches_business_api() -> None:
    with pytest.raises(WatchMcpError) as raised:
        control_metadata(
            str(uuid.uuid4()),
            0,
            str(uuid.uuid4()),
            "RUNNING",
            int(time.time() * 1000) - 1,
        )
    assert raised.value.code == "COMMAND_EXPIRED"
