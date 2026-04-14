package io.github.driftn2forty.chatsentinel.moderation.layer2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;
import io.github.driftn2forty.chatsentinel.moderation.ModerationResult;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import io.github.driftn2forty.chatsentinel.util.HttpUtil;
import io.github.driftn2forty.chatsentinel.util.RateLimiter;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class LLMReviewClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final int maxTokens;
    private final double temperature;
    private final HttpUtil httpUtil;
    private final RateLimiter rateLimiter;
    private final DebugLogger logger;
    private final Gson gson = new Gson();

    public LLMReviewClient(String baseUrl, String apiKey, String model, String systemPrompt, int maxTokens, double temperature, HttpUtil httpUtil, RateLimiter rateLimiter, DebugLogger logger) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.httpUtil = httpUtil;
        this.rateLimiter = rateLimiter;
        this.logger = logger;
    }

    public CompletableFuture<ModerationResult> review(String message, String context, Duration timeout) {
        final long startTime = System.nanoTime();

        if (!rateLimiter.tryAcquire()) {
            logger.warn("LLMReviewClient", "Rate limit exceeded, allowing message through");
            return CompletableFuture.completedFuture(ModerationResult.allow(2, elapsed(startTime)));
        }

        final String userContent = buildUserPrompt(message, context);
        final String json = buildRequestJson(userContent);
        final String url = baseUrl + "/v1/chat/completions";

        logger.debug("LLMReviewClient", "POST " + url + " body=" + json);

        return httpUtil.postJsonAsync(url, json, apiKey, timeout).thenApply(response -> parseResponse(response, startTime));
    }

    private String buildUserPrompt(String message, String context) {
        final StringBuilder sb = new StringBuilder();
        if (context != null && !context.isEmpty()) {
            sb.append("Recent chat context:\n").append(context).append("\n\n");
        }
        sb.append("Flagged message: ").append(message);
        return sb.toString();
    }

    private String buildRequestJson(String userContent) {
        final JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("max_tokens", maxTokens);
        request.addProperty("temperature", temperature);

        final JsonArray messages = new JsonArray();

        final JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        final JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userContent);
        messages.add(userMsg);

        request.add("messages", messages);
        return gson.toJson(request);
    }

    private ModerationResult parseResponse(HttpResponse<String> response, long startTime) {
        final long responseTime = elapsed(startTime);

        if (response.statusCode() != 200) {
            logger.error("LLMReviewClient", "API returned HTTP " + response.statusCode() + ": " + response.body());
            return ModerationResult.allow(2, responseTime);
        }

        logger.debug("LLMReviewClient", "Response: " + response.body());

        final JsonObject root = gson.fromJson(response.body(), JsonObject.class);
        final JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            logger.warn("LLMReviewClient", "No choices in LLM response");
            return ModerationResult.allow(2, responseTime);
        }

        final JsonObject firstChoice = choices.get(0).getAsJsonObject();
        final JsonObject messageObj = firstChoice.getAsJsonObject("message");
        if (messageObj == null) {
            logger.warn("LLMReviewClient", "No message in choice");
            return ModerationResult.allow(2, responseTime);
        }

        final String content = messageObj.get("content").getAsString().trim();
        return parseVerdict(content, responseTime);
    }

    private ModerationResult parseVerdict(String content, long responseTime) {
        try {
            final JsonObject verdictJson = gson.fromJson(content, JsonObject.class);
            final String verdict = verdictJson.has("verdict") ? verdictJson.get("verdict").getAsString().toUpperCase() : "ALLOW";

            final List<String> categories = new ArrayList<>();
            if (verdictJson.has("categories")) {
                final JsonArray cats = verdictJson.getAsJsonArray("categories");
                if (cats != null) {
                    for (int i = 0; i < cats.size(); i++) {
                        categories.add(cats.get(i).getAsString());
                    }
                }
            }

            final double score = verdictJson.has("score") ? verdictJson.get("score").getAsDouble() : 0.0;

            logger.info("LLMReviewClient", "LLM verdict=" + verdict + " categories=" + categories + " score=" + String.format("%.4f", score));

            return switch (verdict) {
                case "WARN" -> ModerationResult.warn(2, categories, score, responseTime);
                case "MUTE" -> ModerationResult.mute(2, categories, score, responseTime);
                case "ESCALATE" -> ModerationResult.escalate(2, categories, score, responseTime);
                default -> ModerationResult.allow(2, responseTime);
            };
        } catch (JsonSyntaxException e) {
            logger.warn("LLMReviewClient", "Failed to parse LLM verdict as JSON: " + content);
            final String upper = content.toUpperCase();
            if (upper.contains("ESCALATE")) {
                return ModerationResult.escalate(2, List.of(), 0.0, responseTime);
            } else if (upper.contains("MUTE")) {
                return ModerationResult.mute(2, List.of(), 0.0, responseTime);
            } else if (upper.contains("WARN")) {
                return ModerationResult.warn(2, List.of(), 0.0, responseTime);
            }
            return ModerationResult.allow(2, responseTime);
        }
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
