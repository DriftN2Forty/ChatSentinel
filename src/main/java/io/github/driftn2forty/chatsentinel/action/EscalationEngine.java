package io.github.driftn2forty.chatsentinel.action;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class EscalationEngine {

    private final List<Threshold> thresholds;

    public EscalationEngine(List<Threshold> thresholds) {
        final List<Threshold> sorted = new java.util.ArrayList<>(thresholds);
        sorted.sort(Comparator.comparingDouble(Threshold::score));
        this.thresholds = Collections.unmodifiableList(sorted);
    }

    public EscalationAction evaluate(double score) {
        Threshold matched = null;
        for (final Threshold threshold : thresholds) {
            if (score >= threshold.score()) {
                matched = threshold;
            }
        }
        if (matched == null) {
            return EscalationAction.NONE;
        }
        return switch (matched.action().toLowerCase()) {
            case "warn" -> EscalationAction.WARN;
            case "mute" -> new EscalationAction(EscalationAction.Type.MUTE, matched.durationSeconds());
            case "escalate" -> EscalationAction.ESCALATE;
            default -> EscalationAction.NONE;
        };
    }

    public List<Threshold> getThresholds() {
        return thresholds;
    }

    public record Threshold(double score, String action, long durationSeconds) {
        public Threshold(double score, String action) {
            this(score, action, 0);
        }
    }

    public record EscalationAction(Type type, long durationSeconds) {

        public enum Type {
            NONE,
            WARN,
            MUTE,
            ESCALATE
        }

        public static final EscalationAction NONE = new EscalationAction(Type.NONE, 0);
        public static final EscalationAction WARN = new EscalationAction(Type.WARN, 0);
        public static final EscalationAction ESCALATE = new EscalationAction(Type.ESCALATE, 0);
    }
}
