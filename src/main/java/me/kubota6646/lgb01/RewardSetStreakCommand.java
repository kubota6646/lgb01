package me.kubota6646.lgb01;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.time.LocalDate;

public record RewardSetStreakCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です。");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "使用法: /" + label + " <player> <streak>");
            return true;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤー '" + playerName + "' が見つかりません。");
            return true;
        }

        int streak;
        try {
            streak = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "ストリークは数値で指定してください。");
            return true;
        }

        if (streak < 0) {
            sender.sendMessage(ChatColor.RED + "ストリークは0以上で指定してください。");
            return true;
        }

        String playerKey = target.getUniqueId().toString();
        String today = LocalDate.now().toString();

        // ストリークを設定
        plugin.getPlayerData().set(playerKey + ".streak", streak);
        plugin.getPlayerData().set(playerKey + ".lastStreakDate", today);
        plugin.savePlayerDataAsync();

        sender.sendMessage(ChatColor.GREEN + "プレイヤー '" + playerName + "' のストリークを " + streak + " 日に設定しました。");

        return true;
    }
}