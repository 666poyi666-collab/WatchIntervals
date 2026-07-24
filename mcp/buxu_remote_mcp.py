#!/usr/bin/env python3
"""步序运动 Streamable HTTP MCP for ChatGPT custom plugins."""
import os
from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings
import watch_intervals_mcp as core

path_token = os.environ.get("BUXU_MCP_PATH", "buxu")
mcp = FastMCP(
    "步序运动",
    instructions="查询和管理步序手机/手表中的训练计划、运动状态、历史与轨迹。",
    host="127.0.0.1",
    port=int(os.environ.get("BUXU_MCP_PORT", "8878")),
    streamable_http_path=f"/{path_token}/mcp",
    stateless_http=True,
    json_response=True,
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)

@mcp.tool(description="查询手表连接、版本和训练状态")
def watch_status() -> dict: return core.call("watch_status", {})

@mcp.tool(description="读取当前训练计划")
def get_training_plan() -> dict: return core.call("get_training_plan", {})

@mcp.tool(description="读取当前计划名称、分组、具体要求和全部阶段")
def get_training_plan_profile() -> dict: return core.call("get_training_plan_profile", {})

@mcp.tool(description="完整替换手表训练计划；unit 使用 DISTANCE 或 TIME")
def set_training_plan(stages: list[dict]) -> dict:
    return core.call("set_training_plan", {"stages": stages})

@mcp.tool(description="设置计划名称、分组、要求和全部阶段并同步到手表")
def set_training_plan_profile(name: str, group: str, requirement: str, stages: list[dict]) -> dict:
    return core.call("set_training_plan_profile", {"name": name, "group": group, "requirement": requirement, "stages": stages})

@mcp.tool(description="列出手机主计划库的全部分组")
def list_plan_groups() -> dict: return core.call("list_plan_groups", {})

@mcp.tool(description="在手机主计划库创建分组并同步手表")
def create_plan_group(name: str) -> dict: return core.call("create_plan_group", {"name": name})

@mcp.tool(description="重命名计划分组并同步手表")
def rename_plan_group(id: str, name: str) -> dict: return core.call("rename_plan_group", {"id": id, "name": name})

@mcp.tool(description="删除分组，并把其中计划移动到“我的计划”")
def delete_plan_group(id: str) -> dict: return core.call("delete_plan_group", {"id": id})

@mcp.tool(description="列出手机主计划库的全部计划与当前选择")
def list_training_plans() -> dict: return core.call("list_training_plans", {})

@mcp.tool(description="创建命名、分组的训练计划并同步手表")
def create_training_plan(name: str, group: str, stages: list[dict], requirement: str = "") -> dict:
    return core.call("create_training_plan", {"name": name, "group": group, "requirement": requirement, "stages": stages})

@mcp.tool(description="按 ID 更新训练计划并同步手表")
def update_training_plan(id: str, name: str, group: str, stages: list[dict], requirement: str = "") -> dict:
    return core.call("update_training_plan", {"id": id, "name": name, "group": group, "requirement": requirement, "stages": stages})

@mcp.tool(description="从手机主计划库删除计划并同步手表")
def delete_training_plan(id: str) -> dict: return core.call("delete_training_plan", {"id": id})

@mcp.tool(description="选择计划为手表当前训练并同步")
def select_training_plan(id: str) -> dict: return core.call("select_training_plan", {"id": id})

@mcp.tool(description="立即把手机完整计划库同步到手表")
def sync_plan_library() -> dict: return core.call("sync_plan_library", {})

@mcp.tool(description="查询全部训练历史，包含轨迹、距离、步数、心率和时间")
def list_workouts() -> list[dict]: return core.call("list_workouts", {})

@mcp.tool(description="汇总训练次数、总距离、总时长、总步数、平均心率和最近训练")
def summarize_workouts() -> dict: return core.call("summarize_workouts", {})

@mcp.tool(description="按 ID 查询一条训练的完整统计和轨迹点")
def get_workout(id: str) -> dict: return core.call("get_workout", {"id": id})

@mcp.tool(description="按当前计划开始手表训练")
def start_workout() -> dict: return core.call("start_workout", {})

@mcp.tool(description="暂停当前训练")
def pause_workout() -> dict: return core.call("pause_workout", {})

@mcp.tool(description="继续当前训练")
def resume_workout() -> dict: return core.call("resume_workout", {})

@mcp.tool(description="结束并保存当前训练")
def stop_workout() -> dict: return core.call("stop_workout", {})

@mcp.tool(description="删除指定训练记录")
def delete_workout(id: str) -> dict: return core.call("delete_workout", {"id": id})

if __name__ == "__main__":
    mcp.run(transport="streamable-http")
