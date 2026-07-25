package com.poyi.watchintervals.phone.connection;

import org.json.JSONObject;

public final class ResponseEnvelope {
    public final String messageId, replyTo, body;
    public final int status;

    public ResponseEnvelope(String messageId,String replyTo,int status,String body) {
        this.messageId=messageId;this.replyTo=replyTo;this.status=status;this.body=body==null?"":body;
    }

    public static ResponseEnvelope fromJson(JSONObject value) {
        JSONObject payload=value.optJSONObject("payload");
        return new ResponseEnvelope(value.optString("messageId"),value.optString("replyTo"),
                payload==null?500:payload.optInt("status",500),payload==null?"":payload.optString("body"));
    }
}
