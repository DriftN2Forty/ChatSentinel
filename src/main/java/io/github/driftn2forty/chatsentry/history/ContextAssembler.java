package io.github.driftn2forty.chatsentry.history;

import io.github.driftn2forty.chatsentry.storage.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ContextAssembler {

    private final PlayerHistoryTracker tracker;
    private final int contextWindow;
    private final boolean includeScores;
    private final Function<UUID, PlayerData> playerDataLoader;

    public ContextAssembler(PlayerHistoryTracker tracker, int contextWindow, boolean includeScores, Function<UUID, PlayerData> playerDataLoader) {
        this.tracker = tracker;
        this.contextWindow = contextWindow;
        this.includeScores = includeScores;
        this.playerDataLoader = playerDataLoader;
    }

    public String assemble(List<UUID> nearbyPlayers) {
        final List<ChatMessage> allMessages = new ArrayList<>();
        final Map<String, Double> playerScores = new java.util.HashMap<>();

        for (final UUID uuid : nearbyPlayers) {
            final List<ChatMessage> history = tracker.getHistory(uuid);
            allMessages.addAll(history);

            if (includeScores && !history.isEmpty()) {
                final PlayerData data = playerDataLoader.apply(uuid);
                if (data != null) {
                    playerScores.put(history.get(0).senderName(), data.getScore());
                }
            }
        }

        allMessages.sort(Comparator.comparing(ChatMessage::timestamp));

        final List<ChatMessage> windowed;
        if (allMessages.size() > contextWindow) {
            windowed = allMessages.subList(allMessages.size() - contextWindow, allMessages.size());
        } else {
            windowed = allMessages;
        }

        final StringBuilder sb = new StringBuilder();

        if (includeScores && !playerScores.isEmpty()) {
            sb.append("Player scores:\n");
            for (final Map.Entry<String, Double> entry : playerScores.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(String.format("%.1f", entry.getValue())).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Chat history:\n");
        for (final ChatMessage msg : windowed) {
            final String prefix = msg.type() == ChatMessage.Type.WHISPER ? "[whisper to " + msg.targetName() + "] " : "";
            sb.append("[").append(msg.senderName()).append("] ").append(prefix).append(msg.text()).append("\n");
        }

        return sb.toString();
    }
}
