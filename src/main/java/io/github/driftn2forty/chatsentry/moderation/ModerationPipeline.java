package io.github.driftn2forty.chatsentry.moderation;

import io.github.driftn2forty.chatsentry.filter.LocalFilterLayer;
import io.github.driftn2forty.chatsentry.moderation.layer1.OpenAIModerationClient;
import io.github.driftn2forty.chatsentry.moderation.layer2.LLMReviewClient;
import io.github.driftn2forty.chatsentry.util.DebugLogger;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ModerationPipeline {

    private final LocalFilterLayer localFilter;
    private final OpenAIModerationClient layer1Client;
    private final LLMReviewClient layer2Client;
    private final boolean layer1Enabled;
    private final boolean layer2Enabled;
    private final double layer1Threshold;
    private final long timeoutMs;
    private final boolean failOpen;
    private final int maxRetryAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final String messageMode;
    private final DebugLogger logger;

    public ModerationPipeline(LocalFilterLayer localFilter, OpenAIModerationClient layer1Client, LLMReviewClient layer2Client, boolean layer1Enabled, boolean layer2Enabled, double layer1Threshold, long timeoutMs, boolean failOpen, int maxRetryAttempts, long baseDelayMs, long maxDelayMs, String messageMode, DebugLogger logger) {
        this.localFilter = localFilter;
        this.layer1Client = layer1Client;
        this.layer2Client = layer2Client;
        this.layer1Enabled = layer1Enabled;
        this.layer2Enabled = layer2Enabled;
        this.layer1Threshold = layer1Threshold;
        this.timeoutMs = timeoutMs;
        this.failOpen = failOpen;
        this.maxRetryAttempts = maxRetryAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.messageMode = messageMode;
        this.logger = logger;
    }

    public CompletableFuture<PipelineResult> process(String message, String context) {
        final long pipelineStart = System.nanoTime();

        final LocalFilterLayer.FilterResult filterResult = localFilter.check(message);
        if (filterResult.flagged()) {
            final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
            logger.info("ModerationPipeline", "Layer 0 flagged message in " + elapsed + "ms: matches=" + filterResult.matches().size());

            String maskedMessage = null;
            if ("mask".equalsIgnoreCase(messageMode)) {
                maskedMessage = localFilter.mask(message);
            }
            final ModerationResult result = ModerationResult.warn(0, List.of("profanity"), 0.0, elapsed);
            return CompletableFuture.completedFuture(new PipelineResult(result, maskedMessage));
        }

        if (!layer1Enabled) {
            final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
            return CompletableFuture.completedFuture(new PipelineResult(ModerationResult.allow(0, elapsed), null));
        }

        return runWithRetry(() -> {
            final long remaining = remainingMs(pipelineStart);
            if (remaining <= 0) {
                return CompletableFuture.completedFuture(ModerationResult.allow(1, (System.nanoTime() - pipelineStart) / 1_000_000));
            }
            return layer1Client.moderate(message, Duration.ofMillis(remaining));
        }, pipelineStart).thenCompose(layer1Result -> {
            if (!layer1Result.isFlagged()) {
                return CompletableFuture.completedFuture(new PipelineResult(layer1Result, null));
            }

            if (!layer2Enabled) {
                return CompletableFuture.completedFuture(new PipelineResult(layer1Result, null));
            }

            if (layer1Result.moderationScore() < layer1Threshold) {
                return CompletableFuture.completedFuture(new PipelineResult(layer1Result, null));
            }

            return runWithRetry(() -> {
                final long remaining = remainingMs(pipelineStart);
                if (remaining <= 0) {
                    return CompletableFuture.completedFuture(layer1Result);
                }
                return layer2Client.review(message, context, Duration.ofMillis(remaining));
            }, pipelineStart).thenApply(layer2Result -> new PipelineResult(layer2Result, null));
        }).exceptionally(ex -> {
            logger.error("ModerationPipeline", "Pipeline error: " + ex.getMessage());
            final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
            if (failOpen) {
                return new PipelineResult(ModerationResult.allow(0, elapsed), null);
            }
            return new PipelineResult(ModerationResult.escalate(0, List.of("error"), 0.0, elapsed), null);
        });
    }

    private CompletableFuture<ModerationResult> runWithRetry(java.util.function.Supplier<CompletableFuture<ModerationResult>> action, long pipelineStart) {
        return attemptWithRetry(action, 1, pipelineStart);
    }

    private CompletableFuture<ModerationResult> attemptWithRetry(java.util.function.Supplier<CompletableFuture<ModerationResult>> action, int attempt, long pipelineStart) {
        final long remaining = remainingMs(pipelineStart);
        if (remaining <= 0) {
            logger.warn("ModerationPipeline", "Timeout expired before attempt " + attempt);
            final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
            return CompletableFuture.completedFuture(failOpen ? ModerationResult.allow(0, elapsed) : ModerationResult.escalate(0, List.of("timeout"), 0.0, elapsed));
        }

        return action.get().orTimeout(remaining, TimeUnit.MILLISECONDS).exceptionally(ex -> {
            if (attempt >= maxRetryAttempts) {
                logger.warn("ModerationPipeline", "All " + maxRetryAttempts + " attempts exhausted: " + ex.getMessage());
                final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
                return failOpen ? ModerationResult.allow(0, elapsed) : ModerationResult.escalate(0, List.of("timeout"), 0.0, elapsed);
            }

            final long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), maxDelayMs);
            final long afterDelay = remainingMs(pipelineStart) - delay;
            if (afterDelay <= 0) {
                logger.warn("ModerationPipeline", "Not enough time for retry " + (attempt + 1));
                final long elapsed = (System.nanoTime() - pipelineStart) / 1_000_000;
                return failOpen ? ModerationResult.allow(0, elapsed) : ModerationResult.escalate(0, List.of("timeout"), 0.0, elapsed);
            }

            logger.warn("ModerationPipeline", "Attempt " + attempt + " failed, retrying in " + delay + "ms (" + (ex instanceof TimeoutException ? "timeout" : ex.getMessage()) + ")");
            return null;
        }).thenCompose(result -> {
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            }
            final long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), maxDelayMs);
            final CompletableFuture<ModerationResult> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> attemptWithRetry(action, attempt + 1, pipelineStart).whenComplete((r, ex) -> {
                if (ex != null) {
                    delayed.completeExceptionally(ex);
                } else {
                    delayed.complete(r);
                }
            }));
            return delayed;
        });
    }

    private long remainingMs(long pipelineStartNanos) {
        return timeoutMs - ((System.nanoTime() - pipelineStartNanos) / 1_000_000);
    }

    public record PipelineResult(ModerationResult moderationResult, String maskedMessage) {
        public boolean isMasked() {
            return maskedMessage != null;
        }
    }
}
