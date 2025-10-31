package me.kubota6646.lgb01;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record RewardResetPlaytimeCommand(Main plugin) implements CommandExecutor {

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
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤー '" + playerName + "' が見つかりません。");
            return true;
        }

        String playerKey = target.getUniqueId().toString();

        // 累積時間をリセット
        plugin.getPlayerData().set(playerKey + ".cumulative", 0.0);
        plugin.savePlayerDataAsync();

        sender.sendMessage(ChatColor.GREEN + "プレイヤー '" + playerName + "' の累積プレイ時間をリセットしました。");

        return true;
    }
}