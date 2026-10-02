package net.eyzencore.link.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.eyzencore.link.EyzencoreLinkPlugin;
import net.eyzencore.link.api.dto.LinkRequest;
import net.eyzencore.link.api.dto.StatsBatchRequest;
import net.eyzencore.link.api.dto.UnlinkRequest;
import net.eyzencore.link.api.dto.WhitelistResponse;
import net.eyzencore.link.config.PluginConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class ApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long MIN_RETRY_GAP_MS = 30_000L;

    private final EyzencoreLinkPlugin plugin;
    private final PluginConfig config;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final AtomicLong nextRetryAtMs = new AtomicLong(0L);
    private final AtomicLong backoffMs = new AtomicLong(30_000L);
    private volatile boolean tokenWarned;

    public ApiClient(EyzencoreLinkPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletableFuture<ApiResponse> link(LinkRequest request) {
        return postJson("/api/plugin/link", gson.toJson(request));
    }

    public CompletableFuture<ApiResponse> unlink(UnlinkRequest request) {
        return postJson("/api/plugin/unlink", gson.toJson(request));
    }

    public CompletableFuture<ApiResponse> pushStats(StatsBatchRequest request) {
        return postJson("/api/plugin/stats", gson.toJson(request));
    }

    public CompletableFuture<WhitelistResponse> fetchWhitelist() {
        return getJson("/api/plugin/whitelist").thenApply(response -> {
            if (!response.ok()) {
                return null;
            }
            try {
                return gson.fromJson(response.body(), WhitelistResponse.class);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse whitelist response", ex);
                return null;
            }
        });
    }

    public JsonObject parseJson(String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        return gson.fromJson(body, JsonObject.class);
    }

    public Gson gson() {
        return gson;
    }

    public void close() {
        // HttpClient has no close in older APIs; nothing to do.
    }

    private CompletableFuture<ApiResponse> postJson(String path, String jsonBody) {
        if (!canAttempt()) {
            return CompletableFuture.completedFuture(new ApiResponse(0, "retry_backoff", false));
        }
        HttpRequest request = baseRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(request);
    }

    private CompletableFuture<ApiResponse> getJson(String path) {
        if (!canAttempt()) {
            return CompletableFuture.completedFuture(new ApiResponse(0, "retry_backoff", false));
        }
        HttpRequest request = baseRequest(path).GET().build();
        return send(request);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + config.token())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "EyzencoreLink/" + plugin.getPluginMeta().getVersion());
    }

    private CompletableFuture<ApiResponse> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::toResponse)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    plugin.getLogger().log(Level.WARNING, "API request failed: " + cause.getMessage());
                    registerFailure();
                    return new ApiResponse(0, cause.getMessage() == null ? "error" : cause.getMessage(), false);
                });
    }

    private ApiResponse toResponse(HttpResponse<String> response) {
        int code = response.statusCode();
        boolean ok = code >= 200 && code < 300;
        ApiResponse apiResponse = new ApiResponse(code, response.body(), ok);

        if (apiResponse.unauthorized()) {
            if (!tokenWarned) {
                tokenWarned = true;
                plugin.getLogger().severe("API token rejected (401). Check api.token in config.yml. Retries paused.");
            }
            // Pause retries for unauthorized so we don't spam.
            nextRetryAtMs.set(System.currentTimeMillis() + 5 * 60_000L);
            return apiResponse;
        }

        if (ok) {
            tokenWarned = false;
            backoffMs.set(30_000L);
            nextRetryAtMs.set(0L);
            return apiResponse;
        }

        if (apiResponse.serverError()) {
            registerFailure();
        }
        return apiResponse;
    }

    private boolean canAttempt() {
        long now = System.currentTimeMillis();
        long next = nextRetryAtMs.get();
        return now >= next;
    }

    private void registerFailure() {
        long delay = Math.min(5 * 60_000L, Math.max(MIN_RETRY_GAP_MS, backoffMs.get()));
        nextRetryAtMs.set(System.currentTimeMillis() + delay);
        backoffMs.set(Math.min(5 * 60_000L, delay * 2));
    }
}
