package com.poyi.watchintervals.phone.connection.ble;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.json.JSONObject;

public final class BleSecureSession {
    private static final long MAX_CLOCK_SKEW_MS=120_000L;
    private final byte[] key;private final String sessionId;private long outboundSequence,inboundSequence;
    public BleSecureSession(byte[] key,String sessionId,long outboundStart,long inboundFloor){this.key=key.clone();this.sessionId=sessionId;outboundSequence=outboundStart;inboundSequence=inboundFloor;}
    public synchronized JSONObject seal(String messageId,byte[] plaintext)throws Exception{long sequence=++outboundSequence,createdAt=System.currentTimeMillis();byte[] nonce=BleSecurity.random(12),aad=aad(messageId,sequence,createdAt);byte[] ciphertext=BleSecurity.encrypt(key,nonce,plaintext,aad);return new JSONObject().put("protocolVersion",2).put("type","SECURE").put("sessionId",sessionId).put("messageId",messageId).put("sequence",sequence).put("createdAt",createdAt).put("nonce",BleSecurity.encode(nonce)).put("ciphertext",BleSecurity.encode(ciphertext));}
    public synchronized byte[] open(JSONObject value,long now)throws Exception{if(!"SECURE".equals(value.optString("type"))||!sessionId.equals(value.optString("sessionId")))throw new GeneralSecurityException("session_mismatch");long sequence=value.optLong("sequence",-1),createdAt=value.optLong("createdAt",0);if(sequence<=inboundSequence)throw new GeneralSecurityException("replay_rejected");if(Math.abs(now-createdAt)>MAX_CLOCK_SKEW_MS)throw new GeneralSecurityException("message_expired");String messageId=value.optString("messageId");byte[] plaintext=BleSecurity.decrypt(key,BleSecurity.decode(value.optString("nonce")),BleSecurity.decode(value.optString("ciphertext")),aad(messageId,sequence,createdAt));inboundSequence=sequence;return plaintext;}
    private byte[] aad(String messageId,long sequence,long createdAt){return (sessionId+"|"+messageId+"|"+sequence+"|"+createdAt).getBytes(StandardCharsets.UTF_8);}
}
