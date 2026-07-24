package com.poyi.watchintervals;

import org.json.JSONException;
import org.json.JSONObject;

public final class Stage {
    public enum Kind { RUN, WALK, REST }
    public enum Unit { DISTANCE, TIME }

    public final Kind kind;
    public final Unit unit;
    public final int target;

    public Stage(Kind kind, Unit unit, int target) {
        this.kind = kind;
        this.unit = unit;
        this.target = Math.max(1, target);
    }

    public String name() {
        switch (kind) {
            case WALK: return "快走";
            case REST: return "休息";
            default: return "跑步";
        }
    }

    public String targetText() {
        if (unit == Unit.TIME) {
            int minutes = target / 60;
            int seconds = target % 60;
            return minutes > 0 ? minutes + "分" + (seconds > 0 ? seconds + "秒" : "") : seconds + "秒";
        }
        return target >= 1000 && target % 1000 == 0 ? (target / 1000) + "公里" : target + "米";
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("kind", kind.name()).put("unit", unit.name()).put("target", target);
    }

    static Stage fromJson(JSONObject json) throws JSONException {
        return new Stage(Kind.valueOf(json.getString("kind")), Unit.valueOf(json.getString("unit")), json.getInt("target"));
    }
}
