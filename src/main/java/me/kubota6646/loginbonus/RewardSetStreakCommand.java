package me.kubota6646.loginbonus;

import org.bukkit.Bukkit;
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
            sender.sendMessage(plugin.getMessage("no-permission", "&cこのコマンドはOP権限が必要です。"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(plugin.getMessage("set-streak-usage", "&c使用法: /%command% <player|everyone> <streak>",
                "%command%", label));
            return true;
        }

        String playerName = args[0];
        
        int streak;
        try {
            streak = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessage("invalid-number", "&cストリークは数値で指定してください。"));
            return true;
        }

        if (streak < 0) {
            sender.sendMessage(plugin.getMessage("number-must-be-positive", "&cストリークは0以上で指定してください。"));
            return true;
        }

        // "everyone" キーワードで全プレイヤーを対象
        if (playerName.equalsIgnoreCase("everyone")) {
            int playerCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                plugin.getStorage().setStreak(onlinePlayer.getUniqueId(), streak);
                plugin.getStorage().setLastStreakDate(onlinePlayer.getUniqueId(), LocalDate.now().toString());
                playerCount++;
            }
            plugin.savePlayerDataAsync();
            sender.sendMessage(plugin.getMessage("set-streak-everyone-success", "&a全プレイヤー (%count% 人) のストリークを %streak% 日に設定しました。",
                "%count%", String.valueOf(playerCount), "%streak%", String.valueOf(streak)));
            return true;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "&cプレイヤー '%player%' が見つかりません。",
                "%player%", playerName));
            return true;
        }

        // ストリークを設定
        plugin.getStorage().setStreak(target.getUniqueId(), streak);
        plugin.getStorage().setLastStreakDate(target.getUniqueId(), LocalDate.now().toString());
        plugin.savePlayerDataAsync();

        sender.sendMessage(plugin.getMessage("set-streak-success", "&aプレイヤー '%player%' のストリークを %streak% 日に設定しました。",
            "%player%", playerName, "%streak%", String.valueOf(streak)));

        return true;
    }
}