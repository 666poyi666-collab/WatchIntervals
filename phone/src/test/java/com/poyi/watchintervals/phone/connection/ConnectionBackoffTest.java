package com.poyi.watchintervals.phone.connection;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ConnectionBackoffTest {
    @Test public void followsBoundedScheduleAndResets(){ConnectionBackoff backoff=new ConnectionBackoff();long[] expected={0L,1_000L,2_000L,5_000L,10_000L,30_000L,60_000L,300_000L,300_000L};for(long value:expected)assertEquals(value,backoff.nextDelayMillis());backoff.reset();assertEquals(0L,backoff.nextDelayMillis());}
}
