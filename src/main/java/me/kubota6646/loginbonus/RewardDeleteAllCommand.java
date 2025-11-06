package me.kubota6646.loginbonus;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record RewardDeleteAllCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です。");
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.YELLOW + "警告: このコマンドは全てのプレイヤーデータを削除します。");
            sender.sendMessage(ChatColor.YELLOW + "実行するには /" + label + " confirm と入力してください。");
            return true;
        }

        // 全プレイヤーデータを削除
        boolean success = plugin.getStorage().deleteAllPlayerData();
        
        if (success) {
            plugin.savePlayerDataAsync();
            sender.sendMessage(ChatColor.GREEN + "全てのプレイヤーデータを削除しました。");
            
            // オンラインの全プレイヤーのトラッキングをキャンセル
            if (plugin.getEventListener() != null) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    plugin.getEventListener().cancelTasksForPlayer(player.getUniqueId());
                }
            }
        } else {
            sender.sendMessage(ChatColor.RED + "データの削除に失敗しました。");
        }

        return true;
    }
}
