package me.kubota6646.loginbonus;

import me.kubota6646.loginbonus.storage.StorageFactory;
import me.kubota6646.loginbonus.storage.StorageInterface;
import me.kubota6646.loginbonus.storage.YamlStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

public class Main extends JavaPlugin {

    private StorageInterface storage;
    private FileConfiguration messages;
    private File messagesFile;
    private EventListener eventListener;
    private String lastCheckedDate; // 最後にチェックした日付

    @Override
    public void onEnable() {
        // messages.yml を初期化（saveDefaultConfig前に必要）
        messagesFile = new File(getDataFolder(), "message.yml");
        
        // 設定ファイルを保存
        saveDefaultConfig();

        // ストレージを初期化
        String storageType = getConfig().getString("storage-type", "yaml").toLowerCase();
        if (!storageType.equals("yaml") && !storageType.equals("sqlite") && !storageType.equals("mysql")) {
            getLogger().warning("無効なストレージタイプ: " + storageType + " - デフォルトの 'yaml' を使用します");
            storageType = "yaml";
        }
        getLogger().info("ストレージタイプ: " + storageType);
        storage = StorageFactory.createStorage(this, storageType);
        storage.initialize();

        // messages.yml を保存
        saveDefaultMessages();

        // イベントリスナーを登録
        eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);

        // コマンドを登録
        PluginCommand rewardStreakCmd = getCommand("rewardstreak");
        if (rewardStreakCmd != null) {
            rewardStreakCmd.setExecutor(new RewardStreakCommand(this));
        }
        PluginCommand rewardReloadCmd = getCommand("rewardreload");
        if (rewardReloadCmd != null) {
            rewardReloadCmd.setExecutor(new RewardReloadCommand(this));
        }
        PluginCommand rewardForceGiveCmd = getCommand("rewardforcegive");
        if (rewardForceGiveCmd != null) {
            rewardForceGiveCmd.setExecutor(new RewardForceGiveCommand(this));
        }
        PluginCommand rewardSetStreakCmd = getCommand("rewardsetstreak");
        if (rewardSetStreakCmd != null) {
            rewardSetStreakCmd.setExecutor(new RewardSetStreakCommand(this));
        }
        PluginCommand rewardResetPlaytimeCmd = getCommand("rewardresetplaytime");
        if (rewardResetPlaytimeCmd != null) {
            rewardResetPlaytimeCmd.setExecutor(new RewardResetPlaytimeCommand(this));
        }
        PluginCommand rewardMigrateCmd = getCommand("rewardmigrate");
        if (rewardMigrateCmd != null) {
            rewardMigrateCmd.setExecutor(new RewardMigrateCommand(this));
        }
        PluginCommand rewardSyncCmd = getCommand("rewardsync");
        if (rewardSyncCmd != null) {
            rewardSyncCmd.setExecutor(new RewardSyncCommand(this));
        }

        // 日付変更チェックタスクを開始
        lastCheckedDate = LocalDate.now().toString();
        startMidnightCheckTask();

        getLogger().info("lgb01プラグインが有効化されました。");
    }

    @Override
    public void onDisable() {
        // オンラインのプレイヤーの累積時間を保存
        if (eventListener != null) {
            eventListener.saveAllCumulativeTimes();
        }

        // タスクをキャンセル
        if (eventListener != null) {
            eventListener.cancelAllTasks();
        }

        // 非同期でデータを保存
        if (storage != null) {
            savePlayerDataAsync();
            // ストレージを閉じる
            storage.close();
        }

        getLogger().info("lgb01プラグインが無効化されました。");
    }

    public void reloadConfig() {
        super.reloadConfig();
        if (storage instanceof YamlStorage) {
            ((YamlStorage) storage).reload();
        }
        if (messagesFile != null) {
            reloadMessages();
        }
    }

    public StorageInterface getStorage() {
        return storage;
    }

    public void savePlayerDataAsync() {
        storage.saveAsync();
    }

    private void createDirectories(File file, String fileName) {
        boolean dirsCreated = file.getParentFile().mkdirs();
        if (!dirsCreated && !file.getParentFile().exists()) {
            getLogger().warning(fileName + " のディレクトリ作成に失敗しました。");
        }
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    private void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveDefaultMessages() {
        try {
            createDirectories(messagesFile, "message.yml");
            
            if (!messagesFile.exists()) {
                // ファイルが存在しない場合は、リソースから新規作成
                java.io.InputStream resourceStream = getResource("message.yml");
                if (resourceStream != null) {
                    Files.copy(resourceStream, messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    boolean fileCreated = messagesFile.createNewFile();
                    if (!fileCreated && !messagesFile.exists()) {
                        getLogger().warning("message.yml のファイル作成に失敗しました。");
                    }
                    getLogger().info("message.yml リソースが見つからないため、空のファイルを作成しました。");
                }
            } else {
                // ファイルが既に存在する場合は、リソースの新しいメッセージを追加
                java.io.InputStream resourceStream = getResource("message.yml");
                if (resourceStream != null) {
                    FileConfiguration defaultMessages = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(resourceStream, java.nio.charset.StandardCharsets.UTF_8));
                    FileConfiguration existingMessages = YamlConfiguration.loadConfiguration(messagesFile);
                    
                    boolean updated = false;
                    for (String key : defaultMessages.getKeys(false)) {
                        if (!existingMessages.contains(key)) {
                            existingMessages.set(key, defaultMessages.get(key));
                            updated = true;
                        }
                    }
                    
                    if (updated) {
                        existingMessages.save(messagesFile);
                        getLogger().info("message.yml に新しいメッセージが追加されました。");
                    }
                }
            }
        } catch (IOException e) {
            getLogger().severe("message.yml の処理に失敗しました: " + e.getMessage());
        }
        reloadMessages();
    }

    public EventListener getEventListener() {
        return eventListener;
    }

    private void startMidnightCheckTask() {
        // 30秒ごとに、日付が変わったかチェック
        new BukkitRunnable() {
            @Override
            public void run() {
                String currentDate = LocalDate.now().toString();
                if (!currentDate.equals(lastCheckedDate)) {
                    // 日付が変わった！
                    lastCheckedDate = currentDate;
                    getLogger().info("日付が変わりました: " + currentDate);
                    // オンラインのプレイヤーで既に報酬を受け取っているプレイヤーの追跡を再開
                    if (eventListener != null) {
                        eventListener.restartTrackingForNewDay();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 600L); // 30秒ごとに実行 (600 ticks = 30 seconds)
    }
}