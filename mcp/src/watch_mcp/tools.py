from __future__ import annotations

import json
from typing import Any, cast

from watch_mcp.client import PhoneApiClient, encoded
from watch_mcp.errors import WatchMcpError
from watch_mcp.health import Metrics
from watch_mcp.schemas import control_metadata, write_metadata


class WatchTools:
    def __init__(self, client: PhoneApiClient, metrics: Metrics) -> None:
        self.client = client
        self.metrics = metrics

    async def invoke(self, operation: Any) -> Any:
        try:
            result = await operation
            self.metrics.call()
            return result
        except WatchMcpError as exc:
            self.metrics.call(failed=True)
            raise RuntimeError(json.dumps(exc.as_dict(), separators=(",", ":"))) from exc

    async def status(self) -> dict[str, Any]:
        phone = await self.client.verify()
        health = await self._object("GET", "/v1/health")
        return {"phone": phone, "connection": health}

    async def capabilities(self) -> dict[str, Any]:
        return await self._object("GET", "/v1/capabilities")

    async def current_plan(self) -> dict[str, Any]:
        return await self._object("GET", "/v1/plan/profile")

    async def list_plans(self) -> dict[str, Any]:
        return await self._object("GET", "/v1/plans")

    async def get_plan(self, plan_id: str) -> dict[str, Any]:
        return await self._object("GET", f"/v1/plans/{encoded(plan_id)}")

    async def set_plan(
        self, request_id: str, expected_revision: int, plan: dict[str, Any]
    ) -> dict[str, Any]:
        payload = write_metadata(request_id, expected_revision)
        payload["plan"] = plan
        plan_id = str(plan.get("id", ""))
        method, path = (
            ("PUT", f"/v1/plans/{encoded(plan_id)}") if plan_id else ("POST", "/v1/plans")
        )
        return await self._object(method, path, payload)

    async def delete_plan(
        self, request_id: str, expected_revision: int, plan_id: str
    ) -> dict[str, Any]:
        return await self._object(
            "DELETE", f"/v1/plans/{encoded(plan_id)}", write_metadata(request_id, expected_revision)
        )

    async def select_plan(
        self, request_id: str, expected_revision: int, plan_id: str
    ) -> dict[str, Any]:
        payload = write_metadata(request_id, expected_revision)
        payload["planId"] = plan_id
        return await self._object("PUT", "/v1/plan-selection", payload)

    async def list_groups(self) -> dict[str, Any]:
        return await self._object("GET", "/v1/plan-groups")

    async def create_group(
        self, request_id: str, expected_revision: int, name: str
    ) -> dict[str, Any]:
        payload = write_metadata(request_id, expected_revision)
        payload["name"] = name
        return await self._object("POST", "/v1/plan-groups", payload)

    async def rename_group(
        self, request_id: str, expected_revision: int, group_id: str, name: str
    ) -> dict[str, Any]:
        payload = write_metadata(request_id, expected_revision)
        payload["name"] = name
        return await self._object("PUT", f"/v1/plan-groups/{encoded(group_id)}", payload)

    async def delete_group(
        self, request_id: str, expected_revision: int, group_id: str
    ) -> dict[str, Any]:
        return await self._object(
            "DELETE",
            f"/v1/plan-groups/{encoded(group_id)}",
            write_metadata(request_id, expected_revision),
        )

    async def list_workouts(self, limit: int = 20) -> dict[str, Any]:
        value = await self.client.request("GET", "/v1/history")
        rows = cast(list[Any], value) if isinstance(value, list) else []
        count = max(1, min(limit, 100))
        return {"items": rows[:count], "count": min(count, len(rows)), "total": len(rows)}

    async def get_workout(self, workout_id: str) -> dict[str, Any]:
        value = await self._object("GET", f"/v1/history/{encoded(workout_id)}")
        value.pop("route", None)
        value.pop("heartRates", None)
        value["routeResource"] = f"watch://workouts/{workout_id}/route/0"
        value["heartResource"] = f"watch://workouts/{workout_id}/heart/0"
        return value

    async def summarize_workouts(self) -> dict[str, Any]:
        value = await self.client.request("GET", "/v1/history")
        rows = cast(list[dict[str, Any]], value) if isinstance(value, list) else []
        heart_rates = [
            float(row["averageHeartRate"]) for row in rows if row.get("averageHeartRate")
        ]
        return {
            "workoutCount": len(rows),
            "totalDistanceMeters": sum(float(row.get("distanceMeters", 0)) for row in rows),
            "totalActiveDurationMs": sum(
                float(row.get("activeDurationMs", row.get("durationMs", 0))) for row in rows
            ),
            "totalSteps": sum(int(row.get("steps", 0)) for row in rows),
            "averageHeartRate": round(sum(heart_rates) / len(heart_rates)) if heart_rates else None,
            "latest": rows[0] if rows else None,
        }

    async def delete_workout(
        self, request_id: str, expected_revision: int, workout_id: str
    ) -> dict[str, Any]:
        return await self._object(
            "DELETE",
            f"/v1/history/{encoded(workout_id)}",
            write_metadata(request_id, expected_revision),
        )

    async def latest_sleep(self) -> dict[str, Any]:
        value = await self._object("GET", "/v1/sleep?days=7")
        records = cast(list[dict[str, Any]], value.get("records", []))
        latest = max(records, key=lambda item: item.get("timestamp", 0)) if records else None
        if isinstance(latest, dict):
            latest = dict(latest)
            latest.pop("sessions", None)
        return {
            "state": value.get("state"),
            "source": value.get("source"),
            "record": latest,
            "detailResource": "watch://sleep/7",
        }

    async def summarize_sleep(self, days: int = 7) -> dict[str, Any]:
        days = max(1, min(days, 31))
        value = await self._object("GET", f"/v1/sleep?days={days}")
        records = cast(list[dict[str, Any]], value.get("records", []))
        durations = [
            float(x["totalDurationMinutes"]) for x in records if x.get("totalDurationMinutes")
        ]
        scores = [float(x["sleepScore"]) for x in records if x.get("sleepScore")]
        spo2 = [float(x["spo2AveragePercent"]) for x in records if x.get("spo2AveragePercent")]
        return {
            "state": value.get("state"),
            "source": value.get("source"),
            "recordCount": len(records),
            "averageDurationMinutes": round(sum(durations) / len(durations)) if durations else None,
            "averageSleepScore": round(sum(scores) / len(scores)) if scores else None,
            "averageSpo2Percent": round(sum(spo2) / len(spo2), 1) if spo2 else None,
            "detailResource": f"watch://sleep/{days}",
        }

    async def control(
        self,
        action: str,
        request_id: str,
        expected_revision: int,
        command_id: str,
        expected_state: str,
        expires_at: int,
    ) -> dict[str, Any]:
        payload = control_metadata(
            request_id, expected_revision, command_id, expected_state, expires_at
        )
        return await self._object("POST", f"/v1/control/{action}", payload)

    async def sync(self, request_id: str, expected_revision: int) -> dict[str, Any]:
        return await self._object("POST", "/v1/sync", write_metadata(request_id, expected_revision))

    async def sync_status(self) -> dict[str, Any]:
        return await self._object("GET", "/v1/sync/status")

    async def _object(
        self, method: str, path: str, body: dict[str, Any] | None = None
    ) -> dict[str, Any]:
        value = await self.client.request(method, path, body)
        if not isinstance(value, dict):
            raise WatchMcpError("PHONE_PROTOCOL_ERROR", "Expected a JSON object", True)
        return cast(dict[str, Any], value)
