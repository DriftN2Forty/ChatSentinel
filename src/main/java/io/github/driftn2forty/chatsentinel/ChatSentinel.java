package io.github.driftn2forty.chatsentinel;

import io.github.driftn2forty.chatsentinel.action.ActionDispatcher;
import io.github.driftn2forty.chatsentinel.action.EscalationEngine;
import io.github.driftn2forty.chatsentinel.action.MuteManager;
import io.github.driftn2forty.chatsentinel.action.ScoreCalculator;
import io.github.driftn2forty.chatsentinel.action.StaffNotifier;
import io.github.driftn2forty.chatsentinel.command.ChatSentinelCommand;
import io.github.driftn2forty.chatsentinel.config.PluginConfig;
import io.github.driftn2forty.chatsentinel.filter.AbbreviationExpander;
import io.github.driftn2forty.chatsentinel.filter.ChatNormalizer;
import io.github.driftn2forty.chatsentinel.filter.LocalFilterLayer;
import io.github.driftn2forty.chatsentinel.filter.ProfanityTrie;
import io.github.driftn2forty.chatsentinel.history.ChatLogWriter;
import io.github.driftn2forty.chatsentinel.history.PlayerHistoryTracker;
import io.github.driftn2forty.chatsentinel.hook.BStatsHook;
import io.github.driftn2forty.chatsentinel.hook.PlaceholderAPIHook;
import io.github.driftn2forty.chatsentinel.listener.AnvilListener;
import io.github.driftn2forty.chatsentinel.listener.BookListener;
import io.github.driftn2forty.chatsentinel.listener.ChatListener;
import io.github.driftn2forty.chatsentinel.listener.SignListener;
import io.github.driftn2forty.chatsentinel.listener.WhisperListener;
import io.github.driftn2forty.chatsentinel.moderation.ModerationPipeline;
import io.github.driftn2forty.chatsentinel.moderation.layer1.OpenAIModerationClient;
import io.github.driftn2forty.chatsentinel.moderation.layer2.LLMReviewClient;
import io.github.driftn2forty.chatsentinel.storage.MySQLRepository;
import io.github.driftn2forty.chatsentinel.storage.PlayerRepository;
import io.github.driftn2forty.chatsentinel.storage.PostgreSQLRepository;
import io.github.driftn2forty.chatsentinel.storage.RetentionPurger;
import io.github.driftn2forty.chatsentinel.storage.SQLiteRepository;
import io.github.driftn2forty.chatsentinel.util.DebugLogger;
import io.github.driftn2forty.chatsentinel.util.HttpUtil;
import io.github.driftn2forty.chatsentinel.util.RateLimiter;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ChatSentinel extends JavaPlugin {

    private PluginConfig pluginConfig;
    private DebugLogger debugLogger;
    private PlayerRepository repository;
    private HttpUtil httpUtil;
    private RetentionPurger retentionPurger;
    private MuteManager muteManager;
    private ChatSentinelCommand commandHandler;
    private PlaceholderAPIHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bootstrap();
    }

    @Override
    public void onDisable() {
        shutdown();
    }

    public void reload() {
        shutdown();
        reloadConfig();
        bootstrap();
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    private void bootstrap() {
        pluginConfig = new PluginConfig(getConfig());
        debugLogger = new DebugLogger(getLogger(), getDataFolder().toPath(), pluginConfig.isLogToConsole(), pluginConfig.isLogToFile(), pluginConfig.isDebugEnabled());
        httpUtil = new HttpUtil();

        // ── Storage ──────────────────────────────────────────────────
        repository = createRepository();
        repository.initialize().join();

        // ── Filter (Layer 0) ─────────────────────────────────────────
        final LocalFilterLayer localFilter = buildLocalFilter();

        // ── Rate Limiter ─────────────────────────────────────────────
        final RateLimiter rateLimiter = new RateLimiter(pluginConfig.getRequestsPerSecond());

        // ── Layer 1 ──────────────────────────────────────────────────
        boolean layer1Active = pluginConfig.isLayer1Enabled();
        OpenAIModerationClient layer1Client = null;
        if (layer1Active) {
            final String apiKey = pluginConfig.getLayer1ApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                getLogger().warning("Layer 1 enabled but api-key is not set — disabling Layer 1.");
                layer1Active = false;
            } else {
                layer1Client = new OpenAIModerationClient(pluginConfig.getLayer1BaseUrl(), apiKey, pluginConfig.getLayer1Model(), pluginConfig.getLayer1Threshold(), pluginConfig.getLayer1CategoryThresholds(), httpUtil, rateLimiter, debugLogger);
            }
        }
        if (!layer1Active) {
            getLogger().info("Layer 1 disabled — only Layer 0 (local filter) is active.");
        }

        // ── Layer 2 ──────────────────────────────────────────────────
        boolean layer2Active = pluginConfig.isLayer2Enabled();
        LLMReviewClient layer2Client = null;
        if (layer2Active) {
            final String apiKey = pluginConfig.getLayer2ApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                getLogger().warning("Layer 2 enabled but api-key is not set — disabling Layer 2.");
                layer2Active = false;
            } else {
                layer2Client = new LLMReviewClient(pluginConfig.getLayer2BaseUrl(), apiKey, pluginConfig.getLayer2Model(), pluginConfig.getLayer2SystemPrompt(), pluginConfig.getLayer2MaxTokens(), pluginConfig.getLayer2Temperature(), httpUtil, rateLimiter, debugLogger);
            }
        }
        if (!layer2Active) {
            getLogger().info("Layer 2 disabled.");
        }
        if (layer2Active && !layer1Active) {
            getLogger().warning("Layer 2 is enabled without Layer 1. Every message passing Layer 0 will be sent to the LLM endpoint — this may incur significant API costs and latency. Consider enabling Layer 1 as a pre-filter.");
        }

        // ── Pipeline ─────────────────────────────────────────────────
        final ModerationPipeline pipeline = new ModerationPipeline(localFilter, layer1Client, layer2Client, layer1Active, layer2Active, pluginConfig.getLayer1Threshold(), pluginConfig.getTimeoutMs(), pluginConfig.isFailOpen(), pluginConfig.getRetryMaxAttempts(), pluginConfig.getRetryBaseDelayMs(), pluginConfig.getRetryMaxDelayMs(), pluginConfig.getMessageMode(), debugLogger);

        // ── Action / Escalation ──────────────────────────────────────
        final ScoreCalculator scoreCalculator = new ScoreCalculator(pluginConfig.getScoreWeightWarn(), pluginConfig.getScoreWeightMute(), pluginConfig.getScoreWeightEscalate(), pluginConfig.getDecayPointsPerDay(), pluginConfig.getDecayMinScore(), pluginConfig.getLayer1CategoryWeights());
        final EscalationEngine escalationEngine = new EscalationEngine(pluginConfig.getEscalationThresholds());
        muteManager = new MuteManager(repository, debugLogger);
        final StaffNotifier staffNotifier = new StaffNotifier(pluginConfig.getStaffPermission(), debugLogger);
        final ActionDispatcher actionDispatcher = new ActionDispatcher(scoreCalculator, escalationEngine, muteManager, staffNotifier, repository, debugLogger, pluginConfig.getWarnMessage(), pluginConfig.getMuteMessage(), pluginConfig.getMuteDurationSeconds(), this, pluginConfig.getLayer1CategoryCommands());

        // ── History ──────────────────────────────────────────────────
        final PlayerHistoryTracker historyTracker = new PlayerHistoryTracker(pluginConfig.getMessagesPerPlayer(), pluginConfig.isIncludeWhispers());
        final ChatLogWriter chatLogWriter = new ChatLogWriter(repository, pluginConfig.isChatLogEnabled(), debugLogger);

        // ── Listeners ────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new ChatListener(pipeline, actionDispatcher, muteManager, repository, historyTracker, chatLogWriter, debugLogger, pluginConfig.getMessageMode()), this);
        getServer().getPluginManager().registerEvents(new WhisperListener(pipeline, actionDispatcher, muteManager, repository, historyTracker, chatLogWriter, debugLogger, pluginConfig.getMessageMode()), this);
        getServer().getPluginManager().registerEvents(new SignListener(localFilter, debugLogger), this);
        getServer().getPluginManager().registerEvents(new BookListener(pipeline, actionDispatcher, muteManager, repository, debugLogger), this);
        getServer().getPluginManager().registerEvents(new AnvilListener(localFilter, debugLogger), this);

        // ── Command ──────────────────────────────────────────────────
        commandHandler = new ChatSentinelCommand(this, repository, debugLogger);
        final PluginCommand cmd = getCommand("chatsentinel");
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }

        // ── Retention Purger ─────────────────────────────────────────
        retentionPurger = new RetentionPurger(repository, pluginConfig.getLogTtlDays(), pluginConfig.getPlayerTtlDays(), pluginConfig.getDebugTtlDays(), pluginConfig.getChatLogTtlDays(), pluginConfig.getPurgeIntervalHours(), debugLogger);
        retentionPurger.start();

        // ── Integrations ─────────────────────────────────────────────
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderHook = new PlaceholderAPIHook(this, muteManager, repository);
            placeholderHook.register();
            debugLogger.info("ChatSentinel", "PlaceholderAPI integration enabled.");
        }

        new BStatsHook(this);

        getLogger().info("ChatSentinel enabled.");
    }

    private void shutdown() {
        HandlerList.unregisterAll(this);

        if (retentionPurger != null) {
            retentionPurger.stop();
            retentionPurger = null;
        }

        if (placeholderHook != null) {
            placeholderHook.unregister();
            placeholderHook = null;
        }

        if (httpUtil != null) {
            httpUtil.shutdown();
            httpUtil = null;
        }

        if (repository != null) {
            repository.shutdown().join();
            repository = null;
        }

        getLogger().info("ChatSentinel disabled.");
    }

    private PlayerRepository createRepository() {
        final String backend = pluginConfig.getStorageBackend();
        return switch (backend.toLowerCase()) {
            case "mysql" -> new MySQLRepository(pluginConfig.getMysqlHost(), pluginConfig.getMysqlPort(), pluginConfig.getMysqlDatabase(), pluginConfig.getMysqlUsername(), pluginConfig.getMysqlPassword(), pluginConfig.getMysqlPoolSize());
            case "postgresql" -> new PostgreSQLRepository(pluginConfig.getPostgresqlHost(), pluginConfig.getPostgresqlPort(), pluginConfig.getPostgresqlDatabase(), pluginConfig.getPostgresqlUsername(), pluginConfig.getPostgresqlPassword(), pluginConfig.getPostgresqlPoolSize());
            default -> new SQLiteRepository(Path.of(getDataFolder().getAbsolutePath(), pluginConfig.getSqliteFile()));
        };
    }

    private LocalFilterLayer buildLocalFilter() {
        try {
            final ProfanityTrie trie = LocalFilterLayer.buildTrie(pluginConfig.getFilterLanguages(), pluginConfig.getWhitelist());

            for (final String word : pluginConfig.getCustomWords()) {
                trie.insert(word.toLowerCase());
            }

            debugLogger.info("ChatSentinel", "Loaded " + trie.size() + " words into trie filter.");

            final ChatNormalizer normalizer = new ChatNormalizer();

            final Map<String, String> abbreviations = loadAbbreviations();
            abbreviations.putAll(pluginConfig.getCustomAbbreviations());
            final AbbreviationExpander expander = new AbbreviationExpander(abbreviations);

            return new LocalFilterLayer(trie, normalizer, expander, pluginConfig.isLeetSpeakEnabled(), pluginConfig.isAbbreviationsEnabled());
        } catch (IOException e) {
            getLogger().severe("Failed to build local filter: " + e.getMessage());
            return new LocalFilterLayer(new ProfanityTrie(), new ChatNormalizer(), new AbbreviationExpander(Map.of()), false, false);
        }
    }

    private Map<String, String> loadAbbreviations() {
        final Map<String, String> map = new HashMap<>();
        try (final InputStream is = getClass().getClassLoader().getResourceAsStream("abbreviations.txt")) {
            if (is == null) {
                debugLogger.warn("ChatSentinel", "abbreviations.txt not found in resources.");
                return map;
            }
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    final int eq = trimmed.indexOf('=');
                    if (eq > 0 && eq < trimmed.length() - 1) {
                        map.put(trimmed.substring(0, eq).trim().toLowerCase(), trimmed.substring(eq + 1).trim());
                    }
                }
            }
        } catch (IOException e) {
            debugLogger.error("ChatSentinel", "Failed to load abbreviations: " + e.getMessage());
        }
        return map;
    }
}
