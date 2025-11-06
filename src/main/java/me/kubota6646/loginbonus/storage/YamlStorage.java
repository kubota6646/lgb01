package me.kubota6646.loginbonus.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YamlStorage implements StorageInterface {
    
    private final JavaPlugin plugin;
    private FileConfiguration playerData;
    private File playerDataFile;
    
    public YamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void initialize() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    if (!plugin.getDataFolder().mkdirs()) {
                        plugin.getLogger().warning("データフォルダの作成に失敗しました");
                    }
                }
                // Create an empty playerdata.yml file
                if (playerDataFile.createNewFile()) {
                    plugin.getLogger().info("playerdata.yml を作成しました");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("playerdata.yml の作成に失敗しました: " + e.getMessage());
            }
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }
    
    @Override
    public double getCumulative(UUID playerId) {
        String key = playerId.toString() + ".cumulative";
        return playerData.getDouble(key, 0.0);
    }
    
    @Override
    public void setCumulative(UUID playerId, double cumulative) {
        String key = playerId.toString() + ".cumulative";
        playerData.set(key, cumulative);
    }
    
    @Override
    public String getLastReward(UUID playerId) {
        String key = playerId.toString() + ".lastReward";
        return playerData.getString(key);
    }
    
    @Override
    public void setLastReward(UUID playerId, String lastReward) {
        String key = playerId.toString() + ".lastReward";
        playerData.set(key, lastReward);
    }
    
    @Override
    public int getStreak(UUID playerId) {
        String key = playerId.toString() + ".streak";
        return playerData.getInt(key, 1);
    }
    
    @Override
    public void setStreak(UUID playerId, int streak) {
        String key = playerId.toString() + ".streak";
        playerData.set(key, streak);
    }
    
    @Override
    public String getLastStreakDate(UUID playerId) {
        String key = playerId.toString() + ".lastStreakDate";
        return playerData.getString(key);
    }
    
    @Override
    public void setLastStreakDate(UUID playerId, String lastStreakDate) {
        String key = playerId.toString() + ".lastStreakDate";
        playerData.set(key, lastStreakDate);
    }
    
    @Override
    public CompletableFuture<Void> saveAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                playerData.save(playerDataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("playerdata.yml の保存に失敗しました: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void close() {
        // YAMLストレージは特にクローズ処理不要
    }
    
    @Override
    public long getLastSync(UUID playerId) {
        String key = playerId.toString() + ".lastSync";
        return playerData.getLong(key, 0L);
    }
    
    @Override
    public void setLastSync(UUID playerId, long lastSync) {
        String key = playerId.toString() + ".lastSync";
        playerData.set(key, lastSync);
    }
    
    @Override
    public boolean syncPlayerData(UUID playerId) {
        // YAMLストレージは単一サーバー用のため、同期は不要
        return false;
    }
    
    public void reload() {
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }
    
    @Override
    public boolean deletePlayerData(UUID playerId) {
        String key = playerId.toString();
        if (playerData.contains(key)) {
            playerData.set(key, null);
            try {
                playerData.save(playerDataFile);
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("プレイヤーデータの削除に失敗しました: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    @Override
    public boolean deleteAllPlayerData() {
        try {
            // 全てのキーを削除
            for (String key : playerData.getKeys(false)) {
                playerData.set(key, null);
            }
            playerData.save(playerDataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("全プレイヤーデータの削除に失敗しました: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public java.util.List<UUID> getAllPlayerUUIDs() {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        for (String key : playerData.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                uuids.add(uuid);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID: " + key);
            }
        }
        return uuids;
    }
}
