package io.github.driftn2forty.chatsentry.listener;

import io.github.driftn2forty.chatsentry.filter.LocalFilterLayer;
import io.github.driftn2forty.chatsentry.util.DebugLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AnvilListener implements Listener {

    private final LocalFilterLayer localFilter;
    private final DebugLogger logger;

    public AnvilListener(LocalFilterLayer localFilter, DebugLogger logger) {
        this.localFilter = localFilter;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("chatsentry.bypass")) {
            return;
        }

        final ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || !resultItem.hasItemMeta()) {
            return;
        }

        final ItemMeta meta = resultItem.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        final String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (displayName.isEmpty()) {
            return;
        }

        final LocalFilterLayer.FilterResult result = localFilter.check(displayName);
        if (result.flagged()) {
            logger.info("AnvilListener", player.getName() + " renamed item with flagged content: " + displayName);
            meta.displayName(null);
            resultItem.setItemMeta(meta);
            player.sendMessage(Component.text("\u00a7cThe item name contained inappropriate content and has been removed."));
        }
    }
}
