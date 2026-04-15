package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.util.CommandPlaceholders;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionDispatcherTest {

    private static final UUID TEST_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void resolvePlaceholdersReplacesAllTokens() {
        final String template = "ban %player% %uuid% score=%score% before=%score_before% cat=%category% mod=%moderation_score% src=%source% act=%action% dur=%duration% layer=%layer%";
        final String result = CommandPlaceholders.resolve(template, "TestPlayer", TEST_UUID, 2.0, 5.5, "hate", 0.95, "chat", "mute", 300, 1);
        assertEquals("ban TestPlayer " + TEST_UUID + " score=5.5 before=2.0 cat=hate mod=0.95 src=chat act=mute dur=300 layer=1", result);
    }

    @Test
    void resolvePlaceholdersWithNoTokensReturnsOriginal() {
        final String command = "say hello world";
        final String result = CommandPlaceholders.resolve(command, "TestPlayer", TEST_UUID, 0, 0, "", 0, "chat", "warn", 0, 0);
        assertEquals("say hello world", result);
    }

    @Test
    void resolvePlaceholdersWithEmptyCategory() {
        final String template = "kick %player% %category%";
        final String result = CommandPlaceholders.resolve(template, "TestPlayer", TEST_UUID, 0, 3.0, "", 0.8, "chat", "warn", 0, 1);
        assertEquals("kick TestPlayer ", result);
    }

    @Test
    void resolvePlaceholdersPlayerNameIsExact() {
        final String template = "%player%";
        final String result = CommandPlaceholders.resolve(template, "TestPlayer", TEST_UUID, 0, 0, "", 0, "", "", 0, 0);
        assertEquals("TestPlayer", result);
    }

    @Test
    void resolvePlaceholdersScoreFormatting() {
        final String template = "%score% %score_before%";
        final String result = CommandPlaceholders.resolve(template, "TestPlayer", TEST_UUID, 1.123, 4.567, "", 0, "", "", 0, 0);
        assertTrue(result.startsWith("4.6"));
        assertTrue(result.contains("1.1"));
    }
}
