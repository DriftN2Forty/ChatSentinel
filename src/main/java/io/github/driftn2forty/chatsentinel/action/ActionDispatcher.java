package io.github.driftn2forty.chatsentinel.action;

import io.github.driftn2forty.chatsentinel.moderation.ModerationResult;
import io.github.driftn2forty.chatsentinel.storage.ModerationEntry;
import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import io.github.driftn2forty.chatsentinel.storage.PlayerRepository;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
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

    public ActionDispatcher(ScoreCalculator scoreCalculator, EscalationEngine escalationEngine, MuteManager muteManager, StaffNotifier staffNotifier, PlayerRepository repository, DebugLogger logger, String warnMessage, String muteMessage, long defaultMuteDuration) {
        this.scoreCalculator = scoreCalculator;
        this.escalationEngine = escalationEngine;
        this.muteManager = muteManager;
        this.staffNotifier = staffNotifier;
        this.repository = repository;
        this.logger = logger;
        this.warnMessage = warnMessage;
        this.muteMessage = muteMessage;
        this.defaultMuteDuration = defaultMuteDuration;
    }

    public CompletableFuture<Void> dispatch(Player player, String message, String source, ModerationResult result, PlayerData data) {
        final UUID uuid = player.getUniqueId();
        final double scoreBefore = data.getScore();
        final double scoreAfter = scoreCalculator.addPoints(data, result.verdict().name(), result.categories());
        data.incrementOffenses();
        data.setLastOffense(Instant.now());

        final EscalationEngine.EscalationAction escalation = escalationEngine.evaluate(scoreAfter);

        final String actionTaken;
        long actionDuration = 0;

        switch (escalation.type()) {
            case WARN -> {
                actionTaken = "warn";
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
                staffNotifier.notifyStaff(player.getName(), message, result.verdict().name(), scoreAfter);
            }
            default -> {
                actionTaken = "none";
            }
        }

        logger.info("ActionDispatcher", player.getName() + " verdict=" + result.verdict() + " score=" + String.format("%.1f→%.1f", scoreBefore, scoreAfter) + " action=" + actionTaken);

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
