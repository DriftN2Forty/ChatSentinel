package io.github.driftn2forty.chatsentinel.listener;

import io.github.driftn2forty.chatsentinel.action.ActionDispatcher;
import io.github.driftn2forty.chatsentinel.action.MuteManager;
import io.github.driftn2forty.chatsentinel.moderation.ModerationPipeline;
import io.github.driftn2forty.chatsentinel.storage.PlayerData;
import io.github.driftn2forty.chatsentinel.storage.PlayerRepository;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import java.util.UUID;

public final class BookListener implements Listener {

    private final ModerationPipeline pipeline;
    private final ActionDispatcher actionDispatcher;
    private final MuteManager muteManager;
    private final PlayerRepository repository;
    private final DebugLogger logger;

    public BookListener(ModerationPipeline pipeline, ActionDispatcher actionDispatcher, MuteManager muteManager, PlayerRepository repository, DebugLogger logger) {
        this.pipeline = pipeline;
        this.actionDispatcher = actionDispatcher;
        this.muteManager = muteManager;
        this.repository = repository;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!event.isSigning()) {
            return;
        }

        final Player player = event.getPlayer();
        if (player.hasPermission("chatsentinel.bypass")) {
            return;
        }

        final BookMeta newMeta = event.getNewBookMeta();
        final StringBuilder content = new StringBuilder();
        for (int i = 1; i <= newMeta.getPageCount(); i++) {
            final Component page = newMeta.page(i);
            final String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(page);
            if (!text.isEmpty()) {
                if (!content.isEmpty()) {
                    content.append(" ");
                }
                content.append(text);
            }
        }

        final String text = content.toString();
        if (text.isEmpty()) {
            return;
        }

        final UUID uuid = player.getUniqueId();
        final ModerationPipeline.PipelineResult result = pipeline.process(text, null).join();

        if (result.moderationResult().isFlagged()) {
            logger.info("BookListener", player.getName() + " signed book with flagged content");
            final PlayerData data = repository.loadPlayer(uuid).join();
            muteManager.loadPlayerMuteState(uuid, data);
            actionDispatcher.dispatch(player, text, "book", result.moderationResult(), data).join();
            event.setCancelled(true);
            player.sendMessage(Component.text("\u00a7cYour book contained inappropriate content and was not signed."));
        }
    }
}
