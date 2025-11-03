package me.kubota6646.loginbonus;

import me.kubota6646.loginbonus.storage.StorageInterface;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RewardSyncCommand implements CommandExecutor {
    
    private final Main plugin;
    
    public RewardSyncCommand(Main plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 権限チェック
        if (!sender.isOp() && !sender.hasPermission("lgb01.admin")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }
        
        StorageInterface storage = plugin.getStorage();
        String storageType = plugin.getConfig().getString("storage-type", "yaml").toLowerCase();
        
        // MySQL以外のストレージタイプの場合は同期不要
        if (!storageType.equals("mysql")) {
            sender.sendMessage(ChatColor.YELLOW + "同期機能はMySQLストレージでのみ使用できます。");
            sender.sendMessage(ChatColor.YELLOW + "現在のストレージタイプ: " + storageType);
            return true;
        }
        
        // 引数なしの場合は自分自身を同期
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "コンソールからはプレイヤー名を指定してください: /rewardsync <player>");
                return true;
            }
            
            Player player = (Player) sender;
            syncPlayerData(sender, player, storage);
            return true;
        }
        
        // 引数がある場合は指定されたプレイヤーを同期
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤー " + targetName + " が見つかりません。");
            return true;
        }
        
        syncPlayerData(sender, target, storage);
        return true;
    }
    
    private void syncPlayerData(CommandSender sender, Player target, StorageInterface storage) {
        sender.sendMessage(ChatColor.YELLOW + target.getName() + " のデータを同期しています...");
        
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
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (hasChanges) {
                            sender.sendMessage(ChatColor.GREEN + target.getName() + " のデータを同期しました。");
                            sender.sendMessage(ChatColor.GRAY + "累積時間: " + oldCumulative + " → " + newCumulative);
                            sender.sendMessage(ChatColor.GRAY + "ストリーク: " + oldStreak + " → " + newStreak);
                            
                            // プレイヤーがオンラインの場合は通知
                            if (target.isOnline()) {
                                target.sendMessage(ChatColor.GREEN + "データが同期されました。");
                            }
                        } else {
                            sender.sendMessage(ChatColor.GREEN + target.getName() + " のデータは既に最新です。");
                        }
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.RED + "データの同期に失敗しました。");
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("データ同期中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "データの同期中にエラーが発生しました。");
                });
            }
        });
    }
}
