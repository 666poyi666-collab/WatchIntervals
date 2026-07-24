package com.poyi.watchintervals.phone;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class WatchClient {
    private final String baseUrl, code;
    WatchClient(String host, String code) { this.baseUrl = "http://" + host + ":8765"; this.code = code; }

    String get(String path) throws Exception { return request("GET", path, null); }
    String put(String path, String body) throws Exception { return request("PUT", path, body); }
    String post(String path) throws Exception { return request("POST", path, "{}"); }
    String post(String path, String body) throws Exception { return request("POST", path, body); }

    private String request(String method, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(4000); connection.setReadTimeout(6000); connection.setRequestMethod(method);
        connection.setRequestProperty("X-Pairing-Code", code); connection.setRequestProperty("Content-Type", "application/json");
        if (body != null) {
            connection.setDoOutput(true); byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length); try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        }
        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(status < 400 ? connection.getInputStream() : connection.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(); String line; while ((line = reader.readLine()) != null) result.append(line);
        if (status >= 400) throw new IllegalStateException("HTTP " + status + " " + result);
        return result.toString();
    }
}
