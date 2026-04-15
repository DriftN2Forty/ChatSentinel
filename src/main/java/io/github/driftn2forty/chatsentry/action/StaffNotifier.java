package io.github.driftn2forty.chatsentry.action;

import io.github.driftn2forty.chatsentry.util.DebugLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class StaffNotifier {

    private final String staffPermission;
    private final DebugLogger logger;

    public StaffNotifier(String staffPermission, DebugLogger logger) {
        this.staffPermission = staffPermission;
        this.logger = logger;
    }

    public void notifyStaff(String playerName, String message, String verdict, double score) {
        final String alertText = "&e[ChatSentry] &c" + playerName + " &7flagged (&e" + verdict + "&7, score " + String.format("%.1f", score) + "): &f" + truncate(message, 80);
        final Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(alertText);

        int notified = 0;
        for (final Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(staffPermission)) {
                staff.sendMessage(component);
                notified++;
            }
        }
        logger.debug("StaffNotifier", "Notified " + notified + " staff about " + playerName);
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
