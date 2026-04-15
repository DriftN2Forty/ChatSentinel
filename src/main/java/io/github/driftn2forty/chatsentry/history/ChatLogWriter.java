package io.github.driftn2forty.chatsentry.history;

import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import io.github.driftn2forty.chatsentry.util.DebugLogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ChatLogWriter {

    private final PlayerRepository repository;
    private final boolean enabled;
    private final DebugLogger logger;

    public ChatLogWriter(PlayerRepository repository, boolean enabled, DebugLogger logger) {
        this.repository = repository;
        this.enabled = enabled;
        this.logger = logger;
    }

    public CompletableFuture<Void> log(UUID uuid, String playerName, ChatMessage message) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        return repository.logChat(uuid, playerName, message.sourceString(), message.text()).exceptionally(ex -> {
            logger.error("ChatLogWriter", "Failed to log chat: " + ex.getMessage());
            return null;
        });
    }

    public boolean isEnabled() {
        return enabled;
    }
}
