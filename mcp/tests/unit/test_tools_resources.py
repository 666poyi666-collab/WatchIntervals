import time
import uuid
from pathlib import Path
from typing import Any

import pytest

from watch_mcp.errors import WatchMcpError
from watch_mcp.health import Metrics
from watch_mcp.idempotency import payload_hash, validate_replay
from watch_mcp.resources import WatchResources
from watch_mcp.settings import Settings
from watch_mcp.tools import WatchTools


class FakeClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, dict[str, Any] | None]] = []

    async def verify(self) -> dict[str, Any]:
        return {"phoneDeviceId": "phone", "libraryRevision": 3}

    async def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> object:
        self.calls.append((method, path, body))
        if path == "/v1/health":
            return {"state": "healthy", "watch": "online"}
        if path == "/v1/capabilities":
            return {"apiVersion": 1}
        if path == "/v1/plans":
            return {"plans": [], "revision": 3}
        if path.startswith("/v1/plans/"):
            return {"plan": {"id": "one"}, "revision": 3}
        if path == "/v1/plan/profile":
            return {"id": "one"}
        if path == "/v1/plan-groups":
            return {"groups": [], "revision": 3}
        if path == "/v1/history":
            return [
                {
                    "id": "one",
                    "distanceMeters": 1000,
                    "activeDurationMs": 600000,
                    "steps": 1200,
                    "averageHeartRate": 100,
                },
                {"id": "two", "distanceMeters": 500, "durationMs": 300000, "steps": 600},
            ]
        if path == "/v1/history/one":
            return {"id": "one", "route": [1], "heartRates": [2]}
        if path.startswith("/v1/history/one/route"):
            return {"items": [{"latitude": 1}], "nextCursor": None}
        if path.startswith("/v1/history/one/heart"):
            return {"items": [{"bpm": 70}], "nextCursor": None}
        if path.startswith("/v1/sleep"):
            return {
                "state": "ready",
                "source": "system",
                "records": [
                    {
                        "timestamp": 2,
                        "totalDurationMinutes": 420,
                        "sleepScore": 80,
                        "spo2AveragePercent": 96,
                        "sessions": [1],
                    }
                ],
            }
        if path == "/v1/sync/status":
            return {"state": "idle"}
        return {"saved": True, "request": body}


@pytest.mark.asyncio
async def test_read_write_control_and_resources() -> None:
    client = FakeClient()
    tools = WatchTools(client, Metrics())  # type: ignore[arg-type]
    resources = WatchResources(client, tools)  # type: ignore[arg-type]
    request_id, command_id = str(uuid.uuid4()), str(uuid.uuid4())

    assert (await tools.status())["connection"]["watch"] == "online"
    assert (await tools.capabilities())["apiVersion"] == 1
    assert (await tools.current_plan())["id"] == "one"
    assert (await tools.list_plans())["revision"] == 3
    assert (await tools.get_plan("one"))["plan"]["id"] == "one"
    assert (await tools.list_groups())["revision"] == 3
    assert (await tools.list_workouts(1))["count"] == 1
    assert (await tools.summarize_workouts())["totalDistanceMeters"] == 1500
    workout = await tools.get_workout("one")
    assert "route" not in workout and workout["routeResource"].endswith("/route/0")
    assert (await tools.latest_sleep())["record"]["timestamp"] == 2
    assert (await tools.summarize_sleep(7))["averageSleepScore"] == 80
    assert (await tools.sync_status())["state"] == "idle"

    await tools.set_plan(request_id, 3, {"name": "new"})
    await tools.set_plan(request_id, 3, {"id": "one", "name": "changed"})
    await tools.delete_plan(request_id, 3, "one")
    await tools.select_plan(request_id, 3, "one")
    await tools.create_group(request_id, 3, "group")
    await tools.rename_group(request_id, 3, "group", "renamed")
    await tools.delete_group(request_id, 3, "group")
    await tools.delete_workout(request_id, 0, "one")
    await tools.sync(request_id, 3)
    await tools.control(
        "pause", request_id, 0, command_id, "RUNNING", int(time.time() * 1000) + 60_000
    )
    assert any(path == "/v1/control/pause" for _, path, _ in client.calls)

    assert "phoneDeviceId" in await resources.status()
    assert "apiVersion" in await resources.capabilities()
    assert "plans" in await resources.plans()
    assert "one" in await resources.recent_workouts()
    assert "latitude" in await resources.route("one", "0")
    assert "bpm" in await resources.heart("one", "0")
    assert "sessions" in await resources.sleep("7")


def test_support_types_and_settings(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    value = {"requestId": str(uuid.uuid4()), "expectedRevision": 1}
    digest = payload_hash(value)
    validate_replay(digest, value)
    with pytest.raises(WatchMcpError):
        validate_replay(digest, {**value, "expectedRevision": 2})
    error = WatchMcpError("CODE", "message", details={"a": 1})
    assert error.as_dict()["details"] == {"a": 1}
    metrics = Metrics()
    metrics.call()
    metrics.call(failed=True)
    metrics.discovery()
    assert "watch_mcp_tool_calls_total 2" in metrics.render(True)

    monkeypatch.setenv("WATCH_MCP_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("WATCH_MCP_PHONE_TOKEN", "x" * 43)
    settings = Settings.from_environment()
    settings.validate()
    assert settings.data_dir == tmp_path
    with pytest.raises(ValueError):
        Settings("0.0.0.0", 8768, tmp_path, "x" * 43, "").validate()
