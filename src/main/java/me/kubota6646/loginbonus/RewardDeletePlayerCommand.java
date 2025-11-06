package me.kubota6646.loginbonus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public record RewardDeletePlayerCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です。");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "使用法: /" + label + " <player>");
            return true;
        }

        String playerName = args[0];
        
        // オフラインプレイヤーを取得
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "プレイヤー '" + playerName + "' が見つかりません。");
            return true;
        }

        // プレイヤーデータを削除
        boolean success = plugin.getStorage().deletePlayerData(target.getUniqueId());
        
        if (success) {
            plugin.savePlayerDataAsync();
            sender.sendMessage(ChatColor.GREEN + "プレイヤー '" + playerName + "' のデータを削除しました。");
            
            // オンラインの場合はトラッキングもキャンセル
            if (target.isOnline() && plugin.getEventListener() != null) {
                plugin.getEventListener().cancelTasksForPlayer(target.getUniqueId());
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "プレイヤー '" + playerName + "' のデータは存在しませんでした。");
        }

        return true;
    }
}
