package me.kubota6646.loginbonus;

import me.kubota6646.loginbonus.storage.StorageInterface;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public record RewardSyncCommand(Main plugin) implements CommandExecutor {
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 権限チェック
        if (!sender.isOp() && !sender.hasPermission("loginbonus.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission-admin", "&cこのコマンドを実行する権限がありません。"));
            return true;
        }
        
        StorageInterface storage = plugin.getStorage();
        String storageType = plugin.getConfig().getString("storage-type", "yaml").toLowerCase();
        
        // MySQL以外のストレージタイプの場合は同期不要
        if (!storageType.equals("mysql")) {
            sender.sendMessage(plugin.getMessage("sync-mysql-only", "&e同期機能はMySQLストレージでのみ使用できます。"));
            sender.sendMessage(plugin.getMessage("sync-current-type", "&e現在のストレージタイプ: %type%",
                "%type%", storageType));
            return true;
        }
        
        // 引数なしの場合は自分自身を同期
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessage("sync-console-usage", "&cコンソールからはプレイヤー名を指定してください: /rewardsync <player>"));
                return true;
            }
            
            syncPlayerData(sender, player, storage);
            return true;
        }
        
        // 引数がある場合は指定されたプレイヤーまたは全員を同期
        String targetName = args[0];
        
        // "everyone" キーワードで全プレイヤーを対象
        if (targetName.equalsIgnoreCase("everyone")) {
            int playerCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                syncPlayerData(sender, onlinePlayer, storage);
                playerCount++;
            }
            sender.sendMessage(plugin.getMessage("sync-everyone-started", "&e全プレイヤー (%count% 人) のデータ同期を開始しました。",
                "%count%", String.valueOf(playerCount)));
            return true;
        }
        
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-found", "&cプレイヤー '%player%' が見つかりません。",
                "%player%", targetName));
            return true;
        }
        
        syncPlayerData(sender, target, storage);
        return true;
    }
    
    private void syncPlayerData(CommandSender sender, Player target, StorageInterface storage) {
        sender.sendMessage(plugin.getMessage("sync-starting", "&e%player% のデータを同期しています...",
            "%player%", target.getName()));
        
        // 非同期でデータベースから同期
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 現在のデータを取得
                double oldCumulative = storage.getCumulative(target.getUniqueId());
                int oldStreak = storage.getStreak(target.getUniqueId());
                String oldLastReward = storage.getLastReward(target.getUniqueId());
                
                // データベースから最新データを取得（再読み込み）
                boolean synced = storage.syncPlayerData(target.getUniqueId());
                
                if (synced) {
                    // 同期後のデータを取得
                    double newCumulative = storage.getCumulative(target.getUniqueId());
                    int newStreak = storage.getStreak(target.getUniqueId());
                    String newLastReward = storage.getLastReward(target.getUniqueId());
                    
                    // 変更があったかチェック
                    boolean hasChanges = (oldCumulative != newCumulative) || 
                                        (oldStreak != newStreak) || 
                                        !java.util.Objects.equals(oldLastReward, newLastReward);
                    
                    // メインスレッドでメッセージを送信
                    Bukkit.getScheduler().runTask(plugin, () -> sendSyncResultMessages(sender, target, hasChanges, oldCumulative, newCumulative, oldStreak, newStreak));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getMessage("sync-failed", "&cデータの同期に失敗しました。")));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("データ同期中にエラーが発生しました: " + e.getMessage());
                plugin.getLogger().severe("スタックトレース: " + java.util.Arrays.toString(e.getStackTrace()));
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.getMessage("sync-error", "&cデータの同期中にエラーが発生しました。")));
            }
        });
    }
    
    private void sendSyncResultMessages(CommandSender sender, Player target, boolean hasChanges, double oldCumulative, double newCumulative, int oldStreak, int newStreak) {
        if (hasChanges) {
            sender.sendMessage(plugin.getMessage("sync-success", "&a%player% のデータを同期しました。",
                "%player%", target.getName()));
            sender.sendMessage(plugin.getMessage("sync-cumulative", "&7累積時間: %old% → %new%",
                "%old%", String.valueOf(oldCumulative), "%new%", String.valueOf(newCumulative)));
            sender.sendMessage(plugin.getMessage("sync-streak", "&7ストリーク: %old% → %new%",
                "%old%", String.valueOf(oldStreak), "%new%", String.valueOf(newStreak)));
            
            // プレイヤーがオンラインの場合は通知
            if (target.isOnline()) {
                target.sendMessage(plugin.getMessage("reward-synced", "&aデータが同期されました。"));
            }
        } else {
            sender.sendMessage(plugin.getMessage("sync-already-latest", "&a%player% のデータは既に最新です。",
                "%player%", target.getName()));
        }
    }
}
