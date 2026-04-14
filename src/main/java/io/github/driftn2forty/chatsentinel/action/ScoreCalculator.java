package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.storage.PlayerData;

import java.time.Duration;
import java.time.Instant;

public final class ScoreCalculator {

    private final double warnWeight;
    private final double muteWeight;
    private final double escalateWeight;
    private final double decayPointsPerDay;
    private final double minScore;

    public ScoreCalculator(double warnWeight, double muteWeight, double escalateWeight, double decayPointsPerDay, double minScore) {
        this.warnWeight = warnWeight;
        this.muteWeight = muteWeight;
        this.escalateWeight = escalateWeight;
        this.decayPointsPerDay = decayPointsPerDay;
        this.minScore = minScore;
    }

    public double addPoints(PlayerData data, String verdict) {
        applyDecay(data);
        final double points = switch (verdict.toUpperCase()) {
            case "WARN" -> warnWeight;
            case "MUTE" -> muteWeight;
            case "ESCALATE" -> escalateWeight;
            default -> 0.0;
        };
        final double newScore = data.getScore() + points;
        data.setScore(newScore);
        return newScore;
    }

    public void applyDecay(PlayerData data) {
        final Instant lastDecay = data.getLastDecayTimestamp();
        if (lastDecay == null || decayPointsPerDay <= 0) {
            return;
        }
        final Instant now = Instant.now();
        final double daysSinceDecay = Duration.between(lastDecay, now).toMillis() / (1000.0 * 60 * 60 * 24);
        if (daysSinceDecay <= 0) {
            return;
        }
        final double decay = daysSinceDecay * decayPointsPerDay;
        final double newScore = Math.max(minScore, data.getScore() - decay);
        data.setScore(newScore);
        data.setLastDecayTimestamp(now);
    }

    public double getPointsForVerdict(String verdict) {
        return switch (verdict.toUpperCase()) {
            case "WARN" -> warnWeight;
            case "MUTE" -> muteWeight;
            case "ESCALATE" -> escalateWeight;
            default -> 0.0;
        };
    }
}
