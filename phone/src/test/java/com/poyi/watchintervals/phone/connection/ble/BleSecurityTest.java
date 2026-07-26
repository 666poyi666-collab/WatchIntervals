package com.poyi.watchintervals.phone.connection.ble;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.json.JSONObject;
import org.junit.Test;

public class BleSecurityTest {
    @Test public void ecdhAndAesGcmRoundTrip()throws Exception{KeyPair a=BleSecurity.keyPair(),b=BleSecurity.keyPair();byte[] left=BleSecurity.sharedSecret(a.getPrivate(),BleSecurity.publicKey(b.getPublic())),right=BleSecurity.sharedSecret(b.getPrivate(),BleSecurity.publicKey(a.getPublic()));assertArrayEquals(left,right);byte[] key=BleSecurity.derive(left,"pair",new byte[]{1,2,3}),nonce=BleSecurity.random(12),plain="pairing-secret".getBytes(StandardCharsets.UTF_8),aad="devices".getBytes(StandardCharsets.UTF_8);assertArrayEquals(plain,BleSecurity.decrypt(key,nonce,BleSecurity.encrypt(key,nonce,plain,aad),aad));}
    @Test public void secureSessionRejectsReplay()throws Exception{byte[] key=BleSecurity.random(32);long clientStart=10,serverStart=90;BleSecureSession client=new BleSecureSession(key,"session",clientStart,serverStart),server=new BleSecureSession(key,"session",serverStart,clientStart);JSONObject sealed=client.seal("message",new byte[]{4,5,6});assertArrayEquals(new byte[]{4,5,6},server.open(sealed,System.currentTimeMillis()));try{server.open(sealed,System.currentTimeMillis());fail("replay accepted");}catch(java.security.GeneralSecurityException expected){assertEquals("replay_rejected",expected.getMessage());}}
    @Test public void authenticationProofIsBoundToChallenge()throws Exception{byte[] secret=BleSecurity.random(32),nonce=BleSecurity.random(16);byte[] proof=BleSecurity.hmac(secret,"client-auth","phone".getBytes(StandardCharsets.UTF_8),nonce);assertTrue(BleSecurity.same(proof,BleSecurity.hmac(secret,"client-auth","phone".getBytes(StandardCharsets.UTF_8),nonce)));nonce[0]^=1;assertFalse(BleSecurity.same(proof,BleSecurity.hmac(secret,"client-auth","phone".getBytes(StandardCharsets.UTF_8),nonce)));}
}
