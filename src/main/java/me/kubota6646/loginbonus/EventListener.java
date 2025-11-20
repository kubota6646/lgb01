package me.kubota6646.loginbonus;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventListener implements Listener {

    private final Main plugin;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> updateTasks = new HashMap<>();
    private final Map<UUID, Long> loginTimes = new HashMap<>(); // ログイン開始時間 (ミリ秒)
    private final Map<UUID, String> currentDates = new HashMap<>(); // 現在の日付
    private final Map<UUID, Double> cumulativeMinutesMap = new HashMap<>(); // 累積時間

    public EventListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // MySQL使用時は自動同期
        String storageType = plugin.getConfig().getString("storage-type", "yaml").toLowerCase();
        if (storageType.equals("mysql")) {
            plugin.getStorage().syncPlayerData(playerId);
        }

        // 現在のリセット日付を取得
        String today = plugin.getResetDate();
        currentDates.put(playerId, today);

        // 累積ログイン時間を取得 (デフォルト0.0)
        double cumulativeMinutes = plugin.getStorage().getCumulative(playerId);
        cumulativeMinutesMap.put(playerId, cumulativeMinutes);

        // 目標時間を取得
        int targetMinutes = plugin.getConfig().getInt("reward-time", 30);

        // 既に達成済みか確認
        String lastReward = plugin.getStorage().getLastReward(playerId);
        boolean alreadyRewarded = today.equals(lastReward);

        if (alreadyRewarded) {
            // 既に報酬を受け取っている場合、何もしない
            return;
        }

        // ログイン開始時間を記録
        loginTimes.put(playerId, System.currentTimeMillis());

        // ボスバーを作成
        String bossBarTitleTemplate = plugin.getConfig().getString("boss-bar-title", "&a報酬まで残り: %remaining%");
        String bossBarColorStr = plugin.getConfig().getString("boss-bar-color", "BLUE");
        String bossBarStyleStr = plugin.getConfig().getString("boss-bar-style", "SOLID");
        BarColor bossBarColor = BarColor.valueOf(bossBarColorStr.toUpperCase());
        BarStyle bossBarStyle = BarStyle.valueOf(bossBarStyleStr.toUpperCase());
        double remainingMinutes = targetMinutes - cumulativeMinutes;
        int remainingSeconds = (int) Math.ceil(Math.max(remainingMinutes, 0.0) * 60);
        String title = bossBarTitleTemplate.replace("%remaining%", formatTime(remainingSeconds));
        BossBar bossBar = plugin.getServer().createBossBar(ChatColor.translateAlternateColorCodes('&', title), bossBarColor, bossBarStyle);
        bossBar.setProgress(Math.min(cumulativeMinutes / targetMinutes, 1.0));
        bossBar.addPlayer(player);
        bossBars.put(playerId, bossBar);

        // 毎秒更新タスク
        BukkitTask updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 現在の累積時間を計算
                long loginStart = loginTimes.get(playerId);
                if (loginStart == 0) return;
                long currentTime = System.currentTimeMillis();
                double additionalMinutes = (currentTime - loginStart) / 60000.0; // ミリ秒から分に変換
                double currentCumulative = cumulativeMinutesMap.get(playerId) + additionalMinutes;
                double currentRemaining = targetMinutes - currentCumulative;
                int currentRemainingSeconds = (int) Math.ceil(Math.max(currentRemaining, 0.0) * 60);

                // 日付変更チェック
                String currentDate = plugin.getResetDate();
                if (!currentDate.equals(currentDates.get(playerId))) {
                    // 日付が変わったので、新しいカウントを開始
                    currentDates.put(playerId, currentDate);
                    // 累積時間を0にリセット
                    plugin.getStorage().setCumulative(playerId, 0.0);
                    plugin.savePlayerDataAsync();
                    cumulativeMinutesMap.put(playerId, 0.0);
                    // ログイン開始時間を現在時刻にリセット
                    loginTimes.put(playerId, System.currentTimeMillis());
                    // ボスバーを更新
                    double newCumulativeMinutes = 0.0;
                    double newRemainingMinutes = targetMinutes - newCumulativeMinutes;
                    int newRemainingSeconds = (int) Math.ceil(Math.max(newRemainingMinutes, 0.0) * 60);
                    String updatedTitle = bossBarTitleTemplate.replace("%remaining%", formatTime(newRemainingSeconds));
                    bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', updatedTitle));
                    bossBar.setProgress(0.0);
                    // 累積時間をリセット
                    currentCumulative = 0.0;
                }

                // ボスバーを更新
                String updatedTitle = bossBarTitleTemplate.replace("%remaining%", formatTime(currentRemainingSeconds));
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', updatedTitle));
                bossBar.setProgress(Math.min(currentCumulative / targetMinutes, 1.0));

                // 目標に達したら報酬を与える
                if (currentCumulative >= targetMinutes) {
                    giveReward(player, currentDates.get(playerId), true, true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 毎秒

        updateTasks.put(playerId, updateTask);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 累積時間を保存
        saveCumulativeTime(playerId);

        // タスクとボスバーをクリア
        if (updateTasks.containsKey(playerId)) {
            updateTasks.get(playerId).cancel();
            updateTasks.remove(playerId);
        }
        if (bossBars.containsKey(playerId)) {
            bossBars.get(playerId).removeAll();
            bossBars.remove(playerId);
        }
        loginTimes.remove(playerId);
        currentDates.remove(playerId);
        cumulativeMinutesMap.remove(playerId);
    }

    private void saveCumulativeTime(UUID playerId) {
        Long loginStart = loginTimes.get(playerId);
        if (loginStart == null || loginStart == 0L) return;
        long start = loginStart;
        long currentTime = System.currentTimeMillis();
        double additionalMinutes = (currentTime - start) / 60000.0;
        double savedCumulative = plugin.getStorage().getCumulative(playerId);
        double newCumulative = savedCumulative + additionalMinutes;
        plugin.getStorage().setCumulative(playerId, newCumulative);
        plugin.savePlayerDataAsync();
    }

    public void giveReward(Player player, String today, boolean setLastReward, boolean updateStreak) {
        UUID playerId = player.getUniqueId();

        // ストリークを取得
        int streak = plugin.getStorage().getStreak(playerId);

        if (updateStreak && plugin.getConfig().getBoolean("streak-enabled", true)) {
            // ストリークを計算
            String lastStreakDateStr = plugin.getStorage().getLastStreakDate(playerId);
            if (lastStreakDateStr != null) {
                LocalDate lastStreakDate;
                // 日付時刻形式 "YYYY-MM-DD HH:mm" または日付のみ "YYYY-MM-DD" をサポート
                if (lastStreakDateStr.length() > 10) {
                    // 日付時刻形式の場合、日付部分のみを抽出
                    lastStreakDate = LocalDate.parse(lastStreakDateStr.substring(0, 10));
                } else {
                    // 日付のみの形式
                    lastStreakDate = LocalDate.parse(lastStreakDateStr);
                }
                LocalDate yesterday = LocalDate.now().minusDays(1);
                if (lastStreakDate.equals(yesterday)) {
                    streak = plugin.getStorage().getStreak(playerId) + 1;
                }
            }
            plugin.getStorage().setStreak(playerId, streak);
            plugin.getStorage().setLastStreakDate(playerId, today);
        }

        // 基本報酬を与える
        giveItems(player, plugin.getConfig().getMapList("reward-items"), streak - 1);

        // 特殊ストリーク報酬を与える
        if (plugin.getConfig().getBoolean("special_streak_rewards.enabled", false)) {
            String streakKey = String.valueOf(streak);
            if (plugin.getConfig().isConfigurationSection("special_streak_rewards.rewards." + streakKey)) {
                List<Map<?, ?>> items = plugin.getConfig().getMapList("special_streak_rewards.rewards." + streakKey + ".items");
                giveItems(player, items, 0);
                String message = plugin.getConfig().getString("special_streak_rewards.rewards." + streakKey + ".message");
                if (message != null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
        }

        // 特殊倍数ストリーク報酬を与える
        if (plugin.getConfig().getBoolean("special_multiple_rewards.enabled", true)) {
            ConfigurationSection multiplesSection = plugin.getConfig().getConfigurationSection("special_multiple_rewards.multiples");
            if (multiplesSection != null) {
                int maxMultiple = 0;
                for (String multipleKey : multiplesSection.getKeys(false)) {
                    int multiple = Integer.parseInt(multipleKey);
                    if (streak % multiple == 0 && streak > 0 && multiple > maxMultiple) {
                        maxMultiple = multiple;
                    }
                }
                if (maxMultiple > 0) {
                    List<Map<?, ?>> items = plugin.getConfig().getMapList("special_multiple_rewards.multiples." + maxMultiple + ".items");
                    giveItems(player, items, 0);
                    String message = plugin.getConfig().getString("special_multiple_rewards.multiples." + maxMultiple + ".message");
                    if (message != null) {
                        message = message.replace("%days%", String.valueOf(streak));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    }
                }
            }
        }

        // 報酬受け取りメッセージ
        String rewardMsg = plugin.getMessages().getString("reward-message", "&e報酬を受け取りました！");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', rewardMsg));

        // 受け取り状況を記録
        if (setLastReward) {
            // 累積時間を0にリセットし、最終報酬日を設定
            plugin.getStorage().setCumulative(playerId, 0.0);
            plugin.getStorage().setLastReward(playerId, today);
            // 同期的に保存を完了させる
            plugin.getStorage().saveAsync().join();
        }

        // タスクとボスバーをクリア
        if (updateTasks.containsKey(playerId)) {
            updateTasks.get(playerId).cancel();
            updateTasks.remove(playerId);
        }
        if (bossBars.containsKey(playerId)) {
            bossBars.get(playerId).removeAll();
            bossBars.remove(playerId);
        }
        loginTimes.remove(playerId);
        currentDates.remove(playerId);
        cumulativeMinutesMap.remove(playerId);
    }

    private void giveItems(Player player, List<Map<?, ?>> items, int extraAmount) {
        if (items.isEmpty()) return;
        List<ItemStack> itemStacks = new ArrayList<>();
        for (Map<?, ?> itemMap : items) {
            String itemName = (String) itemMap.get("item");
            if (itemName == null) itemName = (String) itemMap.get("type");
            int baseAmount = (Integer) itemMap.get("amount");
            int amount = baseAmount + extraAmount;
            Material material = Material.getMaterial(itemName.toUpperCase());
            if (material != null) {
                itemStacks.add(new ItemStack(material, amount));
            } else {
                plugin.getLogger().warning("無効なアイテム名: " + itemName);
            }
        }
        if (itemStacks.isEmpty()) return;
        Map<Integer, ItemStack> returned = player.getInventory().addItem(itemStacks.toArray(new ItemStack[0]));
        if (!returned.isEmpty()) {
            // インベントリが満杯の場合、入りきらなかったアイテムを地面にドロップ
            for (ItemStack leftover : returned.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public void cancelTasksForPlayer(UUID playerId) {
        // タスクとボスバーをクリア
        if (updateTasks.containsKey(playerId)) {
            updateTasks.get(playerId).cancel();
            updateTasks.remove(playerId);
        }
        if (bossBars.containsKey(playerId)) {
            bossBars.get(playerId).removeAll();
            bossBars.remove(playerId);
        }
        loginTimes.remove(playerId);
        currentDates.remove(playerId);
        cumulativeMinutesMap.remove(playerId);
    }

    public void cancelAllTasks() {
        // すべてのプレイヤーのタスクとボスバーをクリア
        for (UUID playerId : new HashSet<>(updateTasks.keySet())) {
            cancelTasksForPlayer(playerId);
        }
    }

    public void saveAllCumulativeTimes() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            saveCumulativeTime(player.getUniqueId());
        }
    }

    public void startTrackingForPlayer(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        startTracking(player);
    }

    private void startTracking(Player player) {
        UUID playerId = player.getUniqueId();

        // 既存のタスクとボスバーをクリア
        cancelTasksForPlayer(playerId);

        // 日付を取得
        String today = LocalDate.now().toString();
        currentDates.put(playerId, today);

        // 累積時間をストレージから取得
        double cumulativeMinutes = plugin.getStorage().getCumulative(playerId);
        cumulativeMinutesMap.put(playerId, cumulativeMinutes);

        // 目標時間を取得
        int targetMinutes = plugin.getConfig().getInt("reward-time", 30);

        // 既に達成済みか確認
        String lastReward = plugin.getStorage().getLastReward(playerId);
        boolean alreadyRewarded = today.equals(lastReward);

        if (alreadyRewarded) {
            return;
        }

        // ログイン開始時間を記録
        loginTimes.put(playerId, System.currentTimeMillis());

        // ボスバーを作成
        String bossBarTitleTemplate = plugin.getConfig().getString("boss-bar-title", "&a報酬まで残り: %remaining%");
        String bossBarColorStr = plugin.getConfig().getString("boss-bar-color", "BLUE");
        String bossBarStyleStr = plugin.getConfig().getString("boss-bar-style", "SOLID");
        BarColor bossBarColor = BarColor.valueOf(bossBarColorStr.toUpperCase());
        BarStyle bossBarStyle = BarStyle.valueOf(bossBarStyleStr.toUpperCase());
        double remainingMinutes = targetMinutes - cumulativeMinutes;
        int remainingSeconds = (int) Math.ceil(Math.max(remainingMinutes, 0.0) * 60);
        String title = bossBarTitleTemplate.replace("%remaining%", formatTime(remainingSeconds));
        BossBar bossBar = plugin.getServer().createBossBar(ChatColor.translateAlternateColorCodes('&', title), bossBarColor, bossBarStyle);
        bossBar.setProgress(Math.min(cumulativeMinutes / targetMinutes, 1.0));
        bossBar.addPlayer(player);
        bossBars.put(playerId, bossBar);

        // 毎秒更新タスク
        BukkitTask updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 現在の累積時間を計算
                long loginStart = loginTimes.get(playerId);
                if (loginStart == 0) return;
                long currentTime = System.currentTimeMillis();
                double additionalMinutes = (currentTime - loginStart) / 60000.0; // ミリ秒から分に変換
                double currentCumulative = cumulativeMinutesMap.get(playerId) + additionalMinutes;
                double currentRemaining = targetMinutes - currentCumulative;
                int currentRemainingSeconds = (int) Math.ceil(Math.max(currentRemaining, 0.0) * 60);

                // 日付変更チェック
                String currentDate = plugin.getResetDate();
                if (!currentDate.equals(currentDates.get(playerId))) {
                    // 日付が変わったので、新しいカウントを開始
                    currentDates.put(playerId, currentDate);
                    // 累積時間を0にリセット
                    plugin.getStorage().setCumulative(playerId, 0.0);
                    plugin.savePlayerDataAsync();
                    cumulativeMinutesMap.put(playerId, 0.0);
                    // ログイン開始時間を現在時刻にリセット
                    loginTimes.put(playerId, System.currentTimeMillis());
                    // ボスバーを更新
                    double newCumulativeMinutes = 0.0;
                    double newRemainingMinutes = targetMinutes - newCumulativeMinutes;
                    int newRemainingSeconds = (int) Math.ceil(Math.max(newRemainingMinutes, 0.0) * 60);
                    String updatedTitle = bossBarTitleTemplate.replace("%remaining%", formatTime(newRemainingSeconds));
                    bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', updatedTitle));
                    bossBar.setProgress(0.0);
                    // 累積時間をリセット
                    currentCumulative = 0.0;
                }

                // ボスバーを更新
                String updatedTitle = bossBarTitleTemplate.replace("%remaining%", formatTime(currentRemainingSeconds));
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', updatedTitle));
                bossBar.setProgress(Math.min(currentCumulative / targetMinutes, 1.0));

                // 目標に達したら報酬を与える
                if (currentCumulative >= targetMinutes) {
                    giveReward(player, currentDates.get(playerId), true, true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 毎秒

        updateTasks.put(playerId, updateTask);
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void restartTrackingForNewDay() {
        // オンラインの全プレイヤーに対して、トラッキングを再開
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            // 既存のトラッキングをキャンセル
            cancelTasksForPlayer(playerId);
            // 新しい日のトラッキングを開始
            startTracking(player);
        }
    }
}