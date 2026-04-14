package io.github.driftn2forty.chatsentinel.config;

import io.github.driftn2forty.chatsentinel.action.EscalationEngine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginConfig {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final FileConfiguration config;

    public PluginConfig(FileConfiguration config) {
        this.config = config;
    }

    // ── Layer 1 ──────────────────────────────────────────────────────
    public boolean isLayer1Enabled() { return config.getBoolean("layer1.enabled", false); }
    public String getLayer1BaseUrl() { return config.getString("layer1.base-url", "https://api.openai.com"); }
    public String getLayer1ApiKey() { return resolveEnvVars(config.getString("layer1.api-key", "")); }
    public String getLayer1Model() { return config.getString("layer1.model", "omni-moderation-latest"); }

    // ── Layer 2 ──────────────────────────────────────────────────────
    public boolean isLayer2Enabled() { return config.getBoolean("layer2.enabled", false); }
    public String getLayer2BaseUrl() { return config.getString("layer2.base-url", "https://api.openai.com"); }
    public String getLayer2ApiKey() { return resolveEnvVars(config.getString("layer2.api-key", "")); }
    public String getLayer2Model() { return config.getString("layer2.model", "gpt-4o"); }
    public String getLayer2SystemPrompt() { return config.getString("layer2.system-prompt", ""); }
    public int getLayer2MaxTokens() { return config.getInt("layer2.max-tokens", 150); }
    public double getLayer2Temperature() { return config.getDouble("layer2.temperature", 0.0); }

    // ── Filter ───────────────────────────────────────────────────────
    public boolean isFilterEnabled() { return config.getBoolean("filter.enabled", true); }

    public List<String> getFilterLanguages() {
        final Object raw = config.get("filter.languages", "*");
        if (raw instanceof String s && "*".equals(s)) {
            return List.of("ar", "cs", "da", "de", "en", "eo", "es", "fa", "fi", "fil", "fr", "fr-CA-u-sd-caqc", "hi", "hu", "it", "ja", "kab", "ko", "nl", "no", "pl", "pt", "ru", "sv", "th", "tlh", "tr", "zh");
        }
        return config.getStringList("filter.languages");
    }

    public List<String> getCustomWords() { return config.getStringList("filter.custom-words"); }

    public Set<String> getWhitelist() { return new HashSet<>(config.getStringList("filter.whitelist")); }

    public boolean isLeetSpeakEnabled() { return config.getBoolean("filter.leet-speak", true); }
    public boolean isAbbreviationsEnabled() { return config.getBoolean("filter.abbreviations", true); }

    public Map<String, String> getCustomAbbreviations() {
        final Map<String, String> map = new HashMap<>();
        final ConfigurationSection section = config.getConfigurationSection("filter.custom-abbreviations");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                map.put(key, section.getString(key, ""));
            }
        }
        return map;
    }

    // ── Pipeline ─────────────────────────────────────────────────────
    public double getLayer1Threshold() { return config.getDouble("pipeline.layer1-threshold", 0.7); }
    public boolean isAsync() { return config.getBoolean("pipeline.async", true); }
    public long getTimeoutMs() { return config.getLong("pipeline.timeout-ms", 3000); }
    public boolean isFailOpen() { return config.getBoolean("pipeline.fail-open", true); }
    public String getMessageMode() { return config.getString("pipeline.message-mode", "block"); }
    public int getRetryMaxAttempts() { return config.getInt("pipeline.retry.max-attempts", 3); }
    public long getRetryBaseDelayMs() { return config.getLong("pipeline.retry.base-delay-ms", 500); }
    public long getRetryMaxDelayMs() { return config.getLong("pipeline.retry.max-delay-ms", 5000); }

    // ── Actions ──────────────────────────────────────────────────────
    public String getWarnMessage() { return config.getString("actions.warn.message", "&cYour message was flagged. Please keep chat respectful."); }
    public long getMuteDurationSeconds() { return config.getLong("actions.mute.duration-seconds", 300); }
    public String getMuteMessage() { return config.getString("actions.mute.message", "&cYou have been muted for 5 minutes."); }
    public String getStaffPermission() { return config.getString("actions.escalate.staff-permission", "chatsentinel.staff"); }
    public boolean isEscalateLogToFile() { return config.getBoolean("actions.escalate.log-to-file", true); }

    // ── Escalation ───────────────────────────────────────────────────
    public boolean isEscalationEnabled() { return config.getBoolean("escalation.enabled", true); }
    public double getScoreWeightWarn() { return config.getDouble("escalation.score-weights.warn", 1); }
    public double getScoreWeightMute() { return config.getDouble("escalation.score-weights.mute", 3); }
    public double getScoreWeightEscalate() { return config.getDouble("escalation.score-weights.escalate", 5); }
    public double getDecayPointsPerDay() { return config.getDouble("escalation.decay.points-per-day", 0.5); }
    public double getDecayMinScore() { return config.getDouble("escalation.decay.min-score", 0); }

    public List<EscalationEngine.Threshold> getEscalationThresholds() {
        final List<EscalationEngine.Threshold> thresholds = new ArrayList<>();
        final List<Map<?, ?>> list = config.getMapList("escalation.thresholds");
        for (final Map<?, ?> entry : list) {
            final double score = ((Number) entry.get("score")).doubleValue();
            final String action = (String) entry.get("action");
            final long duration = entry.containsKey("duration-seconds") ? ((Number) entry.get("duration-seconds")).longValue() : 0;
            thresholds.add(new EscalationEngine.Threshold(score, action, duration));
        }
        return thresholds;
    }

    // ── Storage ──────────────────────────────────────────────────────
    public String getStorageBackend() { return config.getString("storage.backend", "sqlite"); }
    public String getSqliteFile() { return config.getString("storage.sqlite.file", "chatsentinel.db"); }
    public String getMysqlHost() { return config.getString("storage.mysql.host", "localhost"); }
    public int getMysqlPort() { return config.getInt("storage.mysql.port", 3306); }
    public String getMysqlDatabase() { return config.getString("storage.mysql.database", "chatsentinel"); }
    public String getMysqlUsername() { return resolveEnvVars(config.getString("storage.mysql.username", "")); }
    public String getMysqlPassword() { return resolveEnvVars(config.getString("storage.mysql.password", "")); }
    public int getMysqlPoolSize() { return config.getInt("storage.mysql.pool-size", 5); }
    public String getPostgresqlHost() { return config.getString("storage.postgresql.host", "localhost"); }
    public int getPostgresqlPort() { return config.getInt("storage.postgresql.port", 5432); }
    public String getPostgresqlDatabase() { return config.getString("storage.postgresql.database", "chatsentinel"); }
    public String getPostgresqlUsername() { return resolveEnvVars(config.getString("storage.postgresql.username", "")); }
    public String getPostgresqlPassword() { return resolveEnvVars(config.getString("storage.postgresql.password", "")); }
    public int getPostgresqlPoolSize() { return config.getInt("storage.postgresql.pool-size", 5); }

    // ── History ──────────────────────────────────────────────────────
    public int getMessagesPerPlayer() { return config.getInt("history.messages-per-player", 10); }
    public int getContextWindow() { return config.getInt("history.context-window", 25); }
    public boolean isIncludeWhispers() { return config.getBoolean("history.include-whispers", true); }
    public boolean isIncludeScores() { return config.getBoolean("history.include-scores", true); }
    public int getContextRadius() { return config.getInt("history.context-radius", -1); }

    // ── Chat Log ─────────────────────────────────────────────────────
    public boolean isChatLogEnabled() { return config.getBoolean("chat-log.enabled", false); }
    public int getChatLogTtlDays() { return config.getInt("chat-log.ttl-days", 30); }

    // ── Retention ────────────────────────────────────────────────────
    public int getLogTtlDays() { return config.getInt("retention.log-ttl-days", 90); }
    public int getPlayerTtlDays() { return config.getInt("retention.player-ttl-days", 180); }
    public int getDebugTtlDays() { return config.getInt("retention.debug-ttl-days", 7); }
    public int getPurgeIntervalHours() { return config.getInt("retention.purge-interval-hours", 24); }

    // ── Rate Limit ───────────────────────────────────────────────────
    public double getRequestsPerSecond() { return config.getDouble("rate-limit.requests-per-second", 20); }

    // ── Logging ──────────────────────────────────────────────────────
    public boolean isLogToConsole() { return config.getBoolean("logging.log-to-console", true); }
    public boolean isLogToFile() { return config.getBoolean("logging.log-to-file", true); }
    public boolean isLogToDatabase() { return config.getBoolean("logging.log-to-database", false); }
    public boolean isDebugEnabled() { return config.getBoolean("logging.debug.enabled", false); }
    public boolean isVerboseLayers() { return config.getBoolean("logging.debug.verbose-layers", false); }
    public boolean isVerboseTrie() { return config.getBoolean("logging.debug.verbose-trie", false); }

    private static String resolveEnvVars(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        final Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        final StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            final String envName = matcher.group(1);
            final String envValue = System.getenv(envName);
            matcher.appendReplacement(result, envValue != null ? Matcher.quoteReplacement(envValue) : "");
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
