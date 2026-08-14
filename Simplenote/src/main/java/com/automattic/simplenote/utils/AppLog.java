package com.automattic.simplenote.utils;

import androidx.annotation.VisibleForTesting;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AppLog {
    private static final int LOG_MAX = 100;
    private static final Object LOCK = new Object();
    private static final Map<Type, String> mHeaders = new LinkedHashMap<>();
    private static final Deque<String> mQueue = new ArrayDeque<>(LOG_MAX);

    public enum Type {
        ACCOUNT,
        ACTION,
        AUTH,
        DEVICE,
        LAYOUT,
        NETWORK,
        SCREEN,
        SYNC,
        IMPORT,
        EDITOR
    }

    // Headers hold per-process context (device and account blocks) that must survive rotation,
    // so shared diagnostics keep their context after the 100-entry window turns over.
    public static void addHeader(Type type, String message) {
        synchronized (LOCK) {
            mHeaders.put(type, message + "\n");
        }
    }

    public static void add(Type type, String message) {
        String log;

        if (type == Type.ACCOUNT || type == Type.DEVICE) {
            log = message + "\n";
        } else {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            log = timestamp + " - " + type.toString() + ": " + message + "\n";
        }

        synchronized (LOCK) {
            if (mQueue.size() == LOG_MAX) {
                mQueue.removeFirst();
            }
            mQueue.addLast(log);
        }
    }

    public static String get() {
        StringBuilder queue = new StringBuilder();

        synchronized (LOCK) {
            for (String header : mHeaders.values()) {
                queue.append(header);
            }

            for (String entry : mQueue) {
                queue.append(entry);
            }
        }

        return queue.toString();
    }

    @VisibleForTesting
    static void clearForTesting() {
        synchronized (LOCK) {
            mHeaders.clear();
            mQueue.clear();
        }
    }
}
