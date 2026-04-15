package io.github.driftn2forty.chatsentry.command;

import io.github.driftn2forty.chatsentry.ChatSentry;
import io.github.driftn2forty.chatsentry.storage.ModerationEntry;
import io.github.driftn2forty.chatsentry.storage.PlayerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatSentryCommand implements CommandExecutor, TabCompleter {

    private final ChatSentry plugin;
    private final PlayerRepository repository;
    private final AtomicLong messagesProcessed = new AtomicLong();
    private final AtomicLong layer0Catches = new AtomicLong();
    private final AtomicLong layer1Calls = new AtomicLong();
    private final AtomicLong layer1Flagged = new AtomicLong();
    private final AtomicLong layer2Calls = new AtomicLong();
    private final AtomicLong layer2Confirmed = new AtomicLong();
    private final AtomicLong apiErrors = new AtomicLong();
    private volatile long lastErrorTime = 0;
    private final long startTime = System.currentTimeMillis();

    public ChatSentryCommand(ChatSentry plugin, PlayerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void recordMessage() { messagesProcessed.incrementAndGet(); }
    public void recordLayer0Catch() { layer0Catches.incrementAndGet(); }
    public void recordLayer1Call() { layer1Calls.incrementAndGet(); }
    public void recordLayer1Flagged() { layer1Flagged.incrementAndGet(); }
    public void recordLayer2Call() { layer2Calls.incrementAndGet(); }
    public void recordLayer2Confirmed() { layer2Confirmed.incrementAndGet(); }
    public void recordApiError() { apiErrors.incrementAndGet(); lastErrorTime = System.currentTimeMillis(); }

    public void resetCounters() {
        messagesProcessed.set(0);
        layer0Catches.set(0);
        layer1Calls.set(0);
        layer1Flagged.set(0);
        layer2Calls.set(0);
        layer2Confirmed.set(0);
        apiErrors.set(0);
        lastErrorTime = 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /chatsentry <reload|status|history|purge>", NamedTextColor.YELLOW));
            return true;
        }

        final String sub = args[0].toLowerCase();

        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "history" -> handleHistory(sender, args);
            case "purge" -> handlePurge(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("chatsentry.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        plugin.reload();
        resetCounters();
        sender.sendMessage(Component.text("ChatSentry configuration reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("chatsentry.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        final long totalMessages = messagesProcessed.get();
        final long l0 = layer0Catches.get();
        final long l1Total = layer1Calls.get();
        final long l1Flag = layer1Flagged.get();
        final long l2Total = layer2Calls.get();
        final long l2Conf = layer2Confirmed.get();
        final long errors = apiErrors.get();
        final long uptimeMs = System.currentTimeMillis() - startTime;
        final long uptimeHours = Math.max(1, uptimeMs / 3_600_000);
        final long messagesPerHour = totalMessages / uptimeHours;

        final double l0Pct = totalMessages > 0 ? (l0 * 100.0 / totalMessages) : 0;
        final double l1FlagPct = l1Total > 0 ? (l1Flag * 100.0 / l1Total) : 0;
        final double l2ConfPct = l2Total > 0 ? (l2Conf * 100.0 / l2Total) : 0;

        final String lastError;
        if (lastErrorTime == 0) {
            lastError = "none";
        } else {
            final long ago = (System.currentTimeMillis() - lastErrorTime) / 60_000;
            lastError = ago + "m ago";
        }

        final int activeMutes = countActiveMutes();

        sender.sendMessage(Component.text("ChatSentry v" + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(String.format("  Messages processed: %,d total (%,d/hr)", totalMessages, messagesPerHour), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format("  Layer 0 catches: %,d (%.1f%%)", l0, l0Pct), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format("  Layer 1 calls: %,d — flagged %,d (%.1f%%)", l1Total, l1Flag, l1FlagPct), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format("  Layer 2 calls: %,d — confirmed %,d (%.1f%%)", l2Total, l2Conf, l2ConfPct), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format("  API errors: %,d (last: %s)", errors, lastError), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Active mutes: " + activeMutes, NamedTextColor.GRAY));

        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatsentry.staff")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /chatsentry history <player>", NamedTextColor.YELLOW));
            return true;
        }

        final String targetName = args[1];
        final Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or not online: " + targetName, NamedTextColor.RED));
            return true;
        }

        final UUID uuid = target.getUniqueId();
        repository.getModerationsForPlayer(uuid, 10).thenAccept(entries -> {
            if (entries.isEmpty()) {
                sender.sendMessage(Component.text("No moderation history for " + targetName + ".", NamedTextColor.GRAY));
                return;
            }
            sender.sendMessage(Component.text("Recent flags for " + targetName + ":", NamedTextColor.GOLD));
            for (final ModerationEntry entry : entries) {
                final String line = String.format("  [L%d %s] %s — %s (score %.1f→%.1f)", entry.getLayer(), entry.getVerdict(), truncate(entry.getMessage(), 40), entry.getActionTaken(), entry.getPlayerScoreBefore(), entry.getPlayerScoreAfter());
                sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(Component.text("Error fetching history: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });

        return true;
    }

    private boolean handlePurge(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatsentry.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        final int days;
        if (args.length >= 2) {
            try {
                days = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid number: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else {
            days = 90;
        }

        if (days <= 0) {
            sender.sendMessage(Component.text("Days must be a positive number.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Purging logs older than " + days + " days...", NamedTextColor.YELLOW));
        repository.purgeOldLogs(days).thenAccept(count -> sender.sendMessage(Component.text("Purged " + count + " moderation log entries.", NamedTextColor.GREEN))).exceptionally(ex -> {
            sender.sendMessage(Component.text("Purge failed: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            final String partial = args[0].toLowerCase();
            for (final String sub : List.of("reload", "status", "history", "purge")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
            final String partial = args[1].toLowerCase();
            final List<String> completions = new ArrayList<>();
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }
        return List.of();
    }

    private int countActiveMutes() {
        int count = 0;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getMuteManager() != null && plugin.getMuteManager().isMuted(player.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
