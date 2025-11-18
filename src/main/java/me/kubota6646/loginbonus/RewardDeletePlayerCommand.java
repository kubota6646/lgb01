package me.kubota6646.loginbonus;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public record RewardDeletePlayerCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getMessage("no-permission", "&cこのコマンドはOP権限が必要です。"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getMessage("delete-player-usage", "&c使用法: /%command% <player>",
                "%command%", label));
            return true;
        }

        String playerName = args[0];
        
        // オフラインプレイヤーを取得
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getMessage("player-not-found", "&cプレイヤー '%player%' が見つかりません。",
                "%player%", playerName));
            return true;
        }

        // プレイヤーデータを削除
        boolean success = plugin.getStorage().deletePlayerData(target.getUniqueId());
        
        if (success) {
            plugin.savePlayerDataAsync();
            sender.sendMessage(plugin.getMessage("delete-player-success", "&aプレイヤー '%player%' のデータを削除しました。",
                "%player%", playerName));
            
            // オンラインの場合はトラッキングをキャンセルして再開
            if (target.isOnline() && plugin.getEventListener() != null) {
                plugin.getEventListener().cancelTasksForPlayer(target.getUniqueId());
                // データ削除後、トラッキングを再開
                plugin.getEventListener().startTrackingForPlayer(target.getUniqueId());
            }
        } else {
            sender.sendMessage(plugin.getMessage("delete-player-not-exist", "&eプレイヤー '%player%' のデータは存在しませんでした。",
                "%player%", playerName));
        }

        return true;
    }
}
