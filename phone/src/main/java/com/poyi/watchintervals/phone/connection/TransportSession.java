package com.poyi.watchintervals.phone.connection;

public final class TransportSession {
    public final TransportType type; public final String deviceId; public final int mtu;
    public TransportSession(TransportType type,String deviceId,int mtu){this.type=type;this.deviceId=deviceId;this.mtu=mtu;}
}
