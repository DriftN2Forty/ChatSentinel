package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreCalculatorTest {

    @Test
    void addWarnPoints() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        final double result = calc.addPoints(data, "WARN");
        assertEquals(1.0, result, 0.01);
    }

    @Test
    void addMutePoints() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        final double result = calc.addPoints(data, "MUTE");
        assertEquals(3.0, result, 0.01);
    }

    @Test
    void addEscalatePoints() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        final double result = calc.addPoints(data, "ESCALATE");
        assertEquals(5.0, result, 0.01);
    }

    @Test
    void unknownVerdictAddsZero() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        final double result = calc.addPoints(data, "ALLOW");
        assertEquals(0.0, result, 0.01);
    }

    @Test
    void cumulativeScoring() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        calc.addPoints(data, "WARN");
        calc.addPoints(data, "WARN");
        calc.addPoints(data, "MUTE");
        assertEquals(5.0, data.getScore(), 0.01);
    }

    @Test
    void decayReducesScore() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 1.0, 0);
        final PlayerData data = new PlayerData();
        data.setScore(10.0);
        data.setLastDecayTimestamp(Instant.now().minusSeconds(86400 * 2));
        calc.applyDecay(data);
        assertTrue(data.getScore() < 10.0);
        assertTrue(data.getScore() >= 8.0);
    }

    @Test
    void decayRespectsFloor() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 100.0, 0);
        final PlayerData data = new PlayerData();
        data.setScore(5.0);
        data.setLastDecayTimestamp(Instant.now().minusSeconds(86400 * 10));
        calc.applyDecay(data);
        assertEquals(0.0, data.getScore(), 0.01);
    }

    @Test
    void decayWithZeroRateDoesNothing() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0, 0);
        final PlayerData data = new PlayerData();
        data.setScore(10.0);
        data.setLastDecayTimestamp(Instant.now().minusSeconds(86400 * 30));
        calc.applyDecay(data);
        assertEquals(10.0, data.getScore(), 0.01);
    }

    @Test
    void getPointsForVerdict() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        assertEquals(1.0, calc.getPointsForVerdict("WARN"));
        assertEquals(3.0, calc.getPointsForVerdict("MUTE"));
        assertEquals(5.0, calc.getPointsForVerdict("ESCALATE"));
        assertEquals(0.0, calc.getPointsForVerdict("ALLOW"));
    }

    @Test
    void caseInsensitiveVerdict() {
        final ScoreCalculator calc = new ScoreCalculator(1, 3, 5, 0.5, 0);
        final PlayerData data = new PlayerData();
        data.setLastDecayTimestamp(Instant.now());
        calc.addPoints(data, "warn");
        assertEquals(1.0, data.getScore(), 0.01);
    }
}
