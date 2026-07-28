package com.poyi.watchintervals.connection.ble;

import java.util.UUID;

public final class BleUuids {
    private BleUuids() {}
    public static final UUID SERVICE=uuid(0x1000),DEVICE_INFO=uuid(0x1001),PAIRING=uuid(0x1002),CONTROL=uuid(0x1003),EVENTS=uuid(0x1004),SYNC_TX=uuid(0x1005),SYNC_RX=uuid(0x1006),LOCATION=uuid(0x1007),LAN_ENDPOINT=uuid(0x1008),HEARTBEAT=uuid(0x1009);
    public static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static UUID uuid(int value){return UUID.fromString(String.format("7b5e%04x-88b8-4e08-9b7d-4cd930f0c101",value));}
}
