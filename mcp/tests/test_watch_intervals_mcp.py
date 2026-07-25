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


if __name__ == "__main__":
    unittest.main()
