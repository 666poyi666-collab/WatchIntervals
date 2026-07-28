package com.poyi.watchintervals.phone.connection;

import java.util.concurrent.CompletableFuture;

public interface WatchTransport {
    interface EventListener { void onEvent(ResponseEnvelope event); }
    TransportType type();
    boolean isAvailable();
    CompletableFuture<TransportSession> connect();
    CompletableFuture<ResponseEnvelope> request(RequestEnvelope request);
    void subscribe(EventListener listener);
    void disconnect();
}
