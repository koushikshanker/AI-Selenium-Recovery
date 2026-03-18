package com.ai.selenium.recovery.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe key-value data store for sharing data between test steps.
 * Used by AI recovery to save extracted values (e.g., amounts, booking IDs).
 */
public final class DataStore {

    private static final ConcurrentHashMap<String, String> STORE = new ConcurrentHashMap<>();

    private DataStore() {}

    public static void saveData(String key, String value) {
        STORE.put(key, value);
        LogUtil.info("[DataStore] Saved: " + key + " = \"" + value + "\"");
    }

    public static String getData(String key) {
        return STORE.getOrDefault(key, "");
    }

    public static void clear() {
        STORE.clear();
    }
}