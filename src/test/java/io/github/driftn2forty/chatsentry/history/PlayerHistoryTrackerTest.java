package io.github.driftn2forty.chatsentry.history;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHistoryTrackerTest {

    @Test
    void emptyHistoryForNewPlayer() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        assertTrue(tracker.getHistory(UUID.randomUUID()).isEmpty());
    }

    @Test
    void recordAndRetrieve() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        final UUID uuid = UUID.randomUUID();
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "hello"));
        final List<ChatMessage> history = tracker.getHistory(uuid);
        assertEquals(1, history.size());
        assertEquals("hello", history.get(0).text());
    }

    @Test
    void ringBufferOverflow() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(3, true);
        final UUID uuid = UUID.randomUUID();
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "msg1"));
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "msg2"));
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "msg3"));
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "msg4"));
        final List<ChatMessage> history = tracker.getHistory(uuid);
        assertEquals(3, history.size());
        assertEquals("msg2", history.get(0).text());
        assertEquals("msg4", history.get(2).text());
    }

    @Test
    void whisperCapturedWhenEnabled() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        final UUID uuid = UUID.randomUUID();
        tracker.record(uuid, ChatMessage.whisper("Steve", uuid.toString(), "Alex", "secret"));
        assertEquals(1, tracker.getHistory(uuid).size());
    }

    @Test
    void whisperSkippedWhenDisabled() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, false);
        final UUID uuid = UUID.randomUUID();
        tracker.record(uuid, ChatMessage.whisper("Steve", uuid.toString(), "Alex", "secret"));
        assertTrue(tracker.getHistory(uuid).isEmpty());
    }

    @Test
    void clearPlayer() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        final UUID uuid = UUID.randomUUID();
        tracker.record(uuid, ChatMessage.chat("Steve", uuid.toString(), "hello"));
        tracker.clear(uuid);
        assertTrue(tracker.getHistory(uuid).isEmpty());
    }

    @Test
    void clearAllPlayers() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();
        tracker.record(uuid1, ChatMessage.chat("Steve", uuid1.toString(), "hello"));
        tracker.record(uuid2, ChatMessage.chat("Alex", uuid2.toString(), "hi"));
        tracker.clearAll();
        assertEquals(0, tracker.getTrackedPlayerCount());
    }

    @Test
    void isolationBetweenPlayers() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();
        tracker.record(uuid1, ChatMessage.chat("Steve", uuid1.toString(), "steve msg"));
        tracker.record(uuid2, ChatMessage.chat("Alex", uuid2.toString(), "alex msg"));
        assertEquals(1, tracker.getHistory(uuid1).size());
        assertEquals("steve msg", tracker.getHistory(uuid1).get(0).text());
        assertEquals("alex msg", tracker.getHistory(uuid2).get(0).text());
    }

    @Test
    void trackedPlayerCount() {
        final PlayerHistoryTracker tracker = new PlayerHistoryTracker(10, true);
        assertEquals(0, tracker.getTrackedPlayerCount());
        final UUID uuid1 = UUID.randomUUID();
        tracker.record(uuid1, ChatMessage.chat("Steve", uuid1.toString(), "hello"));
        assertEquals(1, tracker.getTrackedPlayerCount());
    }
}
