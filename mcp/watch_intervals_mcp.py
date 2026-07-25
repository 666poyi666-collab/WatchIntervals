#!/usr/bin/env python3
"""Dependency-free local MCP server for 步序."""
import json, os, sys, urllib.request, urllib.error, urllib.parse, uuid
from pathlib import Path

CONFIG = Path(os.environ.get("WATCH_INTERVALS_CONFIG", Path.home() / ".watchintervals.json"))

def config():
    data = {"host": "192.168.1.44", "port": 8765, "phoneHost": "192.168.1.84", "phonePort": 8766, "pairingCode": ""}
    if CONFIG.exists(): data.update(json.loads(CONFIG.read_text("utf-8")))
    data["host"] = os.environ.get("WATCH_INTERVALS_HOST", data["host"])
    data["pairingCode"] = os.environ.get("WATCH_INTERVALS_CODE", data["pairingCode"])
    return data

def request(method, path, body=None):
    cfg = config(); url = f"http://{cfg['host']}:{cfg['port']}{path}"
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=payload, method=method, headers={"X-Pairing-Code": cfg["pairingCode"], "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as response: return json.loads(response.read().decode())

def phone_request(method, path, body=None):
    cfg = config(); url = f"http://{cfg['phoneHost']}:{cfg.get('phonePort',8766)}{path}"
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=payload, method=method, headers={"X-Pairing-Code": cfg.get("phonePairingCode",cfg["pairingCode"]), "Content-Type":"application/json"})
    with urllib.request.urlopen(req, timeout=15) as response: return json.loads(response.read().decode())

def _profile_plan_id(profile):
    """Keep repeated writes of the same named profile idempotent."""
    identity = f"watchintervals:{profile['group'].strip()}:{profile['name'].strip()}"
    return str(uuid.uuid5(uuid.NAMESPACE_URL, identity))

def _normalized_stages(stages):
    return [{"kind": item.get("kind"), "unit": item.get("unit"), "target": item.get("target")} for item in stages]

def set_training_plan_profile(profile):
    plan_id = _profile_plan_id(profile)
    payload = dict(profile, id=plan_id)

    saved = phone_request("POST", "/v1/plans", payload)
    selected = phone_request("PUT", "/v1/plan-selection", {"planId": plan_id})
    sync = selected.get("sync", {})
    if sync.get("state") != "synced":
        raise RuntimeError("plan_saved_on_phone_but_watch_sync_pending: " + json.dumps(sync, ensure_ascii=False))

    phone_plans = phone_request("GET", "/v1/plans")
    watch_library = request("GET", "/v1/plan-library")
    watch_profile = request("GET", "/v1/plan/profile")
    phone_ids = {item.get("id") for item in phone_plans.get("plans", [])}
    watch_ids = {item.get("id") for item in watch_library.get("plans", [])}
    expected_stages = _normalized_stages(profile["stages"])
    actual_stages = _normalized_stages(watch_profile.get("stages", []))

    verified = (
        plan_id in phone_ids
        and phone_plans.get("selectedPlanId") == plan_id
        and plan_id in watch_ids
        and watch_library.get("selectedPlanId") == plan_id
        and watch_profile.get("name") == profile["name"]
        and watch_profile.get("group") == profile["group"]
        and watch_profile.get("requirement") == profile["requirement"]
        and actual_stages == expected_stages
    )
    if not verified:
        raise RuntimeError("plan_sync_verification_failed")

    return {
        "saved": True,
        "verified": True,
        "planId": plan_id,
        "phoneSelectedPlanId": phone_plans.get("selectedPlanId"),
        "watchSelectedPlanId": watch_library.get("selectedPlanId"),
        "sync": sync,
        "profile": watch_profile,
        "phoneMutation": saved.get("sync", {}),
    }

def summarize_sleep_result(result):
    records = result.get("records", [])
    durations = [item.get("totalDurationMinutes", 0) for item in records if item.get("totalDurationMinutes", 0) > 0]
    scores = [item.get("sleepScore", 0) for item in records if item.get("sleepScore", 0) > 0]
    spo2 = [item.get("spo2AveragePercent", 0) for item in records if item.get("spo2AveragePercent", 0) > 0]
    return {
        "state": result.get("state"), "source": result.get("source"), "recordCount": len(records),
        "averageDurationMinutes": round(sum(durations) / len(durations)) if durations else 0,
        "averageSleepScore": round(sum(scores) / len(scores)) if scores else 0,
        "averageSpo2Percent": round(sum(spo2) / len(spo2), 1) if spo2 else 0,
        "latestSleep": max(records, key=lambda item: item.get("timestamp", 0)) if records else None,
    }

TOOLS = [
    ("watch_status", "查询手表连接、版本和训练状态", {"type":"object","properties":{}}),
    ("get_training_plan", "读取当前训练计划", {"type":"object","properties":{}}),
    ("get_training_plan_profile", "读取当前计划名称、分组、具体要求和全部阶段", {"type":"object","properties":{}}),
    ("set_training_plan", "完整替换手表训练计划", {"type":"object","properties":{"stages":{"type":"array","items":{"type":"object","properties":{"kind":{"enum":["RUN","WALK","REST"]},"unit":{"enum":["DISTANCE","TIME"]},"target":{"type":"integer","minimum":1}},"required":["kind","unit","target"]}}},"required":["stages"]}),
    ("set_training_plan_profile", "持久写入手机主计划库、选择并同步到手表；两端回读一致才成功", {"type":"object","properties":{"name":{"type":"string"},"group":{"type":"string"},"requirement":{"type":"string"},"stages":{"type":"array","items":{"type":"object","properties":{"kind":{"enum":["RUN","WALK","REST"]},"unit":{"enum":["DISTANCE","TIME"]},"target":{"type":"integer","minimum":1}},"required":["kind","unit","target"]}}},"required":["name","group","requirement","stages"]}),
    ("list_plan_groups", "列出手机主计划库的全部分组", {"type":"object","properties":{}}),
    ("create_plan_group", "在手机主计划库创建分组并同步手表", {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}),
    ("rename_plan_group", "重命名计划分组并同步手表", {"type":"object","properties":{"id":{"type":"string"},"name":{"type":"string"}},"required":["id","name"]}),
    ("delete_plan_group", "删除分组并把其中计划移动到“我的计划”", {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}),
    ("list_training_plans", "列出手机主计划库的全部计划与当前选择", {"type":"object","properties":{}}),
    ("create_training_plan", "创建命名、分组的训练计划并同步手表", {"type":"object","properties":{"name":{"type":"string"},"group":{"type":"string"},"requirement":{"type":"string"},"stages":{"type":"array","items":{"type":"object"}}},"required":["name","group","stages"]}),
    ("update_training_plan", "按 ID 更新训练计划并同步手表", {"type":"object","properties":{"id":{"type":"string"},"name":{"type":"string"},"group":{"type":"string"},"requirement":{"type":"string"},"stages":{"type":"array","items":{"type":"object"}}},"required":["id","name","group","stages"]}),
    ("delete_training_plan", "从手机主计划库删除计划并同步手表", {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}),
    ("select_training_plan", "选择计划为手表当前训练并同步", {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}),
    ("sync_plan_library", "立即把手机完整计划库同步到手表", {"type":"object","properties":{}}),
    ("list_workouts", "查询全部训练历史，包含轨迹、距离、步数、心率和时间", {"type":"object","properties":{}}),
    ("summarize_workouts", "汇总训练次数、总距离、总时长、总步数、平均心率和最近训练", {"type":"object","properties":{}}),
    ("get_workout", "按 ID 查询一条训练的完整统计和轨迹点", {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}),
    ("get_latest_sleep", "读取最近一条系统睡眠记录及完整阶段时间线", {"type":"object","properties":{}}),
    ("list_sleep_records", "读取指定天数内的系统睡眠、评分、血氧、心率、呼吸与阶段", {"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":31}}}),
    ("summarize_sleep", "汇总指定天数内的系统睡眠时长、评分与平均血氧", {"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":31}}}),
    ("start_workout", "按当前计划开始手表训练", {"type":"object","properties":{}}),
    ("pause_workout", "暂停当前训练", {"type":"object","properties":{}}),
    ("resume_workout", "继续当前训练", {"type":"object","properties":{}}),
    ("stop_workout", "结束并保存当前训练", {"type":"object","properties":{}}),
    ("delete_workout", "删除指定训练记录", {"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}),
]

def call(name, args):
    if name == "watch_status": return request("GET", "/v1/status")
    if name == "get_training_plan": return request("GET", "/v1/plan")
    if name == "get_training_plan_profile": return request("GET", "/v1/plan/profile")
    if name == "set_training_plan": return request("PUT", "/v1/plan", args["stages"])
    if name == "set_training_plan_profile": return set_training_plan_profile(args)
    if name == "list_plan_groups": return phone_request("GET", "/v1/plan-groups")
    if name == "create_plan_group": return phone_request("POST", "/v1/plan-groups", {"name":args["name"]})
    if name == "rename_plan_group": return phone_request("PUT", "/v1/plan-groups/" + urllib.parse.quote(args["id"]), {"name":args["name"]})
    if name == "delete_plan_group": return phone_request("DELETE", "/v1/plan-groups/" + urllib.parse.quote(args["id"]), {})
    if name == "list_training_plans": return phone_request("GET", "/v1/plans")
    if name == "create_training_plan": return phone_request("POST", "/v1/plans", args)
    if name == "update_training_plan": return phone_request("PUT", "/v1/plans/" + urllib.parse.quote(args["id"]), args)
    if name == "delete_training_plan": return phone_request("DELETE", "/v1/plans/" + urllib.parse.quote(args["id"]), {})
    if name == "select_training_plan": return phone_request("PUT", "/v1/plan-selection", {"planId":args["id"]})
    if name == "sync_plan_library": return phone_request("POST", "/v1/sync", {})
    if name == "list_workouts": return request("GET", "/v1/history")
    if name == "summarize_workouts":
        rows=request("GET", "/v1/history"); total_duration=sum(x.get("durationMs",0) for x in rows); hr=[x.get("averageHeartRate",0) for x in rows if x.get("averageHeartRate",0)>0]
        return {"workoutCount":len(rows),"totalDistanceMeters":sum(x.get("distanceMeters",0) for x in rows),"totalDurationMs":total_duration,"totalSteps":sum(x.get("steps",0) for x in rows),"averageHeartRate":round(sum(hr)/len(hr)) if hr else 0,"latestWorkout":rows[0] if rows else None}
    if name == "get_workout": return request("GET", "/v1/history/" + urllib.parse.quote(args["id"]))
    if name == "list_sleep_records": return request("GET", "/v1/sleep?days=" + str(max(1, min(31, int(args.get("days", 7))))))
    if name == "get_latest_sleep":
        result=request("GET", "/v1/sleep?days=7"); records=result.get("records", [])
        return {"state":result.get("state"),"source":result.get("source"),"record":max(records,key=lambda item:item.get("timestamp",0)) if records else None}
    if name == "summarize_sleep":
        days=max(1,min(31,int(args.get("days",7))))
        return summarize_sleep_result(request("GET", "/v1/sleep?days="+str(days)))
    if name == "delete_workout": return request("DELETE", "/v1/history/" + urllib.parse.quote(args["id"]))
    controls = {"start_workout":"start","pause_workout":"pause","resume_workout":"resume","stop_workout":"stop"}
    if name in controls: return request("POST", "/v1/control/" + controls[name], {})
    raise ValueError("unknown tool: " + name)

def respond(obj):
    sys.stdout.write(json.dumps(obj, ensure_ascii=False, separators=(",", ":")) + "\n"); sys.stdout.flush()

def main():
    for line in sys.stdin:
        try:
            msg = json.loads(line.lstrip("\ufeff")); ident = msg.get("id"); method = msg.get("method")
            if method == "notifications/initialized": continue
            elif method == "initialize": result = {"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"buxu-sports","title":"步序运动","version":"0.5.0"}}
            elif method == "tools/list":
                result = {"tools":[{"name":n,"description":d,"inputSchema":s,"annotations":{"readOnlyHint":n in {"watch_status","get_training_plan","get_training_plan_profile","list_plan_groups","list_training_plans","list_workouts","summarize_workouts","get_workout","get_latest_sleep","list_sleep_records","summarize_sleep"},"destructiveHint":n in {"delete_workout","delete_plan_group","delete_training_plan"}}} for n,d,s in TOOLS]}
            elif method == "tools/call":
                p=msg.get("params",{}); value=call(p.get("name"),p.get("arguments",{})); result={"content":[{"type":"text","text":json.dumps(value,ensure_ascii=False)}],"structuredContent":{"result":value}}
            elif method == "ping": result={}
            else: raise ValueError("unsupported method: " + str(method))
            if ident is not None: respond({"jsonrpc":"2.0","id":ident,"result":result})
        except Exception as error:
            if 'ident' in locals() and ident is not None: respond({"jsonrpc":"2.0","id":ident,"error":{"code":-32000,"message":str(error)}})

if __name__ == "__main__": main()
