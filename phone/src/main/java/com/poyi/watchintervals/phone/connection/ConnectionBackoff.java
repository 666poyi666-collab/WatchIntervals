package com.poyi.watchintervals.phone.connection;

public final class ConnectionBackoff {
    private static final long[] DELAYS={0L,1_000L,2_000L,5_000L,10_000L,30_000L,60_000L,300_000L};
    private int attempt;
    public long nextDelayMillis(){long value=DELAYS[Math.min(attempt,DELAYS.length-1)];if(attempt<DELAYS.length-1)attempt++;return value;}
    public void reset(){attempt=0;}
    public int attempt(){return attempt;}
}
