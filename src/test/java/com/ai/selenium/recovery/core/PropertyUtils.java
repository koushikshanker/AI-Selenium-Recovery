package com.ai.selenium.recovery.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads configuration from configuration.properties at the project root.
 */
public final class PropertyUtils {

    private static final Properties PROPS = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("configuration.properties")) {
            PROPS.load(fis);
        } catch (IOException e) {
            LogUtil.error("Failed to load configuration.properties: " + e.getMessage());
        }
    }

    private PropertyUtils() {}

    public static String get(String key) {
        // Environment variable takes precedence
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) return envValue;

        // Then system property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;

        // Then config file
        return PROPS.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}