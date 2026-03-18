package com.ai.selenium.recovery.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for the AI Recovery framework.
 */
public final class LogUtil {

    private static final Logger LOG = LoggerFactory.getLogger("AIRecovery");

    private LogUtil() {}

    public static void info(String message) {
        LOG.info(message);
    }

    public static void error(String message) {
        LOG.error(message);
    }

    public static void error(String message, Throwable t) {
        LOG.error(message, t);
    }

    public static void debug(String message) {
        LOG.debug(message);
    }
}