package com.automattic.simplenote.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

public class AppLog {
    private static final int LOG_MAX = 100;
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

    public static synchronized void add(Type type, String message) {
        String log;

        if (type == Type.ACCOUNT || type == Type.DEVICE) {
            log = message + "\n";
        } else {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            log = timestamp + " - " + type.toString() + ": " + message + "\n";
        }

        if (mQueue.size() == LOG_MAX) {
            mQueue.removeFirst();
        }
        mQueue.addLast(log);
    }

    public static synchronized String get() {
        StringBuilder queue = new StringBuilder();

        for (String entry : mQueue) {
            queue.append(entry);
        }

        return queue.toString();
    }
}
