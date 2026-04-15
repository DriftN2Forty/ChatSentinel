package io.github.driftn2forty.chatsentry.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class HttpUtil {

    private final HttpClient client;

    public HttpUtil() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public CompletableFuture<HttpResponse<String>> postJsonAsync(String url, String jsonBody, String apiKey, Duration timeout) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> getAsync(String url, String apiKey, Duration timeout) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).GET();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public void shutdown() {
        client.close();
    }
}
