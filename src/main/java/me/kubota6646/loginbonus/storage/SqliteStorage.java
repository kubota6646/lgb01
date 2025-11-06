package me.kubota6646.loginbonus.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SqliteStorage implements StorageInterface {
    
    private final JavaPlugin plugin;
    private Connection connection;
    private final File dbFile;
    
    public SqliteStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "playerdata.db");
    }
    
    @Override
    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("データフォルダの作成に失敗しました");
                }
            }
            
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            // テーブルを作成
            String createTable = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY," +
                    "cumulative REAL DEFAULT 0.0," +
                    "last_reward TEXT," +
                    "streak INTEGER DEFAULT 1," +
                    "last_streak_date TEXT," +
                    "last_sync INTEGER DEFAULT 0" +
                    ");";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTable);
            }
            
            // 既存テーブルにlast_syncカラムを追加（存在しない場合）
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(player_data)")) {
                boolean hasLastSync = false;
                while (rs.next()) {
                    if ("last_sync".equals(rs.getString("name"))) {
                        hasLastSync = true;
                        break;
                    }
                }
                if (!hasLastSync) {
                    stmt.execute("ALTER TABLE player_data ADD COLUMN last_sync INTEGER DEFAULT 0");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("last_syncカラムの確認/追加に失敗しました: " + e.getMessage());
            }
            
            plugin.getLogger().info("SQLiteデータベースを初期化しました: " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLiteデータベースの初期化に失敗しました: " + e.getMessage());
            plugin.getLogger().severe("スタックトレース: " + java.util.Arrays.toString(e.getStackTrace()));
        }
    }
    
    @Override
    public synchronized double getCumulative(UUID playerId) {
        String sql = "SELECT cumulative FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("cumulative");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("累積時間の取得に失敗しました: " + e.getMessage());
        }
        return 0.0;
    }
    
    @Override
    public synchronized void setCumulative(UUID playerId, double cumulative) {
        String sql = "INSERT INTO player_data (uuid, cumulative) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET cumulative = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setDouble(2, cumulative);
            pstmt.setDouble(3, cumulative);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("累積時間の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized String getLastReward(UUID playerId) {
        String sql = "SELECT last_reward FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_reward");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終報酬日の取得に失敗しました: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public synchronized void setLastReward(UUID playerId, String lastReward) {
        String sql = "INSERT INTO player_data (uuid, last_reward) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_reward = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, lastReward);
            pstmt.setString(3, lastReward);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("最終報酬日の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized int getStreak(UUID playerId) {
        String sql = "SELECT streak FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("streak");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("ストリークの取得に失敗しました: " + e.getMessage());
        }
        return 1;
    }
    
    @Override
    public synchronized void setStreak(UUID playerId, int streak) {
        String sql = "INSERT INTO player_data (uuid, streak) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET streak = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setInt(2, streak);
            pstmt.setInt(3, streak);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("ストリークの設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized String getLastStreakDate(UUID playerId) {
        String sql = "SELECT last_streak_date FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("last_streak_date");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終ストリーク日の取得に失敗しました: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public synchronized void setLastStreakDate(UUID playerId, String lastStreakDate) {
        String sql = "INSERT INTO player_data (uuid, last_streak_date) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_streak_date = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, lastStreakDate);
            pstmt.setString(3, lastStreakDate);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("最終ストリーク日の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<Void> saveAsync() {
        // SQLiteは各操作ごとに自動的にコミットされる
        // 明示的な保存は不要
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLiteデータベース接続を閉じました");
            } catch (SQLException e) {
                plugin.getLogger().severe("SQLiteデータベース接続のクローズに失敗しました: " + e.getMessage());
            }
        }
    }
    
    @Override
    public synchronized long getLastSync(UUID playerId) {
        String sql = "SELECT last_sync FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_sync");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終同期日時の取得に失敗しました: " + e.getMessage());
        }
        return 0L;
    }
    
    @Override
    public synchronized void setLastSync(UUID playerId, long lastSync) {
        String sql = "INSERT INTO player_data (uuid, last_sync) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET last_sync = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setLong(2, lastSync);
            pstmt.setLong(3, lastSync);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("最終同期日時の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public boolean syncPlayerData(UUID playerId) {
        // SQLiteは単一サーバー用のため、同期は不要
        return false;
    }
    
    @Override
    public synchronized boolean deletePlayerData(UUID playerId) {
        String sql = "DELETE FROM player_data WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("プレイヤーデータの削除に失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public synchronized boolean deleteAllPlayerData() {
        String sql = "DELETE FROM player_data";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("全プレイヤーデータの削除に失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public synchronized java.util.List<UUID> getAllPlayerUUIDs() {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        String sql = "SELECT uuid FROM player_data";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    uuids.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID: " + rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("プレイヤーUUIDリストの取得に失敗しました: " + e.getMessage());
        }
        return uuids;
    }
}
