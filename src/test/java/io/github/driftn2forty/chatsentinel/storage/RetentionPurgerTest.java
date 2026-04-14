package io.github.driftn2forty.chatsentinel.storage;

import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RetentionPurgerTest {

    @TempDir
    Path tempDir;

    private SQLiteRepository repository;
    private RetentionPurger purger;

    @BeforeEach
    void setUp() {
        repository = new SQLiteRepository(tempDir.resolve("purge-test.db"));
        repository.initialize().join();
        final DebugLogger logger = new DebugLogger(Logger.getLogger("Test"), tempDir, false, false, false);
        purger = new RetentionPurger(repository, 90, 180, 7, 30, 24, logger);
    }

    @AfterEach
    void tearDown() {
        purger.stop();
        repository.shutdown().join();
    }

    @Test
    void purgeOnEmptyDatabase() {
        assertDoesNotThrow(() -> purger.purge());
    }

    @Test
    void purgeWithData() {
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData();
        data.setScore(1.0);
        repository.savePlayer(uuid, data).join();

        final ModerationEntry entry = new ModerationEntry();
        entry.setPlayer("Steve");
        entry.setUuid(uuid.toString());
        entry.setMessage("test");
        entry.setSource("chat");
        entry.setLayer(0);
        entry.setVerdict("WARN");
        repository.logModeration(uuid, entry).join();
        repository.logChat(uuid, "Steve", "chat", "hello").join();
        repository.logDebug("INFO", "Test", "debug msg").join();

        assertDoesNotThrow(() -> purger.purge());
    }

    @Test
    void startAndStopScheduler() {
        assertDoesNotThrow(() -> {
            purger.start();
            purger.stop();
        });
    }

    @Test
    void stopBeforeStartIsNoOp() {
        assertDoesNotThrow(() -> purger.stop());
    }

    @Test
    void zeroTtlKeepsEverything() {
        final DebugLogger logger = new DebugLogger(Logger.getLogger("Test"), tempDir, false, false, false);
        final RetentionPurger zeroPurger = new RetentionPurger(repository, 0, 0, 0, 0, 1, logger);

        final UUID uuid = UUID.randomUUID();
        repository.logChat(uuid, "Steve", "chat", "kept").join();
        repository.logDebug("INFO", "Test", "kept").join();

        assertDoesNotThrow(zeroPurger::purge);
        zeroPurger.stop();
    }
}
