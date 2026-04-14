package io.github.driftn2forty.chatsentinel.storage;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PostgreSQLRepository implements PlayerRepository {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        final Thread t = new Thread(r, "ChatSentinel-PostgreSQL");
        t.setDaemon(true);
        return t;
    });
    private HikariDataSource dataSource;

    public PostgreSQLRepository(String host, int port, String database, String username, String password, int poolSize) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            final HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
            config.setPoolName("ChatSentinel-PostgreSQL");
            dataSource = new HikariDataSource(config);
            try (final Connection conn = dataSource.getConnection(); final Statement stmt = conn.createStatement()) {
                createTables(stmt);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize PostgreSQL database", e);
            }
        }, executor);
    }

    private void createTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_players (uuid CHAR(36) PRIMARY KEY, data TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_log (id BIGSERIAL PRIMARY KEY, uuid CHAR(36) NOT NULL, data TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_uuid ON chatsentinel_log (uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_created ON chatsentinel_log (created_at)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_debug (id BIGSERIAL PRIMARY KEY, level VARCHAR(8) NOT NULL, source VARCHAR(64) NOT NULL, message TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_debug_created ON chatsentinel_debug (created_at)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_chat (id BIGSERIAL PRIMARY KEY, uuid CHAR(36) NOT NULL, player_name VARCHAR(16) NOT NULL, source VARCHAR(8) NOT NULL, message TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_uuid ON chatsentinel_chat (uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_created ON chatsentinel_chat (created_at)");
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("SELECT data FROM chatsentinel_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (final ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return gson.fromJson(rs.getString("data"), PlayerData.class);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load player " + uuid, e);
            }
            return new PlayerData();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(UUID uuid, PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("INSERT INTO chatsentinel_players (uuid, data, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (uuid) DO UPDATE SET data = EXCLUDED.data, updated_at = CURRENT_TIMESTAMP")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gson.toJson(data));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save player " + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> logModeration(UUID uuid, ModerationEntry entry) {
        return CompletableFuture.runAsync(() -> {
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("INSERT INTO chatsentinel_log (uuid, data) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, gson.toJson(entry));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to log moderation for " + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ModerationEntry>> getModerationsForPlayer(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            final List<ModerationEntry> entries = new ArrayList<>();
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("SELECT data FROM chatsentinel_log WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (final ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(gson.fromJson(rs.getString("data"), ModerationEntry.class));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get moderations for " + uuid, e);
            }
            return entries;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> logChat(UUID uuid, String playerName, String source, String message) {
        return CompletableFuture.runAsync(() -> {
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("INSERT INTO chatsentinel_chat (uuid, player_name, source, message) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, source);
                ps.setString(4, message);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to log chat for " + uuid, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> logDebug(String level, String logSource, String message) {
        return CompletableFuture.runAsync(() -> {
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("INSERT INTO chatsentinel_debug (level, source, message) VALUES (?, ?, ?)")) {
                ps.setString(1, level);
                ps.setString(2, logSource);
                ps.setString(3, message);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to log debug message", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> purgeOldLogs(int days) {
        return purgeByDate("chatsentinel_log", "created_at", days);
    }

    @Override
    public CompletableFuture<Integer> purgeOldPlayers(int days) {
        return purgeByDate("chatsentinel_players", "updated_at", days);
    }

    @Override
    public CompletableFuture<Integer> purgeOldDebugLogs(int days) {
        return purgeByDate("chatsentinel_debug", "created_at", days);
    }

    @Override
    public CompletableFuture<Integer> purgeOldChatLogs(int days) {
        return purgeByDate("chatsentinel_chat", "created_at", days);
    }

    private CompletableFuture<Integer> purgeByDate(String table, String column, int days) {
        if (days <= 0) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> {
            final String cutoff = Instant.now().minus(days, ChronoUnit.DAYS).toString();
            try (final Connection conn = dataSource.getConnection(); final PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE " + column + " < ?")) {
                ps.setString(1, cutoff);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to purge " + table, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            executor.shutdown();
        });
    }
}
