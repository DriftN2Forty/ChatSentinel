package io.github.driftn2forty.chatsentinel.hook;

import io.github.driftn2forty.chatsentinel.ChatSentinel;
import org.bstats.bukkit.Metrics;

public final class BStatsHook {

    private static final int BSTATS_PLUGIN_ID = 25055;

    public BStatsHook(ChatSentinel plugin) {
        new Metrics(plugin, BSTATS_PLUGIN_ID);
    }
}
