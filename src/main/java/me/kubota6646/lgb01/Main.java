package me.kubota6646.lgb01;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public class Main extends JavaPlugin {

    private FileConfiguration playerData;
    private File playerDataFile;
    private FileConfiguration messages;
    private File messagesFile;
    private EventListener eventListener;

    @Override
    public void onEnable() {
        // 設定ファイルを保存
        saveDefaultConfig();

        // playerdata.yml を保存
        saveDefaultPlayerData();

        // messages.yml を保存
        saveDefaultMessages();

        // イベントリスナーを登録
        eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);

        // コマンドを登録
        PluginCommand rewardClaimCmd = getCommand("rewardclaim");
        if (rewardClaimCmd != null) {
            rewardClaimCmd.setExecutor(new RewardClaimCommand(this));
        }
        PluginCommand rewardReloadCmd = getCommand("rewardreload");
        if (rewardReloadCmd != null) {
            rewardReloadCmd.setExecutor(new RewardReloadCommand(this));
        }
        PluginCommand rewardForceGiveCmd = getCommand("rewardforcegive");
        if (rewardForceGiveCmd != null) {
            rewardForceGiveCmd.setExecutor(new RewardForceGiveCommand(this));
        }
        PluginCommand rewardForceStreakCmd = getCommand("rewardforcestreak");
        if (rewardForceStreakCmd != null) {
            rewardForceStreakCmd.setExecutor(new RewardForceStreakCommand(this));
        }
        PluginCommand rewardSetStreakCmd = getCommand("rewardsetstreak");
        if (rewardSetStreakCmd != null) {
            rewardSetStreakCmd.setExecutor(new RewardSetStreakCommand(this));
        }
        PluginCommand rewardResetPlaytimeCmd = getCommand("rewardresetplaytime");
        if (rewardResetPlaytimeCmd != null) {
            rewardResetPlaytimeCmd.setExecutor(new RewardResetPlaytimeCommand(this));
        }

        getLogger().info("lgb01プラグインが有効化されました。");
    }

    @Override
    public void onDisable() {
        // タスクをキャンセル
        if (eventListener != null) {
            eventListener.cancelAllTasks();
        }

        // 非同期でデータを保存
        savePlayerDataAsync();

        getLogger().info("lgb01プラグインが無効化されました。");
    }

    public void reloadConfig() {
        super.reloadConfig();
        reloadPlayerData();
        reloadMessages();
    }

    public FileConfiguration getPlayerData() {
        return playerData;
    }

    public void savePlayerDataAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                playerData.save(playerDataFile);
            } catch (IOException e) {
                getLogger().severe("playerdata.yml の保存に失敗しました: " + e.getMessage());
            }
        });
    }

    private void reloadPlayerData() {
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void createDirectories(File file, String fileName) {
        boolean dirsCreated = file.getParentFile().mkdirs();
        if (!dirsCreated && !file.getParentFile().exists()) {
            getLogger().warning(fileName + " のディレクトリ作成に失敗しました。");
        }
    }

    private void copyResourceOrCreateEmpty(File file, String resourceName, String fileName) {
        try {
            java.io.InputStream resourceStream = getResource(resourceName);
            createDirectories(file, fileName);
            if (resourceStream != null) {
                Files.copy(resourceStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                boolean fileCreated = file.createNewFile();
                if (!fileCreated && !file.exists()) {
                    getLogger().warning(fileName + " のファイル作成に失敗しました。");
                }
                getLogger().info(fileName + " リソースが見つからないため、空のファイルを作成しました。");
            }
        } catch (IOException e) {
            getLogger().severe(fileName + " の作成に失敗しました: " + e.getMessage());
        }
    }

    private void saveDefaultFile(File file, String resourceName, String fileName) {
        if (!file.exists()) {
            copyResourceOrCreateEmpty(file, resourceName, fileName);
        }
    }

    private void saveDefaultPlayerData() {
        playerDataFile = new File(getDataFolder(), "playerdata.yml");
        saveDefaultFile(playerDataFile, "playerdata.yml", "playerdata.yml");
        reloadPlayerData();
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    private void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveDefaultMessages() {
        messagesFile = new File(getDataFolder(), "message.yml");
        saveDefaultFile(messagesFile, "message.yml", "message.yml");
        reloadMessages();
    }

    public EventListener getEventListener() {
        return eventListener;
    }
}