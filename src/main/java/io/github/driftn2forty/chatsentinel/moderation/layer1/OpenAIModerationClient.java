package io.github.driftn2forty.chatsentinel.moderation.layer1;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.driftn2forty.chatsentinel.moderation.ModerationResult;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import io.github.driftn2forty.chatsentinel.util.HttpUtil;
import io.github.driftn2forty.chatsentinel.util.RateLimiter;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OpenAIModerationClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double defaultThreshold;
    private final Map<String, Double> categoryThresholds;
    private final HttpUtil httpUtil;
    private final RateLimiter rateLimiter;
    private final DebugLogger logger;
    private final Gson gson = new Gson();

    public OpenAIModerationClient(String baseUrl, String apiKey, String model, double defaultThreshold, Map<String, Double> categoryThresholds, HttpUtil httpUtil, RateLimiter rateLimiter, DebugLogger logger) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.defaultThreshold = defaultThreshold;
        this.categoryThresholds = categoryThresholds;
        this.httpUtil = httpUtil;
        this.rateLimiter = rateLimiter;
        this.logger = logger;
    }

    public CompletableFuture<ModerationResult> moderate(String message, Duration timeout) {
        final long startTime = System.nanoTime();

        if (!rateLimiter.tryAcquire()) {
            logger.warn("OpenAIModerationClient", "Rate limit exceeded, allowing message through");
            return CompletableFuture.completedFuture(ModerationResult.allow(1, elapsed(startTime)));
        }

        final JsonObject requestBody = new JsonObject();
        requestBody.addProperty("input", message);
        if (model != null && !model.isEmpty()) {
            requestBody.addProperty("model", model);
        }

        final String url = baseUrl + "/v1/moderations";
        final String json = gson.toJson(requestBody);

        logger.debug("OpenAIModerationClient", "POST " + url + " body=" + json);

        return httpUtil.postJsonAsync(url, json, apiKey, timeout).thenApply(response -> parseResponse(response, startTime));
    }

    private ModerationResult parseResponse(HttpResponse<String> response, long startTime) {
        final long responseTime = elapsed(startTime);

        if (response.statusCode() != 200) {
            logger.error("OpenAIModerationClient", "API returned HTTP " + response.statusCode() + ": " + response.body());
            return ModerationResult.allow(1, responseTime);
        }

        logger.debug("OpenAIModerationClient", "Response: " + response.body());

        final JsonObject root = gson.fromJson(response.body(), JsonObject.class);
        final JsonArray results = root.getAsJsonArray("results");
        if (results == null || results.isEmpty()) {
            logger.warn("OpenAIModerationClient", "No results in moderation response");
            return ModerationResult.allow(1, responseTime);
        }

        final JsonObject result = results.get(0).getAsJsonObject();
        final boolean flagged = result.has("flagged") && result.get("flagged").getAsBoolean();

        if (!flagged) {
            return ModerationResult.allow(1, responseTime);
        }

        final List<String> categories = new ArrayList<>();
        double maxScore = 0.0;

        final JsonObject categoryScores = result.getAsJsonObject("category_scores");
        if (categoryScores != null) {
            for (final Map.Entry<String, JsonElement> entry : categoryScores.entrySet()) {
                final double score = entry.getValue().getAsDouble();
                final double catThreshold = categoryThresholds.getOrDefault(entry.getKey(), defaultThreshold);
                if (score >= catThreshold) {
                    categories.add(entry.getKey());
                }
                maxScore = Math.max(maxScore, score);
            }
        }

        if (categories.isEmpty()) {
            final JsonObject categoryFlags = result.getAsJsonObject("categories");
            if (categoryFlags != null) {
                for (final Map.Entry<String, JsonElement> entry : categoryFlags.entrySet()) {
                    if (entry.getValue().getAsBoolean()) {
                        categories.add(entry.getKey());
                    }
                }
            }
        }

        logger.info("OpenAIModerationClient", "Flagged categories=" + categories + " maxScore=" + String.format("%.4f", maxScore));
        return ModerationResult.escalate(1, categories, maxScore, responseTime);
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
