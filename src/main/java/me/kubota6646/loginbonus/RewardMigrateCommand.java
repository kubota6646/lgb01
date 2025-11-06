package me.kubota6646.loginbonus;

import me.kubota6646.loginbonus.storage.StorageFactory;
import me.kubota6646.loginbonus.storage.StorageInterface;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record RewardMigrateCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です。");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /" + label + " <yaml|sqlite|mysql> <yaml|sqlite|mysql>");
            sender.sendMessage(ChatColor.YELLOW + "例: /" + label + " yaml mysql - YAMLからMySQLへ移行");
            return true;
        }

        String fromType = args[0].toLowerCase();
        String toType = args[1].toLowerCase();

        if (!fromType.equals("yaml") && !fromType.equals("sqlite") && !fromType.equals("mysql")) {
            sender.sendMessage(ChatColor.RED + "移行元は 'yaml', 'sqlite' または 'mysql' のみ指定可能です。");
            return true;
        }

        if (!toType.equals("yaml") && !toType.equals("sqlite") && !toType.equals("mysql")) {
            sender.sendMessage(ChatColor.RED + "移行先は 'yaml', 'sqlite' または 'mysql' のみ指定可能です。");
            return true;
        }

        if (fromType.equals(toType)) {
            sender.sendMessage(ChatColor.RED + "移行元と移行先が同じです。");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "データ移行を開始します: " + fromType + " -> " + toType);

        // 移行元と移行先のストレージを作成
        StorageInterface fromStorage = StorageFactory.createStorage(plugin, fromType);
        StorageInterface toStorage = StorageFactory.createStorage(plugin, toType);

        try {
            fromStorage.initialize();
            toStorage.initialize();

            int migratedCount = 0;

            // 全プレイヤーUUIDを取得
            java.util.List<UUID> playerUUIDs = fromStorage.getAllPlayerUUIDs();
            
            if (playerUUIDs.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "移行するプレイヤーデータが見つかりませんでした。");
                return true;
            }
            
            sender.sendMessage(ChatColor.YELLOW + playerUUIDs.size() + " 件のプレイヤーデータを移行中...");
            
            // 各プレイヤーのデータを移行
            for (UUID playerId : playerUUIDs) {
                try {
                    // データを読み込んで移行
                    double cumulative = fromStorage.getCumulative(playerId);
                    String lastReward = fromStorage.getLastReward(playerId);
                    int streak = fromStorage.getStreak(playerId);
                    String lastStreakDate = fromStorage.getLastStreakDate(playerId);
                    
                    // 移行先に書き込み
                    toStorage.setCumulative(playerId, cumulative);
                    if (lastReward != null) {
                        toStorage.setLastReward(playerId, lastReward);
                    }
                    toStorage.setStreak(playerId, streak);
                    if (lastStreakDate != null) {
                        toStorage.setLastStreakDate(playerId, lastStreakDate);
                    }
                    
                    migratedCount++;
                } catch (Exception e) {
                    plugin.getLogger().warning("プレイヤー " + playerId + " のデータ移行に失敗しました: " + e.getMessage());
                }
            }

            toStorage.saveAsync().join();
            
            sender.sendMessage(ChatColor.GREEN + "データ移行が完了しました。" + migratedCount + " 件のプレイヤーデータを移行しました。");
            sender.sendMessage(ChatColor.YELLOW + "config.yml の storage-type を '" + toType + "' に変更して /rewardreload を実行してください。");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "データ移行中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("データ移行中にエラーが発生しました: " + e.getMessage());
            plugin.getLogger().severe("スタックトレース: " + e.getClass().getName());
            for (StackTraceElement element : e.getStackTrace()) {
                plugin.getLogger().severe("  at " + element.toString());
            }
        } finally {
            fromStorage.close();
            toStorage.close();
        }

        return true;
    }
}
