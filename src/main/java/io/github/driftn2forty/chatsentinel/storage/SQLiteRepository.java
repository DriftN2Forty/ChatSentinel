package io.github.driftn2forty.chatsentinel.storage;

import com.google.gson.Gson;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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

public final class SQLiteRepository implements PlayerRepository {

    private final Path dbFile;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "ChatSentinel-SQLite");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public SQLiteRepository(Path dbFile) {
        this.dbFile = dbFile;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
                connection.setAutoCommit(true);
                try (final Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA foreign_keys=OFF");
                    createTables(stmt);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize SQLite database", e);
            }
        }, executor);
    }

    private void createTables(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_players (uuid CHAR(36) PRIMARY KEY, data TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_log (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid CHAR(36) NOT NULL, data TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_uuid ON chatsentinel_log (uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_created ON chatsentinel_log (created_at)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_debug (id INTEGER PRIMARY KEY AUTOINCREMENT, level VARCHAR(8) NOT NULL, source VARCHAR(64) NOT NULL, message TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_debug_created ON chatsentinel_debug (created_at)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chatsentinel_chat (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid CHAR(36) NOT NULL, player_name VARCHAR(16) NOT NULL, source VARCHAR(8) NOT NULL, message TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_uuid ON chatsentinel_chat (uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_created ON chatsentinel_chat (created_at)");
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (final PreparedStatement ps = connection.prepareStatement("SELECT data FROM chatsentinel_players WHERE uuid = ?")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO chatsentinel_players (uuid, data, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated_at = CURRENT_TIMESTAMP")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO chatsentinel_log (uuid, data) VALUES (?, ?)")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("SELECT data FROM chatsentinel_log WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO chatsentinel_chat (uuid, player_name, source, message) VALUES (?, ?, ?, ?)")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("INSERT INTO chatsentinel_debug (level, source, message) VALUES (?, ?, ?)")) {
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
            try (final PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE " + column + " < ?")) {
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
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close SQLite connection", e);
            } finally {
                executor.shutdown();
            }
        }, executor);
    }
}
