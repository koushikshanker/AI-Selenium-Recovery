package com.ai.selenium.recovery.core;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Thread-safe WebDriver manager using ThreadLocal.
 * Configures Chrome with remote debugging for AI recovery.
 * Uses Selenium Manager (built into Selenium 4.27+) for automatic
 * browser/driver resolution — no external WebDriverManager needed.
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {}

    /**
     * Initialize a Chrome WebDriver with remote debugging enabled.
     */
    public static WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--ignore-ssl-errors=yes");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-dev-shm-usage");

        // Enable remote debugging so Playwright MCP can connect to the same browser
        String remoteDebugging = PropertyUtils.get("ENABLE_REMOTE_DEBUGGING", "true");
        if ("true".equalsIgnoreCase(remoteDebugging)) {
            String port = PropertyUtils.get("ENABLE_REMOTE_DEBUGGING_PORT", "9222");
            options.addArguments("--remote-debugging-port=" + port);
            LogUtil.info("[DriverManager] Chrome remote debugging enabled on port " + port);
        }

        boolean headless = "true".equalsIgnoreCase(PropertyUtils.get("HEADLESS_MODE", "false"));
        if (headless) {
            options.addArguments("--headless=new");
        }

        WebDriver driver = new ChromeDriver(options);
        DRIVER.set(driver);
        return driver;
    }

    /**
     * Get the current thread's WebDriver instance.
     */
    public static WebDriver getDriver() {
        return DRIVER.get();
    }

    /**
     * Quit the driver and clean up.
     */
    public static void quitDriver() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            driver.quit();
            DRIVER.remove();
        }
    }
}