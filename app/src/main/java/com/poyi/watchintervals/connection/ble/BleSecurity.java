package com.poyi.watchintervals.connection.ble;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class BleSecurity {
    private static final SecureRandom RANDOM=new SecureRandom();
    private BleSecurity(){}
    public static KeyPair keyPair()throws GeneralSecurityException{KeyPairGenerator value=KeyPairGenerator.getInstance("EC");value.initialize(256);return value.generateKeyPair();}
    public static byte[] sharedSecret(PrivateKey own,String peerPublic)throws GeneralSecurityException{PublicKey peer=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(decode(peerPublic)));KeyAgreement agreement=KeyAgreement.getInstance("ECDH");agreement.init(own);agreement.doPhase(peer,true);return agreement.generateSecret();}
    public static String publicKey(PublicKey value){return encode(value.getEncoded());}
    public static byte[] random(int length){byte[] value=new byte[length];RANDOM.nextBytes(value);return value;}
    public static long positiveSequence(){return 1L+(Math.abs(RANDOM.nextLong())%1_000_000_000L);}
    public static byte[] derive(byte[] key,String label,byte[]...parts)throws GeneralSecurityException{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));mac.update(label.getBytes(StandardCharsets.UTF_8));for(byte[] part:parts){byte[] value=part==null?new byte[0]:part;mac.update(ByteBuffer.allocate(4).putInt(value.length).array());mac.update(value);}return mac.doFinal();}
    public static byte[] hmac(byte[] key,String label,byte[]...parts)throws GeneralSecurityException{return derive(key,label,parts);}
    public static boolean same(byte[] a,byte[] b){return a!=null&&b!=null&&MessageDigest.isEqual(a,b);}
    public static byte[] encrypt(byte[] key,byte[] nonce,byte[] plaintext,byte[] aad)throws GeneralSecurityException{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));if(aad!=null)cipher.updateAAD(aad);return cipher.doFinal(plaintext);}
    public static byte[] decrypt(byte[] key,byte[] nonce,byte[] ciphertext,byte[] aad)throws GeneralSecurityException{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));if(aad!=null)cipher.updateAAD(aad);return cipher.doFinal(ciphertext);}
    public static String encode(byte[] value){return Base64.getEncoder().withoutPadding().encodeToString(value);}
    public static byte[] decode(String value){return Base64.getDecoder().decode(value);}
}
