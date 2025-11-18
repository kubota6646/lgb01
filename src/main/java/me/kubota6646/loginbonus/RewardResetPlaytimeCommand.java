package me.kubota6646.loginbonus;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record RewardResetPlaytimeCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getMessage("no-permission", "&cこのコマンドはOP権限が必要です。"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getMessage("reset-playtime-usage", "&c使用法: /%command% <player|everyone>",
                "%command%", label));
            return true;
        }

        String playerName = args[0];
        
        // "everyone" キーワードで全プレイヤーを対象
        if (playerName.equalsIgnoreCase("everyone")) {
            int playerCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                plugin.getStorage().setCumulative(onlinePlayer.getUniqueId(), 0.0);
                plugin.getStorage().setLastReward(onlinePlayer.getUniqueId(), null);
                plugin.getEventListener().startTrackingForPlayer(onlinePlayer.getUniqueId());
                playerCount++;
            }
            plugin.getStorage().saveAsync().join();
            sender.sendMessage(plugin.getMessage("reset-playtime-everyone-success", "&a全プレイヤー (%count% 人) の累積プレイ時間をリセットしました。",
                "%count%", String.valueOf(playerCount)));
            return true;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "&cプレイヤー '%player%' が見つかりません。",
                "%player%", playerName));
            return true;
        }

        // 累積時間と最終報酬日をリセット
        plugin.getStorage().setCumulative(target.getUniqueId(), 0.0);
        plugin.getStorage().setLastReward(target.getUniqueId(), null);
        // 同期的に保存を完了させる
        plugin.getStorage().saveAsync().join();

        // ボスバーをリセットして新しいカウントを開始
        plugin.getEventListener().startTrackingForPlayer(target.getUniqueId());

        sender.sendMessage(plugin.getMessage("reset-playtime-success", "&aプレイヤー '%player%' の累積プレイ時間をリセットしました。",
            "%player%", playerName));

        return true;
    }
}