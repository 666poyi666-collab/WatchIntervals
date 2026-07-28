from __future__ import annotations

import json
from typing import Any

from watch_mcp.client import PhoneApiClient, encoded
from watch_mcp.tools import WatchTools


class WatchResources:
    def __init__(self, client: PhoneApiClient, tools: WatchTools) -> None:
        self.client = client
        self.tools = tools

    async def status(self) -> str:
        return self._json(await self.tools.status())

    async def capabilities(self) -> str:
        return self._json(await self.tools.capabilities())

    async def plans(self) -> str:
        return self._json(await self.tools.list_plans())

    async def recent_workouts(self) -> str:
        return self._json(await self.tools.list_workouts(100))

    async def workout(self, workout_id: str) -> str:
        return self._json(await self.client.request("GET", f"/v1/history/{encoded(workout_id)}"))

    async def route(self, workout_id: str, cursor: str) -> str:
        offset = max(0, int(cursor))
        return self._json(
            await self.client.request(
                "GET", f"/v1/history/{encoded(workout_id)}/route?cursor={offset}&limit=500"
            )
        )

    async def heart(self, workout_id: str, cursor: str) -> str:
        offset = max(0, int(cursor))
        return self._json(
            await self.client.request(
                "GET", f"/v1/history/{encoded(workout_id)}/heart?cursor={offset}&limit=500"
            )
        )

    async def sleep(self, days: str) -> str:
        count = max(1, min(int(days), 31))
        return self._json(await self.client.request("GET", f"/v1/sleep?days={count}"))

    @staticmethod
    def _json(value: Any) -> str:
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
