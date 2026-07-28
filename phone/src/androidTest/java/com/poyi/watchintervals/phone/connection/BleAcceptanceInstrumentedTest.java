package com.poyi.watchintervals.phone.connection;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BleAcceptanceInstrumentedTest {
    private static final String TAG="BleAcceptance";
    @Test public void reconnectTenTimesAndServeOneHundredEncryptedRequests()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();WatchConnectionManager manager=WatchConnectionManager.get(context);assertTrue("secure pairing missing",manager.identity().isPaired());
        for(int cycle=0;cycle<10;cycle++){manager.disconnect();manager.connect().get(30,TimeUnit.SECONDS);assertEquals(TransportType.BLE,manager.snapshot().primaryTransport);}
        String expected=manager.identity().watchDeviceId();for(int request=0;request<100;request++){JSONObject status=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L));assertEquals(expected,status.optString("deviceId"));}
        JSONObject before=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L));long rejectedBefore=before.getJSONObject("bleSecurity").optLong("replayRejectedCount");assertTrue(manager.replayLastBleMessageForDiagnostics());Thread.sleep(1000L);JSONObject after=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L));assertTrue(after.getJSONObject("bleSecurity").optLong("replayRejectedCount")>rejectedBefore);
    }

    @Test public void realWorkoutRepeatedPauseResumeIsIdempotent()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();WatchConnectionManager manager=WatchConnectionManager.get(context);manager.disconnect();manager.connect().get(30,TimeUnit.SECONDS);
        int historyBefore=new JSONArray(manager.requestBlocking("GET","/v1/history","",15_000L)).length();
        command(manager,"start",UUID.randomUUID().toString(),"STOPPED");waitForState(manager,"RUNNING");Thread.sleep(2500L);
        String pauseId=UUID.randomUUID().toString();JSONObject firstPause=command(manager,"pause",pauseId,"RUNNING");JSONObject duplicatePause=command(manager,"pause",pauseId,"RUNNING");assertTrue(firstPause.optBoolean("accepted"));assertTrue(duplicatePause.optBoolean("duplicate"));waitForState(manager,"PAUSED");
        String resumeId=UUID.randomUUID().toString();JSONObject firstResume=command(manager,"resume",resumeId,"PAUSED");JSONObject duplicateResume=command(manager,"resume",resumeId,"PAUSED");assertTrue(firstResume.optBoolean("accepted"));assertTrue(duplicateResume.optBoolean("duplicate"));waitForState(manager,"RUNNING");Thread.sleep(2500L);
        command(manager,"stop",UUID.randomUUID().toString(),"RUNNING");waitForState(manager,"STOPPED");Thread.sleep(1000L);
        int historyAfter=new JSONArray(manager.requestBlocking("GET","/v1/history","",15_000L)).length();assertEquals("workout should be saved exactly once",historyBefore+1,historyAfter);
    }

    @Test public void fifteenMinuteScreenOffBleWorkoutGate()throws Exception{
        Context context=ApplicationProvider.getApplicationContext();WatchConnectionManager manager=WatchConnectionManager.get(context);manager.disconnect();manager.connect().get(30,TimeUnit.SECONDS);assertEquals(TransportType.BLE,manager.snapshot().primaryTransport);
        Log.i(TAG,"power_gate_ready disable_wifi_and_adb_now");Thread.sleep(15_000L);
        command(manager,"start",UUID.randomUUID().toString(),"STOPPED");waitForState(manager,"RUNNING");long started=SystemClock.elapsedRealtime(),deadline=started+15*60_000L,nextToggle=started+3*60_000L;int statusRequests=0,toggleCycles=0;
        try{
            while(SystemClock.elapsedRealtime()<deadline){
                JSONObject status=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L));assertTrue(status.optBoolean("activeSession"));statusRequests++;
                if(SystemClock.elapsedRealtime()>=nextToggle){String pauseId=UUID.randomUUID().toString();command(manager,"pause",pauseId,"RUNNING");assertTrue(command(manager,"pause",pauseId,"RUNNING").optBoolean("duplicate"));waitForState(manager,"PAUSED");Thread.sleep(1000L);String resumeId=UUID.randomUUID().toString();command(manager,"resume",resumeId,"PAUSED");assertTrue(command(manager,"resume",resumeId,"PAUSED").optBoolean("duplicate"));waitForState(manager,"RUNNING");toggleCycles++;nextToggle+=3*60_000L;}
                Thread.sleep(10_000L);
            }
        }finally{JSONObject state=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L));if("PAUSED".equals(state.optString("sessionState")))command(manager,"resume",UUID.randomUUID().toString(),"PAUSED");command(manager,"stop",UUID.randomUUID().toString(),"RUNNING");waitForState(manager,"STOPPED");}
        Log.i(TAG,"power_gate_complete statusRequests="+statusRequests+" toggleCycles="+toggleCycles);assertTrue(statusRequests>=80);assertTrue(toggleCycles>=4);
    }

    private static JSONObject command(WatchConnectionManager manager,String action,String id,String expected)throws Exception{return new JSONObject(manager.requestBlocking("POST","/v1/control/"+action,new JSONObject().put("commandId",id).put("expectedState",expected).put("expiresAt",System.currentTimeMillis()+30_000L).toString(),30_000L));}
    private static void waitForState(WatchConnectionManager manager,String expected)throws Exception{long deadline=System.currentTimeMillis()+10_000L;String actual="";while(System.currentTimeMillis()<deadline){actual=new JSONObject(manager.requestBlocking("GET","/v1/status","",15_000L)).optString("sessionState");if(expected.equals(actual))return;Thread.sleep(200L);}fail("expected "+expected+" but was "+actual);}
}
