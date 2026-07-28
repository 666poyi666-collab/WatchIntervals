package com.poyi.watchintervals.phone.connection.ble;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class BleProtocolCodecTest {
    @Test public void roundTripsAcrossDefaultMtuFrames(){String id=UUID.randomUUID().toString();byte[] payload=new byte[2048];for(int i=0;i<payload.length;i++)payload[i]=(byte)(i%251);List<byte[]> frames=BleProtocolCodec.encode(id,payload,23);assertTrue(frames.size()>100);for(byte[] frame:frames)assertTrue(frame.length<=20);BleProtocolCodec.Assembler assembler=new BleProtocolCodec.Assembler();BleProtocolCodec.Message result=null;for(byte[] frame:frames){BleProtocolCodec.Message next=assembler.accept(frame,1000);if(next!=null)result=next;}assertNotNull(result);assertArrayEquals(payload,result.payload);}
    @Test public void acceptsOutOfOrderFramesOnlyOnce(){String id=UUID.randomUUID().toString();List<byte[]> frames=BleProtocolCodec.encode(id,"hello bluetooth".getBytes(StandardCharsets.UTF_8),30);Collections.reverse(frames);BleProtocolCodec.Assembler assembler=new BleProtocolCodec.Assembler();BleProtocolCodec.Message result=null;for(byte[] frame:frames){BleProtocolCodec.Message next=assembler.accept(frame,1000);if(next!=null)result=next;}assertNotNull(result);assertEquals("hello bluetooth",new String(result.payload,StandardCharsets.UTF_8));assertNull(assembler.accept(frames.get(0),1001));}
    @Test public void capsAttributeValueAtGattMaximum(){for(byte[] frame:BleProtocolCodec.encode(UUID.randomUUID().toString(),new byte[4096],517))assertTrue(frame.length<=512);}
    @Test(expected=IllegalArgumentException.class) public void rejectsOversizedMessage(){BleProtocolCodec.encode(UUID.randomUUID().toString(),new byte[BleProtocolCodec.MAX_MESSAGE_BYTES+1],247);}
}
