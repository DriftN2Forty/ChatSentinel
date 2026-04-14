package io.github.driftn2forty.chatsentinel.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteRepositoryTest {

    @TempDir
    Path tempDir;

    private SQLiteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SQLiteRepository(tempDir.resolve("test.db"));
        repository.initialize().join();
    }

    @AfterEach
    void tearDown() {
        repository.shutdown().join();
    }

    @Test
    void loadPlayerReturnsDefaultForNewPlayer() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = repository.loadPlayer(uuid).join();
        assertNotNull(data);
        assertEquals(0.0, data.getScore());
        assertEquals(0, data.getTotalOffenses());
        assertFalse(data.isMuted());
    }

    @Test
    void saveAndLoadPlayerRoundTrip() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setScore(4.5);
        data.setTotalOffenses(3);
        data.setLastDecayTimestamp(Instant.parse("2025-01-01T00:00:00Z"));
        data.setMuteExpiry(Instant.parse("2099-01-01T00:00:00Z"));
        data.setLastOffense(Instant.parse("2025-06-15T12:00:00Z"));

        repository.savePlayer(uuid, data).join();

        final PlayerData loaded = repository.loadPlayer(uuid).join();
        assertEquals(4.5, loaded.getScore());
        assertEquals(3, loaded.getTotalOffenses());
        assertTrue(loaded.isMuted());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), loaded.getLastDecayTimestamp());
        assertEquals(Instant.parse("2025-06-15T12:00:00Z"), loaded.getLastOffense());
    }

    @Test
    void savePlayerOverwritesExisting() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data1 = new PlayerData();
        data1.setScore(2.0);
        repository.savePlayer(uuid, data1).join();

        final PlayerData data2 = new PlayerData();
        data2.setScore(8.0);
        data2.setTotalOffenses(5);
        repository.savePlayer(uuid, data2).join();

        final PlayerData loaded = repository.loadPlayer(uuid).join();
        assertEquals(8.0, loaded.getScore());
        assertEquals(5, loaded.getTotalOffenses());
    }

    @Test
    void logModerationAndRetrieve() {
        final UUID uuid = UUID.randomUUID();
        final ModerationEntry entry = new ModerationEntry();
        entry.setPlayer("Steve");
        entry.setUuid(uuid.toString());
        entry.setMessage("bad word");
        entry.setSource("chat");
        entry.setLayer(0);
        entry.setVerdict("WARN");
        entry.setCategories(List.of("profanity"));
        entry.setModerationScore(0.0);
        entry.setPlayerScoreBefore(0.0);
        entry.setPlayerScoreAfter(1.0);
        entry.setActionTaken("warn");
        entry.setResponseTimeMs(5);

        repository.logModeration(uuid, entry).join();

        final List<ModerationEntry> entries = repository.getModerationsForPlayer(uuid, 10).join();
        assertEquals(1, entries.size());
        assertEquals("Steve", entries.get(0).getPlayer());
        assertEquals("bad word", entries.get(0).getMessage());
        assertEquals("WARN", entries.get(0).getVerdict());
        assertEquals(0, entries.get(0).getLayer());
    }

    @Test
    void getModerationsRespectsLimit() {
        final UUID uuid = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            final ModerationEntry entry = new ModerationEntry();
            entry.setPlayer("Player");
            entry.setUuid(uuid.toString());
            entry.setMessage("message " + i);
            entry.setSource("chat");
            entry.setLayer(0);
            entry.setVerdict("WARN");
            repository.logModeration(uuid, entry).join();
        }

        final List<ModerationEntry> entries = repository.getModerationsForPlayer(uuid, 3).join();
        assertEquals(3, entries.size());
    }

    @Test
    void logChatDoesNotThrow() {
        final UUID uuid = UUID.randomUUID();
        repository.logChat(uuid, "Steve", "chat", "hello world").join();
    }

    @Test
    void logDebugDoesNotThrow() {
        repository.logDebug("INFO", "Test", "test message").join();
    }

    @Test
    void purgeWithZeroDaysReturnsZero() {
        assertEquals(0, repository.purgeOldLogs(0).join());
        assertEquals(0, repository.purgeOldPlayers(0).join());
        assertEquals(0, repository.purgeOldDebugLogs(0).join());
        assertEquals(0, repository.purgeOldChatLogs(0).join());
    }

    @Test
    void purgeOldLogsRemovesOldEntries() {
        final UUID uuid = UUID.randomUUID();
        final ModerationEntry entry = new ModerationEntry();
        entry.setPlayer("Player");
        entry.setUuid(uuid.toString());
        entry.setMessage("msg");
        entry.setSource("chat");
        entry.setLayer(0);
        entry.setVerdict("WARN");
        repository.logModeration(uuid, entry).join();

        final int purged = repository.purgeOldLogs(1).join();
        assertEquals(0, purged);

        final List<ModerationEntry> remaining = repository.getModerationsForPlayer(uuid, 10).join();
        assertEquals(1, remaining.size());
    }

    @Test
    void multiplePlayerIsolation() {
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();

        final PlayerData data1 = new PlayerData();
        data1.setScore(1.0);
        repository.savePlayer(uuid1, data1).join();

        final PlayerData data2 = new PlayerData();
        data2.setScore(9.0);
        repository.savePlayer(uuid2, data2).join();

        assertEquals(1.0, repository.loadPlayer(uuid1).join().getScore());
        assertEquals(9.0, repository.loadPlayer(uuid2).join().getScore());
    }
}
