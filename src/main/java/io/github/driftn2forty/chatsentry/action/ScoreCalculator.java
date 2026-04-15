package io.github.driftn2forty.chatsentry.action;

import io.github.driftn2forty.chatsentry.storage.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ScoreCalculator {

    private final double warnWeight;
    private final double muteWeight;
    private final double escalateWeight;
    private final double decayPointsPerDay;
    private final double minScore;
    private final Map<String, Double> categoryWeights;

    public ScoreCalculator(double warnWeight, double muteWeight, double escalateWeight, double decayPointsPerDay, double minScore, Map<String, Double> categoryWeights) {
        this.warnWeight = warnWeight;
        this.muteWeight = muteWeight;
        this.escalateWeight = escalateWeight;
        this.decayPointsPerDay = decayPointsPerDay;
        this.minScore = minScore;
        this.categoryWeights = categoryWeights;
    }

    public double addPoints(PlayerData data, String verdict) {
        applyDecay(data);
        final double points = getPointsForVerdict(verdict);
        final double newScore = data.getScore() + points;
        data.setScore(newScore);
        return newScore;
    }

    public double addPoints(PlayerData data, String verdict, List<String> categories) {
        applyDecay(data);
        final double verdictPoints = getPointsForVerdict(verdict);
        final double categoryPoints = getHighestCategoryWeight(categories);
        final double points = Math.max(verdictPoints, categoryPoints);
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

    public double getHighestCategoryWeight(List<String> categories) {
        if (categoryWeights.isEmpty() || categories == null || categories.isEmpty()) {
            return 0.0;
        }
        double highest = 0.0;
        for (final String category : categories) {
            final Double weight = categoryWeights.get(category);
            if (weight != null && weight > highest) {
                highest = weight;
            }
        }
        return highest;
    }
}
