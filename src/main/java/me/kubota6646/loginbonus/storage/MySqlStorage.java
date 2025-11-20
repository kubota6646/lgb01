package me.kubota6646.loginbonus.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MySqlStorage implements StorageInterface {
    
    private final JavaPlugin plugin;
    private Connection connection;
    private final String host;
    private final int port;
    private final String database;
    private final String tableName;
    private final String username;
    private final String password;
    
    public MySqlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("mysql");
        
        if (mysqlConfig == null) {
            plugin.getLogger().severe("MySQL設定が見つかりません。config.ymlを確認してください。");
            this.host = "localhost";
            this.port = 3306;
            this.database = "loginbonus";
            this.tableName = "player_data";
            this.username = "root";
            this.password = "password";
        } else {
            this.host = mysqlConfig.getString("host", "localhost");
            this.port = mysqlConfig.getInt("port", 3306);
            this.database = mysqlConfig.getString("database", "loginbonus");
            this.tableName = mysqlConfig.getString("table-name", "player_data");
            this.username = mysqlConfig.getString("username", "root");
            this.password = mysqlConfig.getString("password", "password");
        }
    }
    
    @Override
    public void initialize() {
        try {
            // MySQL接続URLを構築
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    host, port, database);
            
            // 接続を確立
            connection = DriverManager.getConnection(url, username, password);
            
            // テーブルを作成
            String createTable = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "cumulative DOUBLE DEFAULT 0.0," +
                    "last_reward VARCHAR(20)," +
                    "streak INT DEFAULT 1," +
                    "last_streak_date VARCHAR(20)," +
                    "last_sync BIGINT DEFAULT 0," +
                    "INDEX idx_last_sync (last_sync)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTable);
                
                // 既存のテーブルのカラムサイズを更新（VARCHAR(10) → VARCHAR(20)）
                try {
                    stmt.execute("ALTER TABLE " + tableName + " MODIFY COLUMN last_reward VARCHAR(20)");
                    stmt.execute("ALTER TABLE " + tableName + " MODIFY COLUMN last_streak_date VARCHAR(20)");
                    plugin.getLogger().info("MySQLテーブルのカラムサイズを更新しました");
                } catch (SQLException e) {
                    // カラムがすでに正しいサイズか、テーブルが新規作成された場合は無視
                    plugin.getLogger().fine("カラムサイズの更新をスキップしました: " + e.getMessage());
                }
            }
            
            plugin.getLogger().info("MySQLデータベースに接続しました");
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQLデータベースの初期化に失敗しました: " + e.getMessage());
            plugin.getLogger().severe("データベースの接続情報とデータベースが存在することを確認してください。");
            plugin.getLogger().severe("スタックトレース: " + java.util.Arrays.toString(e.getStackTrace()));
        }
    }
    
    private void reconnectIfNeeded() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(5)) {
            plugin.getLogger().warning("MySQL接続が切断されました。再接続を試みます...");
            initialize();
        }
    }
    
    @Override
    public synchronized double getCumulative(UUID playerId) {
        String sql = "SELECT cumulative FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("cumulative");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("累積時間の取得に失敗しました: " + e.getMessage());
        }
        return 0.0;
    }
    
    @Override
    public synchronized void setCumulative(UUID playerId, double cumulative) {
        String sql = "INSERT INTO " + tableName + " (uuid, cumulative, last_sync) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE cumulative = ?, last_sync = ?";
        try {
            reconnectIfNeeded();
            long currentTime = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setDouble(2, cumulative);
                pstmt.setLong(3, currentTime);
                pstmt.setDouble(4, cumulative);
                pstmt.setLong(5, currentTime);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("累積時間の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized String getLastReward(UUID playerId) {
        String sql = "SELECT last_reward FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("last_reward");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終報酬日の取得に失敗しました: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public synchronized void setLastReward(UUID playerId, String lastReward) {
        String sql = "INSERT INTO " + tableName + " (uuid, last_reward, last_sync) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE last_reward = ?, last_sync = ?";
        try {
            reconnectIfNeeded();
            long currentTime = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, lastReward);
                pstmt.setLong(3, currentTime);
                pstmt.setString(4, lastReward);
                pstmt.setLong(5, currentTime);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終報酬日の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized int getStreak(UUID playerId) {
        String sql = "SELECT streak FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("streak");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("ストリークの取得に失敗しました: " + e.getMessage());
        }
        return 1;
    }
    
    @Override
    public synchronized void setStreak(UUID playerId, int streak) {
        String sql = "INSERT INTO " + tableName + " (uuid, streak, last_sync) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE streak = ?, last_sync = ?";
        try {
            reconnectIfNeeded();
            long currentTime = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setInt(2, streak);
                pstmt.setLong(3, currentTime);
                pstmt.setInt(4, streak);
                pstmt.setLong(5, currentTime);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("ストリークの設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized String getLastStreakDate(UUID playerId) {
        String sql = "SELECT last_streak_date FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("last_streak_date");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終ストリーク日の取得に失敗しました: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public synchronized void setLastStreakDate(UUID playerId, String lastStreakDate) {
        String sql = "INSERT INTO " + tableName + " (uuid, last_streak_date, last_sync) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE last_streak_date = ?, last_sync = ?";
        try {
            reconnectIfNeeded();
            long currentTime = System.currentTimeMillis();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, lastStreakDate);
                pstmt.setLong(3, currentTime);
                pstmt.setString(4, lastStreakDate);
                pstmt.setLong(5, currentTime);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終ストリーク日の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized long getLastSync(UUID playerId) {
        String sql = "SELECT last_sync FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("last_sync");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終同期日時の取得に失敗しました: " + e.getMessage());
        }
        return 0L;
    }
    
    @Override
    public synchronized void setLastSync(UUID playerId, long lastSync) {
        String sql = "INSERT INTO " + tableName + " (uuid, last_sync) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE last_sync = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setLong(2, lastSync);
                pstmt.setLong(3, lastSync);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("最終同期日時の設定に失敗しました: " + e.getMessage());
        }
    }
    
    @Override
    public synchronized boolean syncPlayerData(UUID playerId) {
        String sql = "SELECT cumulative, last_reward, streak, last_streak_date, last_sync " +
                "FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // データベースから最新のデータを取得済み
                        // 実際の同期処理はメモリキャッシュがある場合に必要
                        // このStorageインターフェースは直接データベースにアクセスするため、
                        // 読み取り時に常に最新データを取得している
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("プレイヤーデータの同期に失敗しました: " + e.getMessage());
        }
        return false;
    }
    
    @Override
    public CompletableFuture<Void> saveAsync() {
        // MySQLは各操作ごとに自動的にコミットされる
        // 明示的な保存は不要
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("MySQLデータベース接続を閉じました");
            } catch (SQLException e) {
                plugin.getLogger().severe("MySQLデータベース接続のクローズに失敗しました: " + e.getMessage());
            }
        }
    }
    
    @Override
    public synchronized boolean deletePlayerData(UUID playerId) {
        String sql = "DELETE FROM " + tableName + " WHERE uuid = ?";
        try {
            reconnectIfNeeded();
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("プレイヤーデータの削除に失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public synchronized boolean deleteAllPlayerData() {
        String sql = "TRUNCATE TABLE " + tableName;
        try {
            reconnectIfNeeded();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("全プレイヤーデータの削除に失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public synchronized java.util.List<UUID> getAllPlayerUUIDs() {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        String sql = "SELECT uuid FROM " + tableName;
        try {
            reconnectIfNeeded();
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
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("プレイヤーUUIDリストの取得に失敗しました: " + e.getMessage());
        }
        return uuids;
    }
}
