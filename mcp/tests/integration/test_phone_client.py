# pyright: reportPrivateUsage=false, reportUnknownMemberType=false
from pathlib import Path
from typing import cast

import httpx
import pytest
import respx

from watch_mcp.client import PhoneApiClient
from watch_mcp.errors import WatchMcpError
from watch_mcp.health import Metrics
from watch_mcp.settings import Settings


def settings(tmp_path: Path) -> Settings:
    return Settings("127.0.0.1", 8768, tmp_path, "x" * 43, "phone-one")


@pytest.mark.asyncio
@respx.mock
async def test_bearer_auth_and_stable_identity_are_verified(tmp_path: Path) -> None:
    client = PhoneApiClient(settings(tmp_path), Metrics())
    client._runtime_url = "http://phone.test"
    route = respx.get("http://phone.test/v1/status").mock(
        return_value=httpx.Response(200, json={"phoneDeviceId": "phone-one"})
    )
    try:
        result = await client.verify()
    finally:
        await client.close()
    assert result["phoneDeviceId"] == "phone-one"
    request = cast(httpx.Request, route.calls[0].request)
    assert request.headers["Authorization"] == "Bearer " + "x" * 43
    assert (tmp_path / "phone-endpoint.json").exists()


@pytest.mark.asyncio
@respx.mock
async def test_revision_conflict_is_structured(tmp_path: Path) -> None:
    client = PhoneApiClient(settings(tmp_path), Metrics())
    client._runtime_url = "http://phone.test"
    respx.put("http://phone.test/v1/plan-selection").mock(
        return_value=httpx.Response(
            409,
            json={"error": "revision_conflict", "expectedRevision": 1, "actualRevision": 2},
        )
    )
    try:
        with pytest.raises(WatchMcpError) as raised:
            await client.request("PUT", "/v1/plan-selection", {})
    finally:
        await client.close()
    assert raised.value.code == "REVISION_CONFLICT"
    assert raised.value.details == {
        "error": "revision_conflict",
        "expectedRevision": 1,
        "actualRevision": 2,
    }


@pytest.mark.asyncio
@respx.mock
async def test_client_reopens_after_stateless_http_lifespan_close(tmp_path: Path) -> None:
    client = PhoneApiClient(settings(tmp_path), Metrics())
    client._runtime_url = "http://phone.test"
    respx.get("http://phone.test/v1/status").mock(
        return_value=httpx.Response(200, json={"phoneDeviceId": "phone-one"})
    )
    await client.close()
    try:
        result = await client.verify()
    finally:
        await client.close()
    assert result["phoneDeviceId"] == "phone-one"
