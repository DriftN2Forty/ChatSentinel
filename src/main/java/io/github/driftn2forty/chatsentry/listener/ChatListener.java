package io.github.driftn2forty.chatsentry.listener;

import io.github.driftn2forty.chatsentry.action.ActionDispatcher;
import io.github.driftn2forty.chatsentry.action.MuteManager;
import io.github.driftn2forty.chatsentry.history.ChatLogWriter;
import io.github.driftn2forty.chatsentry.history.ChatMessage;
import io.github.driftn2forty.chatsentry.history.PlayerHistoryTracker;
import io.github.driftn2forty.chatsentry.moderation.ModerationPipeline;
import io.github.driftn2forty.chatsentry.storage.PlayerData;
import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ChatListener implements Listener {

    private final ModerationPipeline pipeline;
    private final ActionDispatcher actionDispatcher;
    private final MuteManager muteManager;
    private final PlayerRepository repository;
    private final PlayerHistoryTracker historyTracker;
    private final ChatLogWriter chatLogWriter;
    private final String messageMode;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    public ChatListener(ModerationPipeline pipeline, ActionDispatcher actionDispatcher, MuteManager muteManager, PlayerRepository repository, PlayerHistoryTracker historyTracker, ChatLogWriter chatLogWriter, String messageMode) {
        this.pipeline = pipeline;
        this.actionDispatcher = actionDispatcher;
        this.muteManager = muteManager;
        this.repository = repository;
        this.historyTracker = historyTracker;
        this.chatLogWriter = chatLogWriter;
        this.messageMode = messageMode;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("chatsentry.bypass")) {
            return;
        }

        final UUID uuid = player.getUniqueId();

        if (muteManager.isMuted(uuid)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("\u00a7cYou are muted."));
            return;
        }

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        final ChatMessage chatMessage = ChatMessage.chat(player.getName(), uuid.toString(), message);
        historyTracker.record(uuid, chatMessage);
        chatLogWriter.log(uuid, player.getName(), chatMessage);

        final ReentrantLock lock = playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
        lock.lock();
        try {
            final ModerationPipeline.PipelineResult result = pipeline.process(message, null).join();

            if (result.moderationResult().isFlagged()) {
                final PlayerData data = repository.loadPlayer(uuid).join();
                muteManager.loadPlayerMuteState(uuid, data);
                actionDispatcher.dispatch(player, message, "chat", result.moderationResult(), data).join();

                if ("mask".equalsIgnoreCase(messageMode) && result.isMasked()) {
                    event.message(Component.text(result.maskedMessage()));
                } else {
                    event.setCancelled(true);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
