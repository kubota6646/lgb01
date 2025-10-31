package me.kubota6646.lgb01;

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

    public EventListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerKey = playerId.toString();

        // 今日の日付を取得
        String today = LocalDate.now().toString();

        // 累積ログイン時間を取得 (デフォルト0.0)
        double cumulativeMinutes = plugin.getPlayerData().getDouble(playerKey + ".cumulative", 0.0);

        // 目標時間を取得
        int targetMinutes = plugin.getConfig().getInt("reward-time", 30);

        // 既に達成済みか確認
        String lastReward = plugin.getPlayerData().getString(playerKey + ".lastReward");
        boolean alreadyRewarded = today.equals(lastReward);

        if (alreadyRewarded) {
            // 既に報酬を受け取っている場合、何もしない
            return;
        }

        // 報酬が受け取り可能か確認
        if (cumulativeMinutes >= targetMinutes) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e報酬が受け取り可能です！ /rewardclaim で受け取ってください。"));
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
                double currentCumulative = cumulativeMinutes + additionalMinutes;
                double currentRemaining = targetMinutes - currentCumulative;
                int currentRemainingSeconds = (int) Math.ceil(Math.max(currentRemaining, 0.0) * 60);

                // ボスバーを更新
                String updatedTitle = bossBarTitleTemplate.replace("%remaining%", formatTime(currentRemainingSeconds));
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', updatedTitle));
                bossBar.setProgress(Math.min(currentCumulative / targetMinutes, 1.0));

                // 目標に達したら報酬を与える
                if (currentCumulative >= targetMinutes) {
                    giveReward(player, today, true);
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
    }

    private void saveCumulativeTime(UUID playerId) {
        Long loginStart = loginTimes.get(playerId);
        if (loginStart == null || loginStart == 0L) return;
        long start = loginStart;
        long currentTime = System.currentTimeMillis();
        double additionalMinutes = (currentTime - start) / 60000.0;
        String playerKey = playerId.toString();
        double currentCumulative = plugin.getPlayerData().getDouble(playerKey + ".cumulative", 0.0) + additionalMinutes;
        plugin.getPlayerData().set(playerKey + ".cumulative", currentCumulative);
        plugin.savePlayerDataAsync();
    }

    public void giveReward(Player player, String today, boolean setLastReward) {
        UUID playerId = player.getUniqueId();
        String playerKey = playerId.toString();

        // ストリークを計算
        int streak = 1;
        if (plugin.getConfig().getBoolean("streak-enabled", true)) {
            String lastStreakDateStr = plugin.getPlayerData().getString(playerKey + ".lastStreakDate");
            if (lastStreakDateStr != null) {
                LocalDate lastStreakDate = LocalDate.parse(lastStreakDateStr);
                LocalDate yesterday = LocalDate.now().minusDays(1);
                if (lastStreakDate.equals(yesterday)) {
                    streak = plugin.getPlayerData().getInt(playerKey + ".streak", 1) + 1;
                }
            }
            plugin.getPlayerData().set(playerKey + ".streak", streak);
            plugin.getPlayerData().set(playerKey + ".lastStreakDate", today);
        }

        // 基本報酬を与える
        boolean rewardGiven = giveItems(player, plugin.getConfig().getMapList("reward-items"), streak - 1);
        if (!rewardGiven) {
            // 基本報酬が与えられなかった場合、特殊報酬も処理せず終了
            return;
        }

        // 特殊ストリーク報酬を与える
        if (plugin.getConfig().getBoolean("special_streak_rewards.enabled", false)) {
            String streakKey = String.valueOf(streak);
            if (plugin.getConfig().isConfigurationSection("special_streak_rewards.rewards." + streakKey)) {
                List<Map<?, ?>> items = plugin.getConfig().getMapList("special_streak_rewards.rewards." + streakKey + ".items");
                if (giveItems(player, items, 0)) {
                    String message = plugin.getConfig().getString("special_streak_rewards.rewards." + streakKey + ".message");
                    if (message != null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    }
                }
            }
        }

        // 特殊倍数ストリーク報酬を与える
        if (plugin.getConfig().getBoolean("special_multiple_rewards.enabled", true)) {
            ConfigurationSection multiplesSection = plugin.getConfig().getConfigurationSection("special_multiple_rewards.multiples");
            if (multiplesSection != null) {
                for (String multipleKey : multiplesSection.getKeys(false)) {
                    int multiple = Integer.parseInt(multipleKey);
                    if (streak % multiple == 0 && streak > 0) {
                        List<Map<?, ?>> items = plugin.getConfig().getMapList("special_multiple_rewards.multiples." + multipleKey + ".items");
                        if (giveItems(player, items, 0)) {
                            String message = plugin.getConfig().getString("special_multiple_rewards.multiples." + multipleKey + ".message");
                            if (message != null) {
                                message = message.replace("%days%", String.valueOf(streak));
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                            }
                        }
                    }
                }
            }
        }

        // 報酬受け取りメッセージ
        String rewardMsg = plugin.getMessages().getString("reward-message", "&e報酬を受け取りました！");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', rewardMsg));

        // 受け取り状況を記録
        if (setLastReward) {
            plugin.getPlayerData().set(playerKey + ".lastReward", today);
            plugin.savePlayerDataAsync();
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
    }

    private boolean giveItems(Player player, List<Map<?, ?>> items, int extraAmount) {
        if (items.isEmpty()) return true;
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
        if (itemStacks.isEmpty()) return true;
        Map<Integer, ItemStack> returned = player.getInventory().addItem(itemStacks.toArray(new ItemStack[0]));
        if (!returned.isEmpty()) {
            // インベントリが満杯の場合、入りきらなかったアイテムを地面にドロップ
            for (ItemStack leftover : returned.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            // メッセージを表示
            String inventoryFullMsg = plugin.getMessages().getString("inventory-full-message", "&cインベントリが満タンです。/rewardclaim で受け取ってください。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', inventoryFullMsg));

            // ボスバーを受け取り可能に変更
            BossBar bossBar = bossBars.get(player.getUniqueId());
            if (bossBar != null) {
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', "報酬が受け取り可能です！ /rewardclaim で受け取ってください"));
                bossBar.setProgress(1.0);
            }
            return false;
        }
        return true;
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
    }

    public void cancelAllTasks() {
        // すべてのプレイヤーのタスクとボスバーをクリア
        for (UUID playerId : new HashSet<>(updateTasks.keySet())) {
            cancelTasksForPlayer(playerId);
        }
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}