package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.moderation.ModerationResult;
import io.github.driftn2forty.chatsentinel.storage.ModerationEntry;
import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import io.github.driftn2forty.chatsentinel.storage.PlayerRepository;
import io.github.driftn2forty.chatsentinel.util.CommandPlaceholders;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ActionDispatcher {

    private final ScoreCalculator scoreCalculator;
    private final EscalationEngine escalationEngine;
    private final MuteManager muteManager;
    private final StaffNotifier staffNotifier;
    private final PlayerRepository repository;
    private final DebugLogger logger;
    private final String warnMessage;
    private final String muteMessage;
    private final long defaultMuteDuration;
    private final Plugin plugin;
    private final Map<String, List<String>> categoryCommands;

    public ActionDispatcher(ScoreCalculator scoreCalculator, EscalationEngine escalationEngine, MuteManager muteManager, StaffNotifier staffNotifier, PlayerRepository repository, DebugLogger logger, String warnMessage, String muteMessage, long defaultMuteDuration, Plugin plugin, Map<String, List<String>> categoryCommands) {
        this.scoreCalculator = scoreCalculator;
        this.escalationEngine = escalationEngine;
        this.muteManager = muteManager;
        this.staffNotifier = staffNotifier;
        this.repository = repository;
        this.logger = logger;
        this.warnMessage = warnMessage;
        this.muteMessage = muteMessage;
        this.defaultMuteDuration = defaultMuteDuration;
        this.plugin = plugin;
        this.categoryCommands = categoryCommands;
    }

    public CompletableFuture<Void> dispatch(Player player, String message, String source, ModerationResult result, PlayerData data) {
        final UUID uuid = player.getUniqueId();
        final double scoreBefore = data.getScore();
        final double scoreAfter = scoreCalculator.addPoints(data, result.verdict().name(), result.categories());
        data.incrementOffenses();
        data.setLastOffense(Instant.now());

        final EscalationEngine.EscalationAction escalation = escalationEngine.evaluate(scoreAfter);

        final String actionTaken;
        final long actionDuration;

        switch (escalation.type()) {
            case WARN -> {
                actionTaken = "warn";
                actionDuration = 0;
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(warnMessage));
            }
            case MUTE -> {
                actionTaken = "mute";
                actionDuration = escalation.durationSeconds() > 0 ? escalation.durationSeconds() : defaultMuteDuration;
                muteManager.applyMute(uuid, data, actionDuration);
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(muteMessage));
            }
            case ESCALATE -> {
                actionTaken = "escalate";
                actionDuration = 0;
                staffNotifier.notifyStaff(player.getName(), message, result.verdict().name(), scoreAfter);
            }
            default -> {
                actionTaken = "none";
                actionDuration = 0;
            }
        }

        logger.info("ActionDispatcher", player.getName() + " verdict=" + result.verdict() + " score=" + String.format("%.1f→%.1f", scoreBefore, scoreAfter) + " action=" + actionTaken);

        // ── Execute custom commands ──────────────────────────────────
        final List<String> categories = result.categories() != null ? result.categories() : List.of();
        final List<String> allCommands = new java.util.ArrayList<>();

        // Category commands (fired per flagged category)
        for (final String cat : categories) {
            final List<String> catCmds = categoryCommands.get(cat);
            if (catCmds != null) {
                allCommands.addAll(catCmds);
            }
        }

        // Threshold commands (from the matched escalation threshold)
        allCommands.addAll(escalation.commands());

        if (!allCommands.isEmpty()) {
            final String highestCategory = categories.isEmpty() ? "" : categories.get(0);
            final String playerName = player.getName();
            final UUID playerUuid = uuid;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (final String cmd : allCommands) {
                    final String resolved = CommandPlaceholders.resolve(cmd, playerName, playerUuid, scoreBefore, scoreAfter, highestCategory, result.moderationScore(), source, actionTaken, actionDuration, result.layer());
                    logger.info("ActionDispatcher", "Executing command: " + resolved);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            });
        }

        final ModerationEntry entry = new ModerationEntry();
        entry.setPlayer(player.getName());
        entry.setUuid(uuid.toString());
        entry.setMessage(message);
        entry.setSource(source);
        entry.setLayer(result.layer());
        entry.setVerdict(result.verdict().name());
        entry.setCategories(result.categories() != null ? result.categories() : List.of());
        entry.setModerationScore(result.moderationScore());
        entry.setPlayerScoreBefore(scoreBefore);
        entry.setPlayerScoreAfter(scoreAfter);
        entry.setActionTaken(actionTaken);
        entry.setActionDuration(actionDuration);
        entry.setResponseTimeMs(result.responseTimeMs());

        return repository.savePlayer(uuid, data).thenCompose(v -> repository.logModeration(uuid, entry));
    }
}
