package com.example.helloworld;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// HTTP请求日志记录器（线程安全，最多保留10条）
public class HttpLogger {
    private static final int MAX_ENTRIES = 10;
    private static final List<HttpLogEntry> logs = new CopyOnWriteArrayList<>();

    public static class HttpLogEntry {
        public final long timestamp;
        public final String method;   // GET/POST
        public final String url;
        public final int responseCode;
        public final long durationMs; // 耗时毫秒
        public final String error;

        public HttpLogEntry(String method, String url, int responseCode, long durationMs, String error) {
            this.timestamp = System.currentTimeMillis();
            this.method = method;
            this.url = url;
            this.responseCode = responseCode;
            this.durationMs = durationMs;
            this.error = error;
        }
    }

    public static synchronized void log(String method, String url, int responseCode, long durationMs, String error) {
        if (logs.size() >= MAX_ENTRIES) {
            logs.remove(0);
        }
        logs.add(new HttpLogEntry(method, url, responseCode, durationMs, error));
    }

    public static List<HttpLogEntry> getLogs() {
        List<HttpLogEntry> result = new ArrayList<>(logs);
        // 倒序
        List<HttpLogEntry> reversed = new ArrayList<>();
        for (int i = result.size() - 1; i >= 0; i--) {
            reversed.add(result.get(i));
        }
        return reversed;
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
