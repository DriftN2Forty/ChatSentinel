package io.github.driftn2forty.chatsentinel.listener;

import io.github.driftn2forty.chatsentinel.filter.LocalFilterLayer;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public final class SignListener implements Listener {

    private final LocalFilterLayer localFilter;
    private final DebugLogger logger;

    public SignListener(LocalFilterLayer localFilter, DebugLogger logger) {
        this.localFilter = localFilter;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPermission("chatsentinel.bypass")) {
            return;
        }

        final StringBuilder combined = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            final Component line = event.line(i);
            if (line != null) {
                final String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (!text.isEmpty()) {
                    if (!combined.isEmpty()) {
                        combined.append(" ");
                    }
                    combined.append(text);
                }
            }
        }

        final String text = combined.toString();
        if (text.isEmpty()) {
            return;
        }

        final LocalFilterLayer.FilterResult result = localFilter.check(text);
        if (result.flagged()) {
            logger.info("SignListener", player.getName() + " placed sign with flagged content: " + text);
            for (int i = 0; i < 4; i++) {
                event.line(i, Component.empty());
            }
            player.sendMessage(Component.text("\u00a7cYour sign contained inappropriate content and has been cleared."));
        }
    }
}
