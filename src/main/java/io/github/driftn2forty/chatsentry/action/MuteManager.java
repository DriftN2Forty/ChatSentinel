package io.github.driftn2forty.chatsentry.action;

import io.github.driftn2forty.chatsentry.storage.PlayerData;
import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import io.github.driftn2forty.chatsentry.util.DebugLogger;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class MuteManager {

    private final PlayerRepository repository;
    private final DebugLogger logger;
    private final ConcurrentHashMap<UUID, Instant> muteCache = new ConcurrentHashMap<>();

    public MuteManager(PlayerRepository repository, DebugLogger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public CompletableFuture<Void> applyMute(UUID uuid, PlayerData data, long durationSeconds) {
        final Instant expiry = Instant.now().plusSeconds(durationSeconds);
        data.setMuteExpiry(expiry);
        muteCache.put(uuid, expiry);
        logger.info("MuteManager", "Muted " + uuid + " for " + durationSeconds + "s (expires " + expiry + ")");
        return repository.savePlayer(uuid, data);
    }

    public boolean isMuted(UUID uuid) {
        final Instant cached = muteCache.get(uuid);
        if (cached != null) {
            if (Instant.now().isBefore(cached)) {
                return true;
            }
            muteCache.remove(uuid);
        }
        return false;
    }

    public void loadPlayerMuteState(UUID uuid, PlayerData data) {
        if (data.isMuted()) {
            muteCache.put(uuid, data.getMuteExpiry());
        }
    }

    public void clearMute(UUID uuid, PlayerData data) {
        data.setMuteExpiry(null);
        muteCache.remove(uuid);
        logger.info("MuteManager", "Cleared mute for " + uuid);
    }

    public Instant getMuteExpiry(UUID uuid) {
        return muteCache.get(uuid);
    }

    public long getRemainingSeconds(UUID uuid) {
        final Instant expiry = muteCache.get(uuid);
        if (expiry == null) {
            return 0;
        }
        final long remaining = java.time.Duration.between(Instant.now(), expiry).getSeconds();
        return Math.max(0, remaining);
    }
}
