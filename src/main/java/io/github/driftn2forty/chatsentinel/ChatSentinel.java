package io.github.driftn2forty.chatsentinel;

import org.bukkit.plugin.java.JavaPlugin;

public final class ChatSentinel extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("ChatSentinel enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChatSentinel disabled.");
    }
}
