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
        getLogger().info("ストレージタイプは " + storageType + " に設定されています。");
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
        PluginCommand rewardDeletePlayerCmd = getCommand("rewarddeleteplayer");
        if (rewardDeletePlayerCmd != null) {
            rewardDeletePlayerCmd.setExecutor(new RewardDeletePlayerCommand(this));
        }
        PluginCommand rewardDeleteAllCmd = getCommand("rewarddeleteall");
        if (rewardDeleteAllCmd != null) {
            rewardDeleteAllCmd.setExecutor(new RewardDeleteAllCommand(this));
        }

        // 日付変更チェックタスクを開始
        String resetTime = getConfig().getString("reset-time", "00:00");
        getLogger().info("リセット時刻は " + resetTime + " に設定されています。");
        lastCheckedDate = getCurrentResetDate();
        startMidnightCheckTask();

        getLogger().info("LoginBonusプラグインが有効化されました。");
        getLogger().info("全プレイヤーのトラッキングを再開します。");
    }

    @Override
    public void onDisable() {
        getLogger().info("全プレイヤーのトラッキングを停止します。");
        
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

        getLogger().info("LoginBonusプラグインが無効化されました。");
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
    
    /**
     * メッセージを取得し、プレースホルダーを置換する
     * @param key メッセージキー
     * @param defaultMessage デフォルトメッセージ
     * @param replacements プレースホルダーと置換値のペア（例: "%player%", "PlayerName"）
     * @return 色コード変換済みのメッセージ
     */
    public String getMessage(String key, String defaultMessage, String... replacements) {
        String message = messages.getString(key, defaultMessage);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
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
    
    /**
     * 現在のリセット日付を取得（外部から呼び出し可能）
     * @return リセット日付文字列
     */
    public String getResetDate() {
        return getCurrentResetDate();
    }

    private void startMidnightCheckTask() {
        // 設定されたチェック間隔でリセット時刻が過ぎたかチェック
        int checkIntervalSeconds = getConfig().getInt("reset-check-interval", 30);
        
        // 最小5秒、最大3600秒（1時間）に制限
        if (checkIntervalSeconds < 5) {
            getLogger().warning("reset-check-interval が小さすぎます（" + checkIntervalSeconds + "秒）。最小値の5秒を使用します。");
            checkIntervalSeconds = 5;
        } else if (checkIntervalSeconds > 3600) {
            getLogger().warning("reset-check-interval が大きすぎます（" + checkIntervalSeconds + "秒）。最大値の3600秒（1時間）を使用します。");
            checkIntervalSeconds = 3600;
        }
        
        long checkIntervalTicks = checkIntervalSeconds * 20L; // 秒をティックに変換（1秒 = 20ティック）
        
        getLogger().info("リセット時刻のチェック間隔は " + checkIntervalSeconds + " 秒です。");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (shouldResetToday()) {
                    // リセット時刻が過ぎた！
                    String resetTime = getConfig().getString("reset-time", "00:00");
                    lastCheckedDate = getCurrentResetDate();
                    getLogger().info("リセット時刻（" + resetTime + "）になりました。全オンラインプレイヤーのトラッキングを再開します。");
                    // オンラインの全プレイヤーの追跡を再開
                    if (eventListener != null) {
                        eventListener.restartTrackingForNewDay();
                    }
                }
            }
        }.runTaskTimer(this, 0L, checkIntervalTicks);
    }
    
    /**
     * 現在のリセット日付を取得（リセット時刻を考慮）
     * @return リセット日付文字列（YYYY-MM-DD HH:mm形式）
     */
    private String getCurrentResetDate() {
        String resetTimeStr = getConfig().getString("reset-time", "00:00");
        String[] timeParts = resetTimeStr.split(":");
        int resetHour = 0;
        int resetMinute = 0;
        
        try {
            resetHour = Integer.parseInt(timeParts[0]);
            if (timeParts.length > 1) {
                resetMinute = Integer.parseInt(timeParts[1]);
            }
        } catch (NumberFormatException e) {
            getLogger().warning("無効なリセット時刻形式: " + resetTimeStr + " - デフォルト(00:00)を使用します");
        }
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime resetTime = now.withHour(resetHour).withMinute(resetMinute).withSecond(0);
        
        // 現在時刻がリセット時刻より前の場合、前日のリセット日付を返す
        if (now.isBefore(resetTime)) {
            resetTime = resetTime.minusDays(1);
        }
        
        return resetTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    
    /**
     * 今日リセットすべきかチェック
     * @return リセットすべき場合true
     */
    private boolean shouldResetToday() {
        String currentResetDate = getCurrentResetDate();
        return !currentResetDate.equals(lastCheckedDate);
    }
}