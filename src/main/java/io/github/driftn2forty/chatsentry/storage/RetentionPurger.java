package io.github.driftn2forty.chatsentry.storage;

import io.github.driftn2forty.chatsentry.util.DebugLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RetentionPurger {

    private final PlayerRepository repository;
    private final int logTtlDays;
    private final int playerTtlDays;
    private final int debugTtlDays;
    private final int chatLogTtlDays;
    private final int purgeIntervalHours;
    private final DebugLogger logger;
    private ScheduledExecutorService scheduler;

    public RetentionPurger(PlayerRepository repository, int logTtlDays, int playerTtlDays, int debugTtlDays, int chatLogTtlDays, int purgeIntervalHours, DebugLogger logger) {
        this.repository = repository;
        this.logTtlDays = logTtlDays;
        this.playerTtlDays = playerTtlDays;
        this.debugTtlDays = debugTtlDays;
        this.chatLogTtlDays = chatLogTtlDays;
        this.purgeIntervalHours = Math.max(1, purgeIntervalHours);
        this.logger = logger;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "ChatSentry-Purger");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::purge, purgeIntervalHours, purgeIntervalHours, TimeUnit.HOURS);
        logger.info("RetentionPurger", "Scheduled retention purge every " + purgeIntervalHours + "h");
    }

    public void purge() {
        try {
            repository.purgeOldLogs(logTtlDays).thenAccept(count -> {
                if (count > 0) logger.info("RetentionPurger", "Purged " + count + " moderation log entries");
            });
            repository.purgeOldPlayers(playerTtlDays).thenAccept(count -> {
                if (count > 0) logger.info("RetentionPurger", "Purged " + count + " inactive player records");
            });
            repository.purgeOldDebugLogs(debugTtlDays).thenAccept(count -> {
                if (count > 0) logger.info("RetentionPurger", "Purged " + count + " debug log entries");
            });
            repository.purgeOldChatLogs(chatLogTtlDays).thenAccept(count -> {
                if (count > 0) logger.info("RetentionPurger", "Purged " + count + " chat log entries");
            });
        } catch (Exception e) {
            logger.error("RetentionPurger", "Purge task failed", e);
        }
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
