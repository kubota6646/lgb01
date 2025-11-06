package me.kubota6646.loginbonus;

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
            sender.sendMessage(plugin.getMessage("player-only", "&cこのコマンドはプレイヤーのみ実行可能です。"));
            return true;
        }

        UUID playerId = player.getUniqueId();
        int streak = plugin.getStorage().getStreak(playerId);

        player.sendMessage(plugin.getMessage("current-streak", "&a現在のストリーク: %streak% 日", 
            "%streak%", String.valueOf(streak)));
        return true;
    }
}