package me.kubota6646.loginbonus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.time.LocalDate;

public record RewardForceGiveCommand(Main plugin) implements CommandExecutor {

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

        String today = LocalDate.now().toString();

        // lastRewardを更新して重複付与を防ぐが、ストリークは更新しない
        plugin.getEventListener().giveReward(target, today, true, false);

        sender.sendMessage(ChatColor.GREEN + "プレイヤー '" + playerName + "' に今日の報酬を強制付与しました。");

        return true;
    }
}