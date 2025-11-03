package me.kubota6646.loginbonus.storage;

import org.bukkit.plugin.java.JavaPlugin;

public class StorageFactory {
    
    public static StorageInterface createStorage(JavaPlugin plugin, String storageType) {
        if ("sqlite".equalsIgnoreCase(storageType)) {
            return new SqliteStorage(plugin);
        } else {
            return new YamlStorage(plugin);
        }
    }
}
