package com.ai.selenium.recovery.client;

import com.ai.selenium.recovery.core.LogUtil;
import com.ai.selenium.recovery.core.PropertyUtils;
import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * AI-powered recovery client that analyzes browser state and determines
 * what Playwright MCP tool calls to execute for test recovery.
 * Uses Google Gemini API.
 */
public class PlaywrightAIRecoveryClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String agentModel;
    private final String apiKey;

    public PlaywrightAIRecoveryClient() {
        this.agentModel = PropertyUtils.get("AGENT_MODEL", "gemini-2.5-flash");
        this.apiKey = resolveApiKey();
    }

    public PlaywrightAIRecoveryClient(String apiKey) {
        this.agentModel = PropertyUtils.get("AGENT_MODEL", "gemini-2.5-flash");
        this.apiKey = apiKey;
    }

    /**
     * Result of a multi-turn recovery step.
     *
     * @param done    true if the AI determined the intent is already achieved
     * @param actions actions to execute on the CURRENT page (empty if done)
     */
    public record RecoveryPlan(boolean done, List<RecoveryAction> actions) {}

    /**
     * Multi-turn recovery: given current page state and action history, plan the next actions.
     * The AI only returns actions for elements visible on the CURRENT page snapshot,
     * avoiding stale refs from previous page states.
     *
     * @param intent        What the test step is trying to achieve
     * @param error         The original exception
     * @param pageSnapshot  Fresh accessibility snapshot of the current page
     * @param actionHistory Actions already executed (for context)
     * @return RecoveryPlan with done=true if intent is achieved, or actions for the current page
     */
    public RecoveryPlan planNextActions(String intent, String error, String pageSnapshot, List<String> actionHistory) throws IOException {
        String prompt = buildMultiTurnPrompt(intent, error, pageSnapshot, actionHistory);
        String aiResponse = callGemini(prompt);
        return parseRecoveryPlan(aiResponse);
    }

    /**
     * Extract specific data values from a page snapshot using AI.
     * Sends a single AI call to extract all requested values.
     *
     * @param pageSnapshot Fresh accessibility snapshot of the page
     * @param keys         DataStore keys to save under
     * @param prompts      What to extract for each key
     * @return Map of key -> extracted value
     */
    public LinkedHashMap<String, String> extractData(String pageSnapshot, String[] keys, String[] prompts) throws IOException {
        String prompt = buildExtractionPrompt(pageSnapshot, keys, prompts);
        String aiResponse = callGemini(prompt);
        return parseExtractionResponse(aiResponse, keys);
    }

    // ── Prompt Builders ──────────────────────────────────────────────

    private String buildMultiTurnPrompt(String intent, String error, String pageSnapshot, List<String> actionHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a test recovery agent. A Selenium test step failed. ");
        sb.append("Analyze the CURRENT browser state and determine what to do next.\n\n");
        sb.append("INTENT: ").append(intent).append("\n");
        sb.append("ORIGINAL ERROR: ").append(error).append("\n");

        if (actionHistory != null && !actionHistory.isEmpty()) {
            sb.append("\nACTIONS COMPLETED SO FAR:\n");
            for (int i = 0; i < actionHistory.size(); i++) {
                sb.append(i + 1).append(". ").append(actionHistory.get(i)).append("\n");
            }
            sb.append("\nThe page may have changed after the above actions. ");
            sb.append("Use ONLY ref values from the CURRENT snapshot below.\n");
        }

        sb.append("\nCURRENT PAGE STATE (accessibility snapshot):\n");
        sb.append(truncateSnapshot(pageSnapshot));

        sb.append("""

                AVAILABLE TOOLS (use exact names):
                1. browser_click - Click element. Args: {"ref": "<ref>", "element": "<description>"}
                2. browser_type - Type text. Args: {"ref": "<ref>", "text": "<text>"}
                3. browser_navigate - Go to URL. Args: {"url": "<url>"}
                4. browser_wait_for - Wait. Args: {"text": "<text>"} or {"textGone": "<text>"} or {"time": <seconds>}
                5. browser_press_key - Press key. Args: {"key": "<key_name>"}
                6. browser_evaluate - Run JS. Args: {"function": "() => { <js_code> }"} — MUST be an arrow function
                7. browser_select_option - Select dropdown. Args: {"ref": "<ref>", "element": "<desc>", "values": ["<value>"]}

                RULES:
                - Use "ref" values EXACTLY from the CURRENT snapshot above (e.g., "s1e15")
                - DO NOT use ref values from previous snapshots — they are STALE after page changes
                - Always include "element" description for click/select actions
                - browser_evaluate "function" MUST be an arrow function: "() => { code }" not just "code"
                - If the intent is ALREADY achieved based on the current page state, set status to "done"
                - Only return actions that can be performed on the CURRENT page
                - Do NOT plan actions for elements not yet visible

                RESPONSE FORMAT (JSON only):
                If intent is already achieved: {"status": "done", "actions": []}
                If more actions needed: {"status": "continue", "actions": [{"tool": "browser_click", "arguments": {"ref": "s1e15", "element": "Button"}}]}
                """);

        return sb.toString();
    }

    private String buildExtractionPrompt(String pageSnapshot, String[] keys, String[] prompts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a data extraction agent. From the page snapshot below, extract the requested values.\n\n");
        sb.append("PAGE STATE (accessibility snapshot):\n");
        sb.append(truncateSnapshot(pageSnapshot));
        sb.append("\n\nEXTRACT THESE VALUES:\n");

        for (int i = 0; i < keys.length; i++) {
            sb.append(i + 1).append(". Key \"").append(keys[i]).append("\": ").append(prompts[i]).append("\n");
        }

        sb.append("\nRULES:\n");
        sb.append("- Return ONLY a JSON object with the keys and extracted string values\n");
        sb.append("- If a value cannot be found, use empty string \"\"\n");
        sb.append("- Values must be strings, not numbers\n\n");
        sb.append("RESPONSE FORMAT (JSON object only):\n");
        sb.append("{\"").append(keys[0]).append("\": \"extracted value\"}");

        return sb.toString();
    }

    // ── Snapshot Pruning ─────────────────────────────────────────────

    private String truncateSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return "[no snapshot available]";
        }

        String[] lines = snapshot.split("\n");
        StringBuilder pruned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Always keep interactive elements
            if (line.contains("ref=")) {
                pruned.append(line).append("\n");
                continue;
            }

            // Keep structural/semantic lines
            if (trimmed.startsWith("- heading") || trimmed.startsWith("- navigation")
                    || trimmed.startsWith("- main") || trimmed.startsWith("- form")
                    || trimmed.startsWith("- dialog") || trimmed.startsWith("- banner")
                    || trimmed.startsWith("- contentinfo") || trimmed.startsWith("- region")
                    || trimmed.startsWith("- document") || trimmed.startsWith("- iframe")) {
                pruned.append(line).append("\n");
                continue;
            }

            // Drop long static text lines
            if (trimmed.startsWith("- text") || trimmed.startsWith("- paragraph")) {
                if (trimmed.length() > 100) continue;
            }

            pruned.append(line).append("\n");
        }

        String result = pruned.toString();
        if (result.length() > 50000) {
            return result.substring(0, 50000) + "\n... [truncated at 50K chars]";
        }

        LogUtil.info("[PlaywrightMCP] Snapshot: original=" + snapshot.length()
                + " chars, pruned=" + result.length() + " chars");
        return result;
    }

    // ── Gemini API ───────────────────────────────────────────────────

    private String callGemini(String prompt) throws IOException {
        LogUtil.info("[PlaywrightMCP] Calling Gemini (" + agentModel + ") for recovery analysis");

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + agentModel + ":generateContent?key=" + apiKey;

        JsonObject requestBody = new JsonObject();

        // System instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text",
                "You are a test recovery agent. You MUST respond with ONLY a JSON array of tool calls. "
                        + "No explanation, no markdown, no extra text.");
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);
        requestBody.add("systemInstruction", systemInstruction);

        // User content
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Generation config — force JSON output
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        generationConfig.addProperty("temperature", 0.1);
        requestBody.add("generationConfig", generationConfig);

        return sendHttpRequest(url, GSON.toJson(requestBody));
    }

    private String sendHttpRequest(String url, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Gemini API returned status " + response.statusCode() + ": " + response.body());
            }

            return extractGeminiResponseText(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini API call interrupted", e);
        }
    }

    private String extractGeminiResponseText(String responseBody) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
        if (json.has("candidates")) {
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        }
        return responseBody;
    }

    // ── Response Parsers ─────────────────────────────────────────────

    private RecoveryPlan parseRecoveryPlan(String aiResponse) {
        try {
            String jsonStr = aiResponse.trim();
            JsonElement parsed = JsonParser.parseString(jsonStr);

            if (parsed.isJsonArray()) {
                return parseActionsArray(parsed.getAsJsonArray());
            }

            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                String status = obj.has("status") ? obj.get("status").getAsString() : "continue";

                if ("done".equals(status)) {
                    return new RecoveryPlan(true, Collections.emptyList());
                }

                if (obj.has("actions")) {
                    return parseActionsArray(obj.getAsJsonArray("actions"));
                }

                return new RecoveryPlan(true, Collections.emptyList());
            }

            LogUtil.info("[PlaywrightMCP] Unexpected AI response format: " + jsonStr);
            return new RecoveryPlan(true, Collections.emptyList());

        } catch (Exception e) {
            LogUtil.info("[PlaywrightMCP] Failed to parse recovery plan: " + e.getMessage());
            LogUtil.info("[PlaywrightMCP] Raw AI response: " + aiResponse);
            return new RecoveryPlan(true, Collections.emptyList());
        }
    }

    private RecoveryPlan parseActionsArray(JsonArray actionsArray) {
        if (actionsArray.isEmpty()) {
            return new RecoveryPlan(true, Collections.emptyList());
        }

        List<RecoveryAction> actions = new ArrayList<>();
        for (JsonElement element : actionsArray) {
            JsonObject actionObj = element.getAsJsonObject();
            String tool = actionObj.get("tool").getAsString();
            JsonObject arguments = actionObj.has("arguments") ? actionObj.getAsJsonObject("arguments") : new JsonObject();
            actions.add(new RecoveryAction(tool, arguments));
        }

        return new RecoveryPlan(false, actions);
    }

    private LinkedHashMap<String, String> parseExtractionResponse(String aiResponse, String[] keys) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();

        try {
            String jsonStr = aiResponse.trim();
            if (!jsonStr.startsWith("{")) {
                int start = jsonStr.indexOf('{');
                int end = jsonStr.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1);
                }
            }

            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            for (String key : keys) {
                if (obj.has(key) && !obj.get(key).isJsonNull()) {
                    result.put(key, obj.get(key).getAsString());
                } else {
                    result.put(key, "");
                }
            }
        } catch (Exception e) {
            LogUtil.info("[PlaywrightMCP] Failed to parse extraction response: " + e.getMessage());
            for (String key : keys) {
                result.put(key, "");
            }
        }

        return result;
    }

    private String resolveApiKey() {
        String key = PropertyUtils.get("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) return key;

        key = PropertyUtils.get("AGENT_API_KEY");
        if (key != null && !key.isBlank()) return key;

        key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) return key;

        return null;
    }

    /**
     * Represents a single recovery action (Playwright MCP tool call).
     */
    public record RecoveryAction(String tool, JsonObject arguments) {

        public Map<String, Object> argumentsAsMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : arguments.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    JsonPrimitive prim = value.getAsJsonPrimitive();
                    if (prim.isNumber()) {
                        map.put(entry.getKey(), prim.getAsNumber());
                    } else if (prim.isBoolean()) {
                        map.put(entry.getKey(), prim.getAsBoolean());
                    } else {
                        map.put(entry.getKey(), prim.getAsString());
                    }
                } else if (value.isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement el : value.getAsJsonArray()) {
                        list.add(el.getAsString());
                    }
                    map.put(entry.getKey(), list);
                } else {
                    map.put(entry.getKey(), value.toString());
                }
            }
            return map;
        }

        @Override
        public String toString() {
            return tool + "(" + arguments + ")";
        }
    }
}
