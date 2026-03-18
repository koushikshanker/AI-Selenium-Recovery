package com.ai.selenium.recovery.agent;

import com.ai.selenium.recovery.annotations.AIExtract;
import com.ai.selenium.recovery.annotations.AIRecover;
import com.ai.selenium.recovery.core.DataStore;
import com.ai.selenium.recovery.core.DriverManager;
import com.ai.selenium.recovery.core.LogUtil;
import com.ai.selenium.recovery.orchestrator.PlaywrightRecoveryOrchestrator;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Static helper called by {@link AIRecoveryAdvice} when an {@code @AIRecover} method throws.
 *
 * <p>Builds a recovery prompt from the annotation value + method parameter values,
 * then delegates to {@link PlaywrightRecoveryOrchestrator} for AI-powered recovery.
 */
public class AIRecoveryHandler {

    private AIRecoveryHandler() {}

    /**
     * Attempt AI-powered recovery for a failed method.
     * Called by ByteBuddy Advice — do NOT call directly.
     *
     * @param method the method that threw
     * @param args   the method arguments (for building the recovery prompt)
     * @param error  the exception that was thrown
     * @return true if recovery succeeded, false otherwise
     */
    public static boolean attemptRecovery(Method method, Object[] args, Exception error) {
        AIRecover annotation = method.getAnnotation(AIRecover.class);
        if (annotation == null) return false;

        WebDriver driver;
        try {
            driver = DriverManager.getDriver();
        } catch (Exception e) {
            LogUtil.info("[AIRecovery] No driver available, skipping recovery");
            return false;
        }

        if (driver == null) {
            LogUtil.info("[AIRecovery] Driver is null, skipping recovery");
            return false;
        }

        String prompt = buildPrompt(annotation.value(), method, args);
        LogUtil.info("[AIRecovery] Selenium failed, attempting recovery: " + error.getMessage());

        // Build the orchestrator
        PlaywrightRecoveryOrchestrator orchestrator = PlaywrightRecoveryOrchestrator
                .on(driver)
                .intent(prompt)
                .error(error);

        // If extraction is needed, configure it before recover()
        AIExtract[] extracts = annotation.extract();
        if (extracts.length > 0) {
            String[] keys = new String[extracts.length];
            String[] prompts = new String[extracts.length];
            for (int i = 0; i < extracts.length; i++) {
                keys[i] = extracts[i].key();
                prompts[i] = extracts[i].prompt();
            }
            orchestrator.extractAfterRecovery(keys, prompts);
        }

        boolean recovered = orchestrator.recover();

        if (recovered) {
            LogUtil.info("[AIRecovery] Recovery succeeded for: " + annotation.value());

            // Save extracted data to DataStore
            if (extracts.length > 0) {
                Map<String, String> extractedData = orchestrator.getExtractedData();
                for (Map.Entry<String, String> entry : extractedData.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        DataStore.saveData(entry.getKey(), entry.getValue());
                    } else {
                        LogUtil.info("[AIRecovery] Skipped empty value for key: " + entry.getKey());
                    }
                }
            }
        } else {
            LogUtil.info("[AIRecovery] Recovery failed for: " + annotation.value());
        }

        return recovered;
    }

    /**
     * Build the recovery prompt from the annotation value and method parameter values.
     *
     * <p>Supports two modes:
     * <ul>
     *   <li>Placeholders: {@code @AIRecover("Enter name '{0}' and card '{1}'")} — values substituted inline</li>
     *   <li>Auto-append: {@code @AIRecover("Fill payment form")} — parameter values appended at the end</li>
     * </ul>
     */
    private static String buildPrompt(String intent, Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return intent;
        }

        // If the prompt contains {0}, {1}, etc. — substitute inline
        if (intent.contains("{0}")) {
            String result = intent;
            for (int i = 0; i < args.length; i++) {
                result = result.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return result;
        }

        // Otherwise, auto-append parameter names and values
        StringBuilder promptBuilder = new StringBuilder(intent);
        Parameter[] params = method.getParameters();
        promptBuilder.append("\nParameter values: ");

        for (int i = 0; i < args.length; i++) {
            if (i > 0) promptBuilder.append(", ");
            String name = params.length > i ? params[i].getName() : "arg" + i;
            promptBuilder.append(name).append("='").append(args[i]).append("'");
        }

        return promptBuilder.toString();
    }
}
