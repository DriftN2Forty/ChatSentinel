package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.action.EscalationEngine.EscalationAction;
import io.github.driftn2forty.chatsentinel.action.EscalationEngine.Threshold;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscalationEngineTest {

    private static final List<Threshold> DEFAULT_THRESHOLDS = List.of(
            new Threshold(3, "warn"),
            new Threshold(6, "mute", 300),
            new Threshold(12, "mute", 1800),
            new Threshold(20, "mute", 86400),
            new Threshold(30, "escalate")
    );

    @Test
    void belowFirstThresholdReturnsNone() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        assertEquals(EscalationAction.Type.NONE, engine.evaluate(2.0).type());
    }

    @Test
    void exactThresholdTriggersAction() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        assertEquals(EscalationAction.Type.WARN, engine.evaluate(3.0).type());
    }

    @Test
    void betweenThresholdsUsesLowerMatch() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        final EscalationAction action = engine.evaluate(5.0);
        assertEquals(EscalationAction.Type.WARN, action.type());
    }

    @Test
    void muteThresholdIncludesDuration() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        final EscalationAction action = engine.evaluate(6.0);
        assertEquals(EscalationAction.Type.MUTE, action.type());
        assertEquals(300, action.durationSeconds());
    }

    @Test
    void higherMuteThresholdLongerDuration() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        final EscalationAction action = engine.evaluate(15.0);
        assertEquals(EscalationAction.Type.MUTE, action.type());
        assertEquals(1800, action.durationSeconds());
    }

    @Test
    void escalateThreshold() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        assertEquals(EscalationAction.Type.ESCALATE, engine.evaluate(30.0).type());
    }

    @Test
    void scoreWellAboveMaxThreshold() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        assertEquals(EscalationAction.Type.ESCALATE, engine.evaluate(100.0).type());
    }

    @Test
    void zeroScoreReturnsNone() {
        final EscalationEngine engine = new EscalationEngine(DEFAULT_THRESHOLDS);
        assertEquals(EscalationAction.Type.NONE, engine.evaluate(0.0).type());
    }

    @Test
    void emptyThresholdsAlwaysNone() {
        final EscalationEngine engine = new EscalationEngine(List.of());
        assertEquals(EscalationAction.Type.NONE, engine.evaluate(100.0).type());
    }

    @Test
    void thresholdsAreSortedInternally() {
        final EscalationEngine engine = new EscalationEngine(List.of(
                new Threshold(10, "mute", 600),
                new Threshold(3, "warn"),
                new Threshold(20, "escalate")
        ));
        assertEquals(EscalationAction.Type.WARN, engine.evaluate(5.0).type());
        assertEquals(EscalationAction.Type.MUTE, engine.evaluate(10.0).type());
        assertEquals(EscalationAction.Type.ESCALATE, engine.evaluate(20.0).type());
    }

    @Test
    void thresholdWithCommandsPassesThroughToAction() {
        final List<String> cmds = List.of("kick %player%", "say %player% was kicked");
        final EscalationEngine engine = new EscalationEngine(List.of(
                new Threshold(5, "warn", 0, cmds)
        ));
        final EscalationAction action = engine.evaluate(5.0);
        assertEquals(EscalationAction.Type.WARN, action.type());
        assertEquals(cmds, action.commands());
    }

    @Test
    void thresholdWithoutCommandsReturnsEmptyList() {
        final EscalationEngine engine = new EscalationEngine(List.of(
                new Threshold(5, "mute", 300)
        ));
        final EscalationAction action = engine.evaluate(5.0);
        assertEquals(EscalationAction.Type.MUTE, action.type());
        assertEquals(List.of(), action.commands());
    }

    @Test
    void muteThresholdWithCommandsIncludesBoth() {
        final List<String> cmds = List.of("tempban %player% 1h");
        final EscalationEngine engine = new EscalationEngine(List.of(
                new Threshold(10, "mute", 600, cmds)
        ));
        final EscalationAction action = engine.evaluate(10.0);
        assertEquals(EscalationAction.Type.MUTE, action.type());
        assertEquals(600, action.durationSeconds());
        assertEquals(cmds, action.commands());
    }

    @Test
    void noneActionHasEmptyCommands() {
        final EscalationAction none = EscalationAction.NONE;
        assertEquals(List.of(), none.commands());
    }
}
