package me.kubota6646.loginbonus;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public record RewardStreakCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        UUID playerId = player.getUniqueId();
        int streak = plugin.getStorage().getStreak(playerId);

        player.sendMessage(ChatColor.GREEN + "現在のストリーク: " + streak + " 日");
        return true;
    }
}