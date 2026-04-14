package io.github.driftn2forty.chatsentinel.storage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerRepository {

    CompletableFuture<Void> initialize();

    CompletableFuture<PlayerData> loadPlayer(UUID uuid);

    CompletableFuture<Void> savePlayer(UUID uuid, PlayerData data);

    CompletableFuture<Void> logModeration(UUID uuid, ModerationEntry entry);

    CompletableFuture<List<ModerationEntry>> getModerationsForPlayer(UUID uuid, int limit);

    CompletableFuture<Void> logChat(UUID uuid, String playerName, String source, String message);

    CompletableFuture<Void> logDebug(String level, String logSource, String message);

    CompletableFuture<Integer> purgeOldLogs(int days);

    CompletableFuture<Integer> purgeOldPlayers(int days);

    CompletableFuture<Integer> purgeOldDebugLogs(int days);

    CompletableFuture<Integer> purgeOldChatLogs(int days);

    CompletableFuture<Void> shutdown();
}
