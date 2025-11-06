package me.kubota6646.loginbonus.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageInterface {
    
    /**
     * プレイヤーの累積時間を取得
     * @param playerId プレイヤーのUUID
     * @return 累積時間（分）
     */
    double getCumulative(UUID playerId);
    
    /**
     * プレイヤーの累積時間を設定
     * @param playerId プレイヤーのUUID
     * @param cumulative 累積時間（分）
     */
    void setCumulative(UUID playerId, double cumulative);
    
    /**
     * プレイヤーの最終報酬日を取得
     * @param playerId プレイヤーのUUID
     * @return 最終報酬日（YYYY-MM-DD形式）、なければnull
     */
    String getLastReward(UUID playerId);
    
    /**
     * プレイヤーの最終報酬日を設定
     * @param playerId プレイヤーのUUID
     * @param lastReward 最終報酬日（YYYY-MM-DD形式）
     */
    void setLastReward(UUID playerId, String lastReward);
    
    /**
     * プレイヤーのストリークを取得
     * @param playerId プレイヤーのUUID
     * @return ストリーク日数
     */
    int getStreak(UUID playerId);
    
    /**
     * プレイヤーのストリークを設定
     * @param playerId プレイヤーのUUID
     * @param streak ストリーク日数
     */
    void setStreak(UUID playerId, int streak);
    
    /**
     * プレイヤーの最終ストリーク日を取得
     * @param playerId プレイヤーのUUID
     * @return 最終ストリーク日（YYYY-MM-DD形式）、なければnull
     */
    String getLastStreakDate(UUID playerId);
    
    /**
     * プレイヤーの最終ストリーク日を設定
     * @param playerId プレイヤーのUUID
     * @param lastStreakDate 最終ストリーク日（YYYY-MM-DD形式）
     */
    void setLastStreakDate(UUID playerId, String lastStreakDate);
    
    /**
     * データを非同期で保存
     * @return CompletableFuture
     */
    CompletableFuture<Void> saveAsync();
    
    /**
     * ストレージを初期化
     */
    void initialize();
    
    /**
     * ストレージを閉じる
     */
    void close();
    
    /**
     * プレイヤーの最終同期日時を取得
     * Note: 将来の同期機能拡張のために予約されています
     * @param playerId プレイヤーのUUID
     * @return 最終同期日時（エポックミリ秒）、なければ0
     */
    @SuppressWarnings("unused")
    long getLastSync(UUID playerId);
    
    /**
     * プレイヤーの最終同期日時を設定
     * Note: 将来の同期機能拡張のために予約されています
     * @param playerId プレイヤーのUUID
     * @param lastSync 最終同期日時（エポックミリ秒）
     */
    @SuppressWarnings("unused")
    void setLastSync(UUID playerId, long lastSync);
    
    /**
     * データベースからプレイヤーデータを同期
     * @param playerId プレイヤーのUUID
     * @return データが更新された場合true
     */
    boolean syncPlayerData(UUID playerId);
    
    /**
     * 特定のプレイヤーのデータを削除
     * @param playerId プレイヤーのUUID
     * @return 削除に成功した場合true
     */
    boolean deletePlayerData(UUID playerId);
    
    /**
     * 全てのプレイヤーデータを削除
     * @return 削除に成功した場合true
     */
    boolean deleteAllPlayerData();
    
    /**
     * 全てのプレイヤーUUIDを取得
     * @return プレイヤーUUIDのリスト
     */
    java.util.List<UUID> getAllPlayerUUIDs();
}
