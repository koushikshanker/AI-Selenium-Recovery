package com.ai.selenium.recovery.client;

import com.ai.selenium.recovery.core.LogUtil;
import com.google.gson.*;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Low-level MCP (Model Context Protocol) client using JSON-RPC 2.0 over stdio.
 * Communicates with MCP servers by spawning them as child processes and
 * exchanging newline-delimited JSON messages via stdin/stdout.
 */
public class MCPJsonRpcClient implements AutoCloseable {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final int timeoutSeconds;

    public MCPJsonRpcClient(int timeoutSeconds, String... command) throws IOException {
        this.timeoutSeconds = timeoutSeconds;
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        this.process = pb.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        // Drain stderr in background to prevent blocking
        Thread stderrDrain = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    LogUtil.debug("[MCP stderr] " + line);
                }
            } catch (IOException ignored) {
            }
        });
        stderrDrain.setDaemon(true);
        stderrDrain.start();
    }

    public MCPJsonRpcClient(String... command) throws IOException {
        this(DEFAULT_TIMEOUT_SECONDS, command);
    }

    /**
     * Perform MCP initialization handshake.
     * Must be called before any tool calls.
     */
    public JsonObject initialize() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "ai-selenium-recovery", "version", "1.0.0"));

        JsonObject result = sendRequest("initialize", params);

        // Send initialized notification (no response expected)
        sendNotification("notifications/initialized", Map.of());
        LogUtil.info("[MCP] Initialized successfully");

        return result;
    }

    /**
     * Call an MCP tool and return the result.
     *
     * @param name      Tool name (e.g., "browser_snapshot")
     * @param arguments Tool arguments as a map
     * @return The result JsonObject from the server
     */
    public JsonObject callTool(String name, Map<String, Object> arguments) throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments != null ? arguments : Map.of());
        return sendRequest("tools/call", params);
    }

    private JsonObject sendRequest(String method, Map<String, Object> params) throws IOException {
        int id = requestId.incrementAndGet();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        String json = GSON.toJson(request);
        LogUtil.info("[MCP] >> " + method + " (id=" + id + ")");

        synchronized (writer) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }

        return readResponse(id);
    }

    private void sendNotification(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);

        String json = GSON.toJson(notification);

        synchronized (writer) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    private JsonObject readResponse(int expectedId) throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonObject> future = executor.submit(() -> {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        throw new IOException("MCP server closed connection unexpectedly");
                    }
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // Skip non-JSON lines (server log messages etc.)
                    if (!line.startsWith("{")) continue;

                    try {
                        JsonObject response = JsonParser.parseString(line).getAsJsonObject();

                        // Skip notifications (no "id" field)
                        if (!response.has("id") || response.get("id").isJsonNull()) continue;

                        if (response.get("id").getAsInt() == expectedId) {
                            if (response.has("error") && !response.get("error").isJsonNull()) {
                                JsonObject error = response.getAsJsonObject("error");
                                throw new IOException("MCP error [" + error.get("code") + "]: "
                                        + error.get("message").getAsString());
                            }
                            return response.has("result") ? response.getAsJsonObject("result") : new JsonObject();
                        }
                    } catch (JsonSyntaxException e) {
                        LogUtil.debug("[MCP] Skipping non-JSON line: " + line);
                    }
                }
            });

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("MCP response timeout after " + timeoutSeconds + "s for request id=" + expectedId);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new IOException("MCP read failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP read interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public void close() {
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {
        }
        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
