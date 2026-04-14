package io.github.driftn2forty.chatsentinel.history;

import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

    private PlayerHistoryTracker tracker;
    private Map<UUID, PlayerData> playerStore;
    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        tracker = new PlayerHistoryTracker(10, true);
        playerStore = new ConcurrentHashMap<>();
        assembler = new ContextAssembler(tracker, 25, true, playerStore::get);
    }

    @Test
    void emptyContext() {
        final String context = assembler.assemble(List.of());
        assertTrue(context.contains("Chat history:"));
    }

    @Test
    void singlePlayerContext() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setScore(4.5);
        playerStore.put(uuid, data);
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "hello world"));
        final String context = assembler.assemble(List.of(uuid));
        assertTrue(context.contains("Steve"));
        assertTrue(context.contains("hello world"));
        assertTrue(context.contains("4.5"));
    }

    @Test
    void multiPlayerContext() {
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();
        final PlayerData data1 = new PlayerData();
        data1.setScore(2.0);
        final PlayerData data2 = new PlayerData();
        data2.setScore(7.0);
        playerStore.put(uuid1, data1);
        playerStore.put(uuid2, data2);
        tracker.record(uuid1, ChatMessage.chat("Steve", uuid1.toString(), "steve msg"));
        tracker.record(uuid2, ChatMessage.chat("Alex", uuid2.toString(), "alex msg"));
        final String context = assembler.assemble(List.of(uuid1, uuid2));
        assertTrue(context.contains("Steve"));
        assertTrue(context.contains("Alex"));
        assertTrue(context.contains("steve msg"));
        assertTrue(context.contains("alex msg"));
    }

    @Test
    void contextWindowLimitsMessages() {
        final ContextAssembler smallAssembler = new ContextAssembler(tracker, 3, false, playerStore::get);
        final UUID uuid = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "message " + i));
        }
        final String context = smallAssembler.assemble(List.of(uuid));
        assertTrue(context.contains("message 9"));
        assertTrue(context.contains("message 8"));
        assertTrue(context.contains("message 7"));
        assertFalse(context.contains("message 0"));
    }

    @Test
    void whisperIncludesTarget() {
        final UUID uuid = UUID.randomUUID();
        playerStore.put(uuid, new PlayerData());
        tracker.record(uuid, ChatMessage.whisper("Steve", uuid.toString(), "Alex", "secret msg"));
        final String context = assembler.assemble(List.of(uuid));
        assertTrue(context.contains("whisper to Alex"));
        assertTrue(context.contains("secret msg"));
    }

    @Test
    void scoresOmittedWhenDisabled() {
        final ContextAssembler noScores = new ContextAssembler(tracker, 25, false, playerStore::get);
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setScore(10.0);
        playerStore.put(uuid, data);
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "hello"));
        final String context = noScores.assemble(List.of(uuid));
        assertFalse(context.contains("Player scores"));
    }

    @Test
    void scoresIncludedWhenEnabled() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setScore(5.0);
        playerStore.put(uuid, data);
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "hello"));
        final String context = assembler.assemble(List.of(uuid));
        assertTrue(context.contains("Player scores"));
        assertTrue(context.contains("5.0"));
    }
}
