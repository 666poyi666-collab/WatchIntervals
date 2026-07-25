package com.poyi.watchintervals.phone.connection;

import java.util.UUID;
import org.json.JSONObject;

public final class RequestEnvelope {
    public final String messageId, method, path, body;
    public final long createdAt, expiresAt;

    public RequestEnvelope(String messageId, String method, String path, String body, long createdAt, long expiresAt) {
        this.messageId=messageId;this.method=method;this.path=path;this.body=body==null?"":body;
        this.createdAt=createdAt;this.expiresAt=expiresAt;
    }

    public static RequestEnvelope create(String method,String path,String body,long ttlMillis) {
        long now=System.currentTimeMillis();
        return new RequestEnvelope(UUID.randomUUID().toString(),method,path,body,now,ttlMillis<=0?0L:now+ttlMillis);
    }

    public JSONObject toJson() throws Exception {
        return new JSONObject().put("protocolVersion",1).put("messageId",messageId).put("type","REQUEST")
                .put("createdAt",createdAt).put("expiresAt",expiresAt)
                .put("payload",new JSONObject().put("method",method).put("path",path).put("body",body));
    }
}
