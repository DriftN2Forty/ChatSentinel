package io.github.driftn2forty.chatsentinel.util;

import java.util.UUID;

public final class CommandPlaceholders {

    private CommandPlaceholders() {}

    public static String resolve(String command, String playerName, UUID uuid, double scoreBefore, double scoreAfter, String category, double moderationScore, String source, String action, long duration, int layer) {
        return command.replace("%player%", playerName).replace("%uuid%", uuid.toString()).replace("%score%", String.format("%.1f", scoreAfter)).replace("%score_before%", String.format("%.1f", scoreBefore)).replace("%category%", category).replace("%moderation_score%", String.format("%.2f", moderationScore)).replace("%source%", source).replace("%action%", action).replace("%duration%", String.valueOf(duration)).replace("%layer%", String.valueOf(layer));
    }
}
