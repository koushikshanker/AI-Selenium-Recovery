package com.ai.selenium.recovery.orchestrator;

import com.ai.selenium.recovery.client.PlaywrightAIRecoveryClient;
import com.ai.selenium.recovery.client.PlaywrightMCPClient;
import com.ai.selenium.recovery.core.LogUtil;
import com.ai.selenium.recovery.core.PropertyUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.util.*;

/**
 * High-level orchestrator for AI-powered test recovery using Playwright MCP.
 *
 * <p>When a Selenium test step fails, this orchestrator:
 * <ol>
 *   <li>Connects Playwright MCP to the same Chrome browser via CDP</li>
 *   <li>Takes an accessibility snapshot of the current page</li>
 *   <li>Sends the snapshot + failure context to Google Gemini AI</li>
 *   <li>AI determines what Playwright tool calls to execute</li>
 *   <li>Executes each tool call to recover the test</li>
 *   <li>Validates the result and optionally extracts data</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * boolean recovered = PlaywrightRecoveryOrchestrator.on(driver)
 *     .intent("Click the submit button and verify payment confirmation")
 *     .error(e)
 *     .recover();
 * </pre>
 */
public class PlaywrightRecoveryOrchestrator {

    private final WebDriver driver;
    private String intent;
    private Exception error;
    private String validationText;
    private int maxRetries;
    private String[] extractKeys;
    private String[] extractPrompts;
    private final LinkedHashMap<String, String> extractedData = new LinkedHashMap<>();

    private PlaywrightRecoveryOrchestrator(WebDriver driver) {
        this.driver = driver;
        this.maxRetries = resolveMaxRetries();
    }

    public static PlaywrightRecoveryOrchestrator on(WebDriver driver) {
        return new PlaywrightRecoveryOrchestrator(driver);
    }

    public PlaywrightRecoveryOrchestrator intent(String intent) {
        this.intent = intent;
        return this;
    }

    public PlaywrightRecoveryOrchestrator error(Exception error) {
        this.error = error;
        return this;
    }

    public PlaywrightRecoveryOrchestrator validateText(String text) {
        this.validationText = text;
        return this;
    }

    public PlaywrightRecoveryOrchestrator maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public PlaywrightRecoveryOrchestrator extractAfterRecovery(String[] keys, String[] prompts) {
        this.extractKeys = keys;
        this.extractPrompts = prompts;
        return this;
    }

    public Map<String, String> getExtractedData() {
        return extractedData;
    }

    /**
     * Execute the recovery flow using a multi-turn observe-act loop.
     *
     * <p>Instead of planning all actions from a single snapshot, this method:
     * <ol>
     *   <li>Takes a fresh snapshot of the current page</li>
     *   <li>Asks the AI what actions to perform on THIS page</li>
     *   <li>Executes those actions (which may cause page transitions)</li>
     *   <li>Loops back to step 1 with a fresh snapshot of the NEW page</li>
     *   <li>Stops when the AI reports the intent is achieved, or max steps reached</li>
     * </ol>
     */
    public boolean recover() {
        if (intent == null || intent.isBlank()) {
            LogUtil.info("[PlaywrightRecovery] No intent provided, skipping recovery");
            return false;
        }

        String errorMessage = error != null
                ? error.getClass().getSimpleName() + ": " + error.getMessage()
                : "Unknown error";
        LogUtil.info("[PlaywrightRecovery] Starting recovery for: " + intent);
        LogUtil.info("[PlaywrightRecovery] Error: " + errorMessage);

        String cdpEndpoint = resolveCDPEndpoint();
        if (cdpEndpoint == null) {
            LogUtil.info("[PlaywrightRecovery] Could not resolve CDP endpoint. "
                    + "Ensure ENABLE_REMOTE_DEBUGGING=true in configuration.properties");
            return false;
        }

        int maxSteps = resolveMaxSteps();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            LogUtil.info("[PlaywrightRecovery] Attempt " + attempt + "/" + maxRetries);

            try (PlaywrightMCPClient mcp = PlaywrightMCPClient.connectToCDP(cdpEndpoint)) {
                PlaywrightAIRecoveryClient aiClient = new PlaywrightAIRecoveryClient();
                List<String> actionHistory = new ArrayList<>();
                boolean intentAchieved = false;

                // Observe-Act loop
                for (int step = 0; step < maxSteps; step++) {
                    String snapshot = mcp.snapshot();
                    LogUtil.info("[PlaywrightRecovery] Step " + (step + 1)
                            + " snapshot captured (" + snapshot.length() + " chars)");

                    PlaywrightAIRecoveryClient.RecoveryPlan plan =
                            aiClient.planNextActions(intent, errorMessage, snapshot, actionHistory);

                    if (plan.done()) {
                        LogUtil.info("[PlaywrightRecovery] AI reports intent achieved after "
                                + actionHistory.size() + " total actions");
                        intentAchieved = true;
                        break;
                    }

                    if (plan.actions().isEmpty()) {
                        LogUtil.info("[PlaywrightRecovery] AI returned no actions, stopping");
                        break;
                    }

                    LogUtil.info("[PlaywrightRecovery] AI suggested "
                            + plan.actions().size() + " actions for current page");

                    for (var action : plan.actions()) {
                        LogUtil.info("[PlaywrightRecovery] Executing: " + action);
                        try {
                            Map<String, Object> args = action.argumentsAsMap();
                            // Safety: Playwright MCP requires arrow functions for browser_evaluate
                            if ("browser_evaluate".equals(action.tool()) && args.containsKey("function")) {
                                String fn = String.valueOf(args.get("function"));
                                if (!fn.contains("=>") && !fn.trim().startsWith("function")) {
                                    args.put("function", "() => { " + fn + " }");
                                }
                            }
                            String result = mcp.callTool(action.tool(), args);
                            actionHistory.add(action + " -> success");
                            LogUtil.info("[PlaywrightRecovery] Action result: "
                                    + (result.length() > 200 ? result.substring(0, 200) + "..." : result));
                        } catch (IOException e) {
                            actionHistory.add(action + " -> FAILED: " + e.getMessage());
                            LogUtil.info("[PlaywrightRecovery] Action failed: " + e.getMessage());
                            break;
                        }
                    }
                }

                if (!intentAchieved && actionHistory.isEmpty()) {
                    LogUtil.info("[PlaywrightRecovery] No actions executed, retrying");
                    continue;
                }

                if (!intentAchieved) {
                    LogUtil.info("[PlaywrightRecovery] Max steps (" + maxSteps
                            + ") reached without achieving intent");
                }

                // Validate recovery
                if (validationText != null && !validationText.isBlank()) {
                    try {
                        mcp.waitForText(validationText);
                        LogUtil.info("[PlaywrightRecovery] Validation passed — text found: " + validationText);
                    } catch (IOException e) {
                        LogUtil.info("[PlaywrightRecovery] Validation failed — text not found: " + validationText);
                        continue;
                    }
                }

                // Extract data from page if requested
                if (extractKeys != null && extractKeys.length > 0) {
                    try {
                        String freshSnapshot = mcp.snapshot();
                        LogUtil.info("[PlaywrightRecovery] Fresh snapshot for data extraction ("
                                + freshSnapshot.length() + " chars)");

                        LinkedHashMap<String, String> data =
                                aiClient.extractData(freshSnapshot, extractKeys, extractPrompts);
                        extractedData.putAll(data);

                        for (Map.Entry<String, String> entry : data.entrySet()) {
                            LogUtil.info("[PlaywrightRecovery] Extracted: "
                                    + entry.getKey() + " = \"" + entry.getValue() + "\"");
                        }
                    } catch (IOException e) {
                        LogUtil.info("[PlaywrightRecovery] Data extraction failed: " + e.getMessage());
                    }
                }

                LogUtil.info("[PlaywrightRecovery] Recovery SUCCEEDED on attempt " + attempt);
                return true;

            } catch (IOException e) {
                LogUtil.info("[PlaywrightRecovery] Attempt " + attempt + " failed: " + e.getMessage());
            }
        }

        LogUtil.info("[PlaywrightRecovery] Recovery FAILED after " + maxRetries + " attempts");
        return false;
    }

    private String resolveCDPEndpoint() {
        // Strategy 1: Get from Chrome capabilities
        try {
            if (driver instanceof RemoteWebDriver remoteDriver) {
                Capabilities caps = remoteDriver.getCapabilities();

                @SuppressWarnings("unchecked")
                Map<String, Object> chromeOptions = (Map<String, Object>) caps.getCapability("goog:chromeOptions");
                if (chromeOptions != null && chromeOptions.containsKey("debuggerAddress")) {
                    String debuggerAddress = (String) chromeOptions.get("debuggerAddress");
                    String endpoint = "http://" + debuggerAddress;
                    LogUtil.info("[PlaywrightRecovery] CDP endpoint from capabilities: " + endpoint);
                    return endpoint;
                }
            }
        } catch (Exception e) {
            LogUtil.info("[PlaywrightRecovery] Could not get CDP from capabilities: " + e.getMessage());
        }

        // Strategy 2: Use configured port
        String remoteDebugging = PropertyUtils.get("ENABLE_REMOTE_DEBUGGING");
        if ("true".equalsIgnoreCase(remoteDebugging)) {
            String port = PropertyUtils.get("ENABLE_REMOTE_DEBUGGING_PORT");
            if (port != null && !port.isBlank()) {
                String endpoint = "http://localhost:" + port;
                LogUtil.info("[PlaywrightRecovery] CDP endpoint from config: " + endpoint);
                return endpoint;
            }
        }

        LogUtil.info("[PlaywrightRecovery] No CDP endpoint available");
        return null;
    }

    private int resolveMaxRetries() {
        try {
            String val = PropertyUtils.get("AGENT_MAX_RETRY");
            if (val != null && !val.isBlank()) return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        return 2;
    }

    private int resolveMaxSteps() {
        try {
            String val = PropertyUtils.get("AGENT_MAX_STEPS");
            if (val != null && !val.isBlank()) return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        return 10;
    }
}
