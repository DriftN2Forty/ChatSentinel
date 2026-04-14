package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import io.github.driftn2forty.chatsentinel.storage.PlayerRepository;
import io.github.driftn2forty.chatsentinel.storage.SQLiteRepository;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuteManagerTest {

    @TempDir
    Path tempDir;

    private SQLiteRepository repository;
    private MuteManager muteManager;

    @BeforeEach
    void setUp() {
        repository = new SQLiteRepository(tempDir.resolve("mute-test.db"));
        repository.initialize().join();
        final DebugLogger logger = new DebugLogger(Logger.getLogger("Test"), tempDir, false, false, false);
        muteManager = new MuteManager(repository, logger);
    }

    @AfterEach
    void tearDown() {
        repository.shutdown().join();
    }

    @Test
    void newPlayerIsNotMuted() {
        assertFalse(muteManager.isMuted(UUID.randomUUID()));
    }

    @Test
    void applyMuteMakesPlayerMuted() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        muteManager.applyMute(uuid, data, 300).join();
        assertTrue(muteManager.isMuted(uuid));
    }

    @Test
    void expiredMuteNotMuted() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setMuteExpiry(Instant.now().minusSeconds(10));
        muteManager.loadPlayerMuteState(uuid, data);
        assertFalse(muteManager.isMuted(uuid));
    }

    @Test
    void clearMuteRemovesMute() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        muteManager.applyMute(uuid, data, 300).join();
        assertTrue(muteManager.isMuted(uuid));
        muteManager.clearMute(uuid, data);
        assertFalse(muteManager.isMuted(uuid));
        assertNull(data.getMuteExpiry());
    }

    @Test
    void remainingSecondsPositive() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        muteManager.applyMute(uuid, data, 600).join();
        assertTrue(muteManager.getRemainingSeconds(uuid) > 0);
    }

    @Test
    void remainingSecondsZeroIfNotMuted() {
        assertEquals(0, muteManager.getRemainingSeconds(UUID.randomUUID()));
    }

    @Test
    void loadPlayerMuteStateFromData() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setMuteExpiry(Instant.now().plusSeconds(600));
        muteManager.loadPlayerMuteState(uuid, data);
        assertTrue(muteManager.isMuted(uuid));
    }

    @Test
    void getMuteExpiryReturnsCorrectInstant() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        muteManager.applyMute(uuid, data, 300).join();
        assertTrue(muteManager.getMuteExpiry(uuid).isAfter(Instant.now()));
    }
}
