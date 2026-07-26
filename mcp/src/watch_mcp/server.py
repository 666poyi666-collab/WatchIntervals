# pyright: reportUnusedFunction=false
from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Any

from mcp.server.fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import JSONResponse, PlainTextResponse, Response

from watch_mcp.client import PhoneApiClient
from watch_mcp.health import Metrics
from watch_mcp.resources import WatchResources
from watch_mcp.settings import Settings
from watch_mcp.tools import WatchTools


def build_server(settings: Settings) -> FastMCP:
    settings.validate()
    metrics = Metrics()
    client = PhoneApiClient(settings, metrics)
    tools = WatchTools(client, metrics)
    resources = WatchResources(client, tools)

    def write_arg(value: str | None, alias: str | None) -> str:
        return value if value is not None else (alias or "")

    def revision_arg(value: int | None, alias: int | None) -> int:
        return value if value is not None else (alias if alias is not None else 0)

    @asynccontextmanager
    async def lifespan(_: FastMCP[Any]):
        settings.data_dir.mkdir(parents=True, exist_ok=True)
        try:
            yield {"client": client}
        finally:
            await client.close()

    server = FastMCP(
        name="WatchIntervals",
        instructions=(
            "Manage only WatchIntervals plans, workouts, history, and sleep. "
            "Use watch:// resources for route, heart-rate, and full sleep data."
        ),
        stateless_http=True,
        json_response=True,
        host=settings.host,
        port=settings.port,
        streamable_http_path="/mcp",
        lifespan=lifespan,
    )

    @server.custom_route("/healthz", methods=["GET"])
    async def healthz(_: Request) -> Response:
        return JSONResponse({"service": "PoyiWatchMcp", "state": "alive"})

    @server.custom_route("/readyz", methods=["GET"])
    async def readyz(_: Request) -> Response:
        return JSONResponse({"service": "PoyiWatchMcp", "state": "ready"})

    @server.custom_route("/metrics", methods=["GET"])
    async def metrics_route(_: Request) -> Response:
        return PlainTextResponse(metrics.render(True), media_type="text/plain; version=0.0.4")

    @server.tool(name="watch_get_status", description="Get phone, watch, BLE, and workout status")
    async def watch_get_status() -> dict[str, Any]:
        return await tools.invoke(tools.status())

    @server.tool(name="watch_get_capabilities", description="Get WatchIntervals API capabilities")
    async def watch_get_capabilities() -> dict[str, Any]:
        return await tools.invoke(tools.capabilities())

    @server.tool(name="watch_get_current_plan", description="Get the selected workout plan")
    async def watch_get_current_plan() -> dict[str, Any]:
        return await tools.invoke(tools.current_plan())

    @server.tool(name="watch_list_plans", description="List phone-authoritative workout plans")
    async def watch_list_plans() -> dict[str, Any]:
        return await tools.invoke(tools.list_plans())

    @server.tool(name="watch_get_plan", description="Get one workout plan by ID")
    async def watch_get_plan(plan_id: str) -> dict[str, Any]:
        return await tools.invoke(tools.get_plan(plan_id))

    @server.tool(name="watch_set_plan", description="Create or update a workout plan idempotently")
    async def watch_set_plan(
        plan: dict[str, Any],
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.set_plan(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                plan,
            )
        )

    @server.tool(name="watch_delete_plan", description="Delete a workout plan idempotently")
    async def watch_delete_plan(
        plan_id: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.delete_plan(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                plan_id,
            )
        )

    @server.tool(name="watch_select_plan", description="Select and sync a workout plan")
    async def watch_select_plan(
        plan_id: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.select_plan(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                plan_id,
            )
        )

    @server.tool(name="watch_list_plan_groups", description="List workout plan groups")
    async def watch_list_plan_groups() -> dict[str, Any]:
        return await tools.invoke(tools.list_groups())

    @server.tool(name="watch_create_plan_group", description="Create a plan group idempotently")
    async def watch_create_plan_group(
        name: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.create_group(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                name,
            )
        )

    @server.tool(name="watch_rename_plan_group", description="Rename a plan group idempotently")
    async def watch_rename_plan_group(
        group_id: str,
        name: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.rename_group(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                group_id,
                name,
            )
        )

    @server.tool(name="watch_delete_plan_group", description="Delete a plan group idempotently")
    async def watch_delete_plan_group(
        group_id: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.delete_group(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                group_id,
            )
        )

    @server.tool(name="watch_list_workouts", description="List workout summaries")
    async def watch_list_workouts(limit: int = 20) -> dict[str, Any]:
        return await tools.invoke(tools.list_workouts(limit))

    @server.tool(name="watch_get_workout", description="Get workout summary and resource links")
    async def watch_get_workout(workout_id: str) -> dict[str, Any]:
        return await tools.invoke(tools.get_workout(workout_id))

    @server.tool(name="watch_summarize_workouts", description="Summarize workout history")
    async def watch_summarize_workouts() -> dict[str, Any]:
        return await tools.invoke(tools.summarize_workouts())

    @server.tool(name="watch_delete_workout", description="Delete a workout idempotently")
    async def watch_delete_workout(
        workout_id: str,
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.delete_workout(
                write_arg(request_id, requestId),
                revision_arg(expected_revision, expectedRevision),
                workout_id,
            )
        )

    @server.tool(name="watch_get_latest_sleep", description="Get latest sleep summary")
    async def watch_get_latest_sleep() -> dict[str, Any]:
        return await tools.invoke(tools.latest_sleep())

    @server.tool(name="watch_summarize_sleep", description="Summarize recent sleep metrics")
    async def watch_summarize_sleep(days: int = 7) -> dict[str, Any]:
        return await tools.invoke(tools.summarize_sleep(days))

    async def control(
        action: str,
        request_id: str,
        expected_revision: int,
        command_id: str,
        expected_state: str,
        expires_at: int,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.control(
                action,
                request_id,
                expected_revision,
                command_id,
                expected_state,
                expires_at,
            )
        )

    @server.tool(name="watch_start_workout", description="Start the selected workout")
    async def watch_start_workout(
        request_id: str | None = None,
        expected_revision: int | None = None,
        command_id: str | None = None,
        expected_state: str | None = None,
        expires_at: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
        commandId: str | None = None,
        expectedState: str | None = None,
        expiresAt: int | None = None,
    ) -> dict[str, Any]:
        return await control(
            "start",
            write_arg(request_id, requestId),
            revision_arg(expected_revision, expectedRevision),
            write_arg(command_id, commandId),
            write_arg(expected_state, expectedState),
            revision_arg(expires_at, expiresAt),
        )

    @server.tool(name="watch_pause_workout", description="Pause a running workout")
    async def watch_pause_workout(
        request_id: str | None = None,
        expected_revision: int | None = None,
        command_id: str | None = None,
        expected_state: str | None = None,
        expires_at: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
        commandId: str | None = None,
        expectedState: str | None = None,
        expiresAt: int | None = None,
    ) -> dict[str, Any]:
        return await control(
            "pause",
            write_arg(request_id, requestId),
            revision_arg(expected_revision, expectedRevision),
            write_arg(command_id, commandId),
            write_arg(expected_state, expectedState),
            revision_arg(expires_at, expiresAt),
        )

    @server.tool(name="watch_resume_workout", description="Resume a paused workout")
    async def watch_resume_workout(
        request_id: str | None = None,
        expected_revision: int | None = None,
        command_id: str | None = None,
        expected_state: str | None = None,
        expires_at: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
        commandId: str | None = None,
        expectedState: str | None = None,
        expiresAt: int | None = None,
    ) -> dict[str, Any]:
        return await control(
            "resume",
            write_arg(request_id, requestId),
            revision_arg(expected_revision, expectedRevision),
            write_arg(command_id, commandId),
            write_arg(expected_state, expectedState),
            revision_arg(expires_at, expiresAt),
        )

    @server.tool(name="watch_stop_workout", description="Stop and save the active workout")
    async def watch_stop_workout(
        request_id: str | None = None,
        expected_revision: int | None = None,
        command_id: str | None = None,
        expected_state: str | None = None,
        expires_at: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
        commandId: str | None = None,
        expectedState: str | None = None,
        expiresAt: int | None = None,
    ) -> dict[str, Any]:
        return await control(
            "stop",
            write_arg(request_id, requestId),
            revision_arg(expected_revision, expectedRevision),
            write_arg(command_id, commandId),
            write_arg(expected_state, expectedState),
            revision_arg(expires_at, expiresAt),
        )

    @server.tool(name="watch_get_sync_status", description="Get phone-to-watch sync status")
    async def watch_get_sync_status() -> dict[str, Any]:
        return await tools.invoke(tools.sync_status())

    @server.tool(name="watch_sync_plans", description="Retry plan synchronization idempotently")
    async def watch_sync_plans(
        request_id: str | None = None,
        expected_revision: int | None = None,
        requestId: str | None = None,
        expectedRevision: int | None = None,
    ) -> dict[str, Any]:
        return await tools.invoke(
            tools.sync(
                write_arg(request_id, requestId), revision_arg(expected_revision, expectedRevision)
            )
        )

    @server.resource("watch://status", name="Watch status")
    async def status_resource() -> str:
        return await resources.status()

    @server.resource("watch://capabilities", name="Watch capabilities")
    async def capabilities_resource() -> str:
        return await resources.capabilities()

    @server.resource("watch://plans", name="Watch plans")
    async def plans_resource() -> str:
        return await resources.plans()

    @server.resource("watch://workouts/recent", name="Recent workouts")
    async def recent_workouts_resource() -> str:
        return await resources.recent_workouts()

    @server.resource("watch://workouts/{workout_id}", name="Workout detail")
    async def workout_resource(workout_id: str) -> str:
        return await resources.workout(workout_id)

    @server.resource("watch://workouts/{workout_id}/route/{cursor}", name="Workout route page")
    async def route_resource(workout_id: str, cursor: str) -> str:
        return await resources.route(workout_id, cursor)

    @server.resource("watch://workouts/{workout_id}/heart/{cursor}", name="Heart-rate page")
    async def heart_resource(workout_id: str, cursor: str) -> str:
        return await resources.heart(workout_id, cursor)

    @server.resource("watch://sleep/{days}", name="Sleep details")
    async def sleep_resource(days: str) -> str:
        return await resources.sleep(days)

    return server
