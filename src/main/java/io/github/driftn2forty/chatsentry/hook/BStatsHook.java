package io.github.driftn2forty.chatsentry.hook;

import io.github.driftn2forty.chatsentry.ChatSentry;
import org.bstats.bukkit.Metrics;

public final class BStatsHook {

    private static final int BSTATS_PLUGIN_ID = 25055;

    public BStatsHook(ChatSentry plugin) {
        new Metrics(plugin, BSTATS_PLUGIN_ID);
    }
}
