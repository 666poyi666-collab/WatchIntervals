from pathlib import Path

import httpx
import pytest

from watch_mcp.server import build_server
from watch_mcp.settings import Settings


def server(tmp_path: Path):
    return build_server(Settings("127.0.0.1", 8768, tmp_path, "x" * 43, ""))


@pytest.mark.asyncio
async def test_all_tools_are_namespaced_and_resources_cover_large_data(tmp_path: Path) -> None:
    value = server(tmp_path)
    names = {tool.name for tool in await value.list_tools()}
    assert len(names) == 24
    assert all(name.startswith("watch_") for name in names)
    assert {
        "watch_get_status",
        "watch_set_plan",
        "watch_pause_workout",
        "watch_delete_workout",
        "watch_summarize_workouts",
        "watch_sync_plans",
    } <= names
    tools = {tool.name: tool for tool in await value.list_tools()}
    sync_props = tools["watch_sync_plans"].inputSchema["properties"]
    assert {"request_id", "expected_revision", "requestId", "expectedRevision"} <= set(sync_props)
    pause_props = tools["watch_pause_workout"].inputSchema["properties"]
    assert {
        "command_id",
        "expected_state",
        "expires_at",
        "commandId",
        "expectedState",
        "expiresAt",
    } <= set(pause_props)
    templates = {str(item.uriTemplate) for item in await value.list_resource_templates()}
    assert "watch://workouts/{workout_id}/route/{cursor}" in templates
    assert "watch://workouts/{workout_id}/heart/{cursor}" in templates
    assert "watch://sleep/{days}" in templates


@pytest.mark.asyncio
async def test_health_ready_and_metrics_share_watch_port_app(tmp_path: Path) -> None:
    app = server(tmp_path).streamable_http_app()
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://watch.local") as client:
        health = await client.get("/healthz")
        ready = await client.get("/readyz")
        metrics = await client.get("/metrics")
        protected_resource = await client.get("/.well-known/oauth-protected-resource/mcp")
        protected_resource_fallback = await client.get("/.well-known/oauth-protected-resource")
    assert health.status_code == 200
    assert health.json()["service"] == "PoyiWatchMcp"
    assert ready.status_code == 200
    assert "watch_mcp_ready 1" in metrics.text
    assert protected_resource.status_code == 200
    assert protected_resource.json() == {
        "resource": "http://127.0.0.1:8768/mcp",
        "authorization_servers": [],
    }
    assert protected_resource_fallback.json() == protected_resource.json()
