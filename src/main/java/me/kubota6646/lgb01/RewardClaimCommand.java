package me.kubota6646.lgb01;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RewardClaimCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        UUID playerId = player.getUniqueId();
        String playerKey = playerId.toString();
        String today = LocalDate.now().toString();

        // 既に受け取り済みか確認
        String lastReward = plugin.getPlayerData().getString(playerKey + ".lastReward");
        if (today.equals(lastReward)) {
            String alreadyClaimedMsg = plugin.getMessages().getString("already-claimed-message", "&c今日は既に報酬を受け取っています。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyClaimedMsg));
            return true;
        }

        // 累積時間を確認
        double cumulativeMinutes = plugin.getPlayerData().getDouble(playerKey + ".cumulative", 0.0);
        int targetMinutes = plugin.getConfig().getInt("reward-time", 30);
        if (cumulativeMinutes < targetMinutes) {
            String notEnoughTimeMsg = plugin.getMessages().getString("not-enough-time-message", "&cまだ報酬を受け取る条件を満たしていません。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', notEnoughTimeMsg));
            return true;
        }

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
            return true;
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

        // 受け取りメッセージ
        String rewardMsg = plugin.getMessages().getString("reward-message", "&e報酬を受け取りました！");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', rewardMsg));

        // 受け取り状況を記録
        plugin.getPlayerData().set(playerKey + ".lastReward", today);
        plugin.savePlayerDataAsync();

        // ボスバーを削除（もし表示中なら）
        plugin.getEventListener().cancelTasksForPlayer(playerId);

        return true;
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
            String inventoryFullMsg = plugin.getMessages().getString("inventory-full-message", "&cインベントリが満タンです。スペースを空けてから再度実行してください。");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', inventoryFullMsg));
            return false;
        }
        return true;
    }
}