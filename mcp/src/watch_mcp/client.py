from __future__ import annotations

import asyncio
import json
import time
from dataclasses import dataclass
from typing import Any, cast
from urllib.parse import quote

import httpx
from zeroconf import ServiceBrowser, ServiceListener, Zeroconf

from watch_mcp.audit import event
from watch_mcp.errors import WatchMcpError
from watch_mcp.health import Metrics
from watch_mcp.settings import Settings


@dataclass(frozen=True, slots=True)
class Endpoint:
    base_url: str
    device_id: str


class _Listener(ServiceListener):
    def __init__(self) -> None:
        self.addresses: list[tuple[str, int]] = []

    def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        info = zc.get_service_info(type_, name, timeout=1200)
        if info and info.port:
            self.addresses.extend((address, info.port) for address in info.parsed_addresses())

    def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        self.add_service(zc, type_, name)

    def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
        return None


class PhoneApiClient:
    def __init__(self, settings: Settings, metrics: Metrics) -> None:
        self.settings = settings
        self.metrics = metrics
        self._runtime_url = ""
        self._client = self._new_client()

    def _new_client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            timeout=httpx.Timeout(
                connect=self.settings.connect_timeout,
                read=self.settings.read_timeout,
                write=self.settings.read_timeout,
                pool=self.settings.connect_timeout,
            ),
            headers={
                "Authorization": f"Bearer {self.settings.phone_token}",
                "Content-Type": "application/json",
                "User-Agent": "PoyiWatchMcp/0.20",
            },
        )

    def _ensure_client(self) -> httpx.AsyncClient:
        if self._client.is_closed:
            self._client = self._new_client()
        return self._client

    async def close(self) -> None:
        await self._client.aclose()

    async def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> object:
        last_error: WatchMcpError | None = None
        for base_url in await self._candidate_urls():
            try:
                response = await self._ensure_client().request(
                    method, base_url.rstrip("/") + path, json=body
                )
                result = self._decode(response)
                if path == "/v1/status":
                    self._verify_and_pin(base_url, result)
                self._runtime_url = base_url
                return result
            except WatchMcpError as exc:
                last_error = exc
                if not exc.retryable:
                    raise
            except httpx.TimeoutException:
                last_error = WatchMcpError("PHONE_TIMEOUT", "Phone request timed out", True)
            except httpx.NetworkError:
                last_error = WatchMcpError("PHONE_OFFLINE", "Phone is offline", True)
        raise last_error or WatchMcpError("PHONE_OFFLINE", "Phone was not discovered", True)

    async def verify(self) -> dict[str, Any]:
        result = self._object(await self.request("GET", "/v1/status"))
        self._verify_and_pin(self._runtime_url, result)
        return result

    async def _candidate_urls(self) -> list[str]:
        candidates: list[str] = []
        if self._runtime_url:
            candidates.append(self._runtime_url)
        cached = self._load_endpoint()
        if cached:
            candidates.append(cached.base_url)
        self.metrics.discovery()
        candidates.extend(await asyncio.to_thread(self._discover))
        return list(dict.fromkeys(item for item in candidates if item))

    def _discover(self) -> list[str]:
        listener = _Listener()
        zeroconf = Zeroconf()
        browser = ServiceBrowser(zeroconf, self.settings.discovery_service, listener)
        try:
            time.sleep(1.8)
            return [f"http://{host}:{port}" for host, port in listener.addresses]
        finally:
            browser.cancel()
            zeroconf.close()

    def _verify_and_pin(self, base_url: str, value: Any) -> None:
        status = self._object(value)
        actual = str(status.get("phoneDeviceId", ""))
        if not actual:
            raise WatchMcpError("PHONE_PROTOCOL_ERROR", "Phone status has no stable device ID")
        expected = (
            self.settings.phone_device_id or (self._load_endpoint() or Endpoint("", "")).device_id
        )
        if expected and actual != expected:
            raise WatchMcpError("PHONE_AUTH_FAILED", "Discovered phone identity does not match")
        self._save_endpoint(Endpoint(base_url, actual))

    def _load_endpoint(self) -> Endpoint | None:
        path = self.settings.data_dir / "phone-endpoint.json"
        try:
            value = json.loads(path.read_text("utf-8"))
            return Endpoint(str(value["baseUrl"]), str(value["phoneDeviceId"]))
        except (OSError, ValueError, KeyError, TypeError):
            return None

    def _save_endpoint(self, endpoint: Endpoint) -> None:
        self.settings.data_dir.mkdir(parents=True, exist_ok=True)
        path = self.settings.data_dir / "phone-endpoint.json"
        temp = path.with_suffix(".tmp")
        temp.write_text(
            json.dumps(
                {"baseUrl": endpoint.base_url, "phoneDeviceId": endpoint.device_id},
                separators=(",", ":"),
            ),
            "utf-8",
        )
        temp.replace(path)
        event("phone_endpoint_verified", deviceId=endpoint.device_id)

    @staticmethod
    def _decode(response: httpx.Response) -> object:
        try:
            raw = cast(object, response.json())
        except ValueError as exc:
            raise WatchMcpError(
                "PHONE_PROTOCOL_ERROR", "Phone returned invalid JSON", True
            ) from exc
        details = cast(dict[str, Any], raw) if isinstance(raw, dict) else {}
        error = str(details.get("error", ""))
        if response.status_code == 401:
            raise WatchMcpError("PHONE_AUTH_FAILED", "Phone rejected the API token")
        if response.status_code == 404:
            raise WatchMcpError("NOT_FOUND", "WatchIntervals object was not found")
        if response.status_code == 409:
            code = error.upper() or "CONFLICT"
            raise WatchMcpError(code, "Write conflicted with newer data", details=details)
        if response.status_code == 422:
            raise WatchMcpError(
                "INVALID_ARGUMENT", error or "Phone rejected the request", details=details
            )
        if response.status_code == 429:
            raise WatchMcpError("RATE_LIMITED", "Phone rate limit exceeded", True)
        if response.status_code == 503:
            code = "WATCH_OFFLINE" if error == "watch_offline" else "PHONE_DEGRADED"
            raise WatchMcpError(
                code, "Watch is offline" if code == "WATCH_OFFLINE" else "Phone API degraded", True
            )
        if response.status_code >= 500:
            raise WatchMcpError("PHONE_OFFLINE", "Phone API failed", True)
        if response.status_code >= 400:
            raise WatchMcpError("INVALID_ARGUMENT", "Phone rejected the request", details=details)
        return cast(object, raw)

    @staticmethod
    def _object(value: object) -> dict[str, Any]:
        if not isinstance(value, dict):
            raise WatchMcpError("PHONE_PROTOCOL_ERROR", "Expected a JSON object", True)
        return cast(dict[str, Any], value)


def encoded(value: str) -> str:
    return quote(value, safe="")
