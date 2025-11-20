package me.kubota6646.loginbonus;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record RewardDeleteAllCommand(Main plugin) implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getMessage("no-permission", "&cこのコマンドはOP権限が必要です。"));
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(plugin.getMessage("delete-all-warning", "&e警告: このコマンドは全てのプレイヤーデータを削除します。"));
            sender.sendMessage(plugin.getMessage("delete-all-confirmation", "&e実行するには /%command% confirm と入力してください。",
                "%command%", label));
            return true;
        }

        // 全プレイヤーデータを削除
        boolean success = plugin.getStorage().deleteAllPlayerData();
        
        if (success) {
            plugin.savePlayerDataAsync();
            sender.sendMessage(plugin.getMessage("delete-all-success", "&a全てのプレイヤーデータを削除しました。"));
            
            // オンラインの全プレイヤーのトラッキングをキャンセルして再開
            if (plugin.getEventListener() != null) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    plugin.getEventListener().cancelTasksForPlayer(player.getUniqueId());
                    // データ削除後、トラッキングを再開
                    plugin.getEventListener().startTrackingForPlayer(player.getUniqueId());
                }
            }
        } else {
            sender.sendMessage(plugin.getMessage("delete-all-failed", "&cデータの削除に失敗しました。"));
        }

        return true;
    }
}
