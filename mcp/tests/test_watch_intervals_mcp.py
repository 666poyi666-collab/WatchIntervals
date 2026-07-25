import importlib.util
import pathlib
import unittest
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).parents[1] / "watch_intervals_mcp.py"
SPEC = importlib.util.spec_from_file_location("watch_intervals_mcp", MODULE_PATH)
MCP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MCP)


class SetTrainingPlanProfileTests(unittest.TestCase):
    def setUp(self):
        self.profile = {
            "name": "Baseline walk",
            "group": "Four weeks",
            "requirement": "Keep the effort easy.",
            "stages": [
                {"kind": "WALK", "unit": "TIME", "target": 300},
                {"kind": "WALK", "unit": "TIME", "target": 1200},
                {"kind": "WALK", "unit": "TIME", "target": 300},
            ],
        }

    def test_writes_phone_selects_syncs_and_verifies_both_devices(self):
        plan_id = MCP._profile_plan_id(self.profile)
        phone_responses = [
            {"library": {}, "sync": {"state": "synced"}},
            {"library": {}, "sync": {"state": "synced"}},
            {"selectedPlanId": plan_id, "plans": [{"id": plan_id}]},
        ]
        watch_responses = [
            {"selectedPlanId": plan_id, "plans": [{"id": plan_id}]},
            dict(self.profile),
        ]

        with patch.object(MCP, "phone_request", side_effect=phone_responses) as phone, patch.object(MCP, "request", side_effect=watch_responses) as watch:
            result = MCP.set_training_plan_profile(self.profile)

        self.assertTrue(result["verified"])
        self.assertEqual(plan_id, result["planId"])
        self.assertEqual("POST", phone.call_args_list[0].args[0])
        self.assertEqual(plan_id, phone.call_args_list[0].args[2]["id"])
        self.assertEqual(("PUT", "/v1/plan-selection", {"planId": plan_id}), phone.call_args_list[1].args)
        self.assertEqual(("GET", "/v1/plan-library"), watch.call_args_list[0].args)

    def test_rejects_pending_watch_sync(self):
        phone_responses = [
            {"library": {}, "sync": {"state": "pending"}},
            {"library": {}, "sync": {"state": "pending", "reason": "watch_offline"}},
        ]
        with patch.object(MCP, "phone_request", side_effect=phone_responses), self.assertRaisesRegex(RuntimeError, "watch_sync_pending"):
            MCP.set_training_plan_profile(self.profile)

    def test_rejects_mismatched_readback(self):
        plan_id = MCP._profile_plan_id(self.profile)
        phone_responses = [
            {"library": {}, "sync": {"state": "synced"}},
            {"library": {}, "sync": {"state": "synced"}},
            {"selectedPlanId": plan_id, "plans": [{"id": plan_id}]},
        ]
        watch_responses = [
            {"selectedPlanId": plan_id, "plans": [{"id": plan_id}]},
            dict(self.profile, name="Different plan"),
        ]
        with patch.object(MCP, "phone_request", side_effect=phone_responses), patch.object(MCP, "request", side_effect=watch_responses), self.assertRaisesRegex(RuntimeError, "verification_failed"):
            MCP.set_training_plan_profile(self.profile)

    def test_plan_id_is_stable_for_retries(self):
        self.assertEqual(MCP._profile_plan_id(self.profile), MCP._profile_plan_id(dict(self.profile)))


class SleepToolsTests(unittest.TestCase):
    def test_sleep_summary_uses_available_system_metrics(self):
        result = MCP.summarize_sleep_result({"state":"ready","source":"system_healthkit","records":[
            {"totalDurationMinutes":420,"sleepScore":80,"spo2AveragePercent":96},
            {"totalDurationMinutes":480,"sleepScore":90,"spo2AveragePercent":94},
        ]})
        self.assertEqual(result["recordCount"], 2)
        self.assertEqual(result["averageDurationMinutes"], 450)
        self.assertEqual(result["averageSleepScore"], 85)
        self.assertEqual(result["averageSpo2Percent"], 95.0)
        self.assertEqual(result["metricSampleCounts"]["sleepScore"], 2)

    def test_sleep_summary_marks_missing_metrics_instead_of_reporting_zero(self):
        result = MCP.summarize_sleep_result({"state":"ready","source":"system_healthkit","records":[
            {"totalDurationMinutes":360,"sleepScore":0,"spo2AveragePercent":0,"sessions":[]},
        ]})
        self.assertIsNone(result["averageSleepScore"])
        self.assertIsNone(result["averageSpo2Percent"])
        self.assertEqual(result["missingMetricCounts"]["sleepScore"], 1)
        self.assertEqual(result["missingMetricCounts"]["spo2"], 1)
        self.assertEqual(result["missingMetricCounts"]["sessions"], 1)

    def test_latest_sleep_preserves_empty_state(self):
        with patch.object(MCP, "request", return_value={"state":"ready","source":"system_healthkit","records":[]}):
            self.assertIsNone(MCP.call("get_latest_sleep", {})["record"])

    def test_latest_sleep_does_not_trust_service_order(self):
        rows=[{"timestamp":10},{"timestamp":30},{"timestamp":20}]
        with patch.object(MCP, "request", return_value={"state":"ready","source":"system_healthkit","records":rows}):
            self.assertEqual(MCP.call("get_latest_sleep", {})["record"]["timestamp"], 30)


class ProtocolV2Tests(unittest.TestCase):
    def test_get_workout_collects_all_route_pages(self):
        responses = [
            {"id":"record","route":[{"preview":True}],"routeTruncated":True},
            {"items":[{"latitude":1}],"nextCursor":1,"total":2},
            {"items":[{"latitude":2}],"nextCursor":None,"total":2},
        ]
        with patch.object(MCP,"request",side_effect=responses) as request:
            result=MCP.get_workout_full("record")
        self.assertEqual([1,2],[item["latitude"] for item in result["route"]])
        self.assertFalse(result["routeTruncated"])
        self.assertIn("cursor=1",request.call_args_list[2].args[1])

    def test_pause_control_is_idempotent_command(self):
        with patch.object(MCP,"request",return_value={"accepted":True}) as request:
            MCP.call("pause_workout",{})
        method,path,body=request.call_args.args
        self.assertEqual(("POST","/v1/control/pause"),(method,path))
        self.assertEqual("RUNNING",body["expectedState"])
        self.assertTrue(body["commandId"])
        self.assertGreater(body["expiresAt"],int(MCP.time.time()*1000))


if __name__ == "__main__":
    unittest.main()
