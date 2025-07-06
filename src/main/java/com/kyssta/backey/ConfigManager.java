package com.kyssta.backey;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {
    
    private final BackeyPlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(BackeyPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
    
    // Upload method
    public String getUploadMethod() {
        return config.getString("upload-method", "sftp");
    }
    
    // SFTP Configuration
    public boolean isSftpEnabled() {
        return config.getBoolean("sftp.enabled", true);
    }
    
    public String getSftpHost() {
        return config.getString("sftp.host", "localhost");
    }
    
    public int getSftpPort() {
        return config.getInt("sftp.port", 22);
    }
    
    public String getSftpUsername() {
        return config.getString("sftp.username", "");
    }
    
    public String getSftpPassword() {
        return config.getString("sftp.password", "");
    }
    
    public String getPrivateKeyPath() {
        return config.getString("sftp.private-key-path", "");
    }
    
    public String getPrivateKeyPassphrase() {
        return config.getString("sftp.private-key-passphrase", "");
    }
    
    public String getRemoteDirectory() {
        return config.getString("sftp.remote-directory", "/backups");
    }
    
    public int getSftpTimeout() {
        return config.getInt("sftp.timeout", 30) * 1000; // Convert to milliseconds
    }
    
    // Pterodactyl Configuration
    public String getPterodactylPanelUrl() {
        return config.getString("pterodactyl.panel-url", "");
    }
    
    public String getPterodactylClientApiKey() {
        return config.getString("pterodactyl.client-api-key", "");
    }
    
    public String getPterodactylServerId() {
        return config.getString("pterodactyl.server-id", "");
    }
    
    public String getPterodactylBackupDirectory() {
        return config.getString("pterodactyl.backup-directory", "/backups");
    }
    
    // Backup Configuration
    public int getBackupInterval() {
        return config.getInt("backup.interval-minutes", 60);
    }
    
    public int getKeepBackups() {
        return config.getInt("backup.keep-backups", 2);
    }
    
    public String getFilenamePrefix() {
        return config.getString("backup.filename-prefix", "server-backup");
    }
    
    public int getCompressionLevel() {
        return config.getInt("backup.compression-level", 6);
    }
    
    // Exclude Configuration
    public List<String> getExcludeList() {
        return config.getStringList("exclude");
    }
    
    // Debug Configuration
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }
}