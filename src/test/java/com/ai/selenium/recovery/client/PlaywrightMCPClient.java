package com.ai.selenium.recovery.client;

import com.ai.selenium.recovery.core.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-level client for Playwright MCP browser automation tools.
 * Connects to an existing Chrome browser via CDP and provides typed methods
 * for common browser operations (snapshot, click, type, navigate, etc.).
 */
public class PlaywrightMCPClient implements AutoCloseable {

    private final MCPJsonRpcClient mcpClient;

    private PlaywrightMCPClient(MCPJsonRpcClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Connect to an existing Chrome browser via CDP endpoint.
     * Starts the Playwright MCP server as a child process connected to the given Chrome.
     *
     * @param cdpEndpoint CDP endpoint URL (e.g., "http://localhost:9222")
     * @return Connected PlaywrightMCPClient
     */
    public static PlaywrightMCPClient connectToCDP(String cdpEndpoint) throws IOException {
        return connectToCDP(cdpEndpoint, 30);
    }

    /**
     * Connect to an existing Chrome browser via CDP endpoint with custom timeout.
     *
     * @param cdpEndpoint    CDP endpoint URL
     * @param timeoutSeconds Timeout for each MCP operation
     * @return Connected PlaywrightMCPClient
     */
    public static PlaywrightMCPClient connectToCDP(String cdpEndpoint, int timeoutSeconds) throws IOException {
        LogUtil.info("[PlaywrightMCP] Connecting to CDP endpoint: " + cdpEndpoint);

        MCPJsonRpcClient client = new MCPJsonRpcClient(
                timeoutSeconds,
                "npx", "@playwright/mcp@latest", "--cdp-endpoint", cdpEndpoint
        );
        client.initialize();

        LogUtil.info("[PlaywrightMCP] Connected to browser via CDP");
        return new PlaywrightMCPClient(client);
    }

    /**
     * Capture an accessibility snapshot of the current page.
     * Returns the page structure as an accessibility tree with element refs.
     */
    public String snapshot() throws IOException {
        JsonObject result = mcpClient.callTool("browser_snapshot", Map.of());
        return extractTextContent(result);
    }

    /**
     * Wait for specific text to appear on the page.
     */
    public String waitForText(String text) throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("text", text);
        JsonObject result = mcpClient.callTool("browser_wait_for", args);
        return extractTextContent(result);
    }

    /**
     * Execute any Playwright MCP tool by name with arbitrary arguments.
     * Use this for tools not wrapped by typed methods above.
     */
    public String callTool(String toolName, Map<String, Object> arguments) throws IOException {
        JsonObject result = mcpClient.callTool(toolName, arguments);
        return extractTextContent(result);
    }

    private String extractTextContent(JsonObject result) {
        if (result == null) return "";
        JsonArray content = result.getAsJsonArray("content");
        if (content == null || content.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (JsonElement el : content) {
            JsonObject item = el.getAsJsonObject();
            if (item.has("type") && "text".equals(item.get("type").getAsString())) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(item.get("text").getAsString());
            }
        }
        return sb.toString();
    }

    @Override
    public void close() {
        LogUtil.info("[PlaywrightMCP] Closing connection");
        mcpClient.close();
    }
}
