package io.github.driftn2forty.chatsentry.hook;

import io.github.driftn2forty.chatsentry.ChatSentry;
import io.github.driftn2forty.chatsentry.action.MuteManager;
import io.github.driftn2forty.chatsentry.storage.PlayerData;
import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final ChatSentry plugin;
    private final MuteManager muteManager;
    private final PlayerRepository repository;

    public PlaceholderAPIHook(ChatSentry plugin, MuteManager muteManager, PlayerRepository repository) {
        this.plugin = plugin;
        this.muteManager = muteManager;
        this.repository = repository;
    }

    @Override
    public String getIdentifier() {
        return "chatsentry";
    }

    @Override
    public String getAuthor() {
        return plugin.getPluginMeta().getAuthors().isEmpty() ? "DriftN2Forty" : plugin.getPluginMeta().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        final UUID uuid = player.getUniqueId();

        return switch (params.toLowerCase()) {
            case "score" -> {
                final PlayerData data = repository.loadPlayer(uuid).join();
                yield String.format("%.1f", data.getScore());
            }
            case "muted" -> String.valueOf(muteManager.isMuted(uuid));
            case "mute_remaining" -> {
                final long remaining = muteManager.getRemainingSeconds(uuid);
                yield remaining > 0 ? formatDuration(remaining) : "\u2014";
            }
            case "total_offenses" -> {
                final PlayerData data = repository.loadPlayer(uuid).join();
                yield String.valueOf(data.getTotalOffenses());
            }
            case "last_offense" -> {
                final PlayerData data = repository.loadPlayer(uuid).join();
                final Instant last = data.getLastOffense();
                yield last != null ? formatTimeAgo(last) : "never";
            }
            default -> null;
        };
    }

    private static String formatDuration(long totalSeconds) {
        final long minutes = totalSeconds / 60;
        final long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static String formatTimeAgo(Instant instant) {
        final long seconds = Duration.between(instant, Instant.now()).getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        final long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        final long days = hours / 24;
        return days + "d ago";
    }
}
