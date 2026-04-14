package io.github.driftn2forty.chatsentinel.moderation;

import java.util.List;

public record ModerationResult(Verdict verdict, int layer, List<String> categories, double moderationScore, long responseTimeMs) {

    public enum Verdict {
        ALLOW,
        WARN,
        MUTE,
        ESCALATE
    }

    public static ModerationResult allow(int layer, long responseTimeMs) {
        return new ModerationResult(Verdict.ALLOW, layer, List.of(), 0.0, responseTimeMs);
    }

    public static ModerationResult warn(int layer, List<String> categories, double score, long responseTimeMs) {
        return new ModerationResult(Verdict.WARN, layer, categories, score, responseTimeMs);
    }

    public static ModerationResult mute(int layer, List<String> categories, double score, long responseTimeMs) {
        return new ModerationResult(Verdict.MUTE, layer, categories, score, responseTimeMs);
    }

    public static ModerationResult escalate(int layer, List<String> categories, double score, long responseTimeMs) {
        return new ModerationResult(Verdict.ESCALATE, layer, categories, score, responseTimeMs);
    }

    public boolean isFlagged() {
        return verdict != Verdict.ALLOW;
    }
}
