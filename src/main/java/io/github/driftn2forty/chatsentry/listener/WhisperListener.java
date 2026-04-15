package io.github.driftn2forty.chatsentry.listener;

import io.github.driftn2forty.chatsentry.action.ActionDispatcher;
import io.github.driftn2forty.chatsentry.action.MuteManager;
import io.github.driftn2forty.chatsentry.history.ChatLogWriter;
import io.github.driftn2forty.chatsentry.history.ChatMessage;
import io.github.driftn2forty.chatsentry.history.PlayerHistoryTracker;
import io.github.driftn2forty.chatsentry.moderation.ModerationPipeline;
import io.github.driftn2forty.chatsentry.storage.PlayerData;
import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhisperListener implements Listener {

    private static final Pattern WHISPER_PATTERN = Pattern.compile("^/(msg|tell|w|whisper|r)\\s+(?:(\\S+)\\s+)?(.+)$", Pattern.CASE_INSENSITIVE);

    private final ModerationPipeline pipeline;
    private final ActionDispatcher actionDispatcher;
    private final MuteManager muteManager;
    private final PlayerRepository repository;
    private final PlayerHistoryTracker historyTracker;
    private final ChatLogWriter chatLogWriter;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    public WhisperListener(ModerationPipeline pipeline, ActionDispatcher actionDispatcher, MuteManager muteManager, PlayerRepository repository, PlayerHistoryTracker historyTracker, ChatLogWriter chatLogWriter) {
        this.pipeline = pipeline;
        this.actionDispatcher = actionDispatcher;
        this.muteManager = muteManager;
        this.repository = repository;
        this.historyTracker = historyTracker;
        this.chatLogWriter = chatLogWriter;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("chatsentry.bypass")) {
            return;
        }

        final String command = event.getMessage();
        final Matcher matcher = WHISPER_PATTERN.matcher(command);
        if (!matcher.matches()) {
            return;
        }

        final String targetName = matcher.group(2);
        final String message = matcher.group(3);
        final UUID uuid = player.getUniqueId();

        if (muteManager.isMuted(uuid)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("\u00a7cYou are muted."));
            return;
        }

        final ChatMessage chatMessage = ChatMessage.whisper(player.getName(), uuid.toString(), targetName, message);
        historyTracker.record(uuid, chatMessage);
        chatLogWriter.log(uuid, player.getName(), chatMessage);

        final ReentrantLock lock = playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
        lock.lock();
        try {
            final ModerationPipeline.PipelineResult result = pipeline.process(message, null).join();

            if (result.moderationResult().isFlagged()) {
                final PlayerData data = repository.loadPlayer(uuid).join();
                muteManager.loadPlayerMuteState(uuid, data);
                actionDispatcher.dispatch(player, message, "whisper", result.moderationResult(), data).join();
                event.setCancelled(true);
            }
        } finally {
            lock.unlock();
        }
    }
}
