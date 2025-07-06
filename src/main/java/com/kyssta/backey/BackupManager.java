package com.kyssta.backey;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class BackupManager {
    
    private final BackeyPlugin plugin;
    private final ConfigManager configManager;
    private final TarGzUtil tarGzUtil;
    private final SftpUtil sftpUtil;
    private final PterodactylUtil pterodactylUtil;
    private final AtomicBoolean isBackupRunning = new AtomicBoolean(false);
    
    public BackupManager(BackeyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.tarGzUtil = new TarGzUtil(plugin, configManager);
        this.sftpUtil = new SftpUtil(plugin, configManager);
        this.pterodactylUtil = new PterodactylUtil(plugin, configManager);
    }
    
    public boolean performBackup() {
        if (isBackupRunning.getAndSet(true)) {
            plugin.log(Level.WARNING, "Backup already in progress, skipping...");
            return false;
        }
        
        try {
            plugin.log(Level.INFO, "Starting backup process...");
            
            // Generate backup filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filename = configManager.getFilenamePrefix() + "-" + timestamp + ".tar.gz";
            
            // Create temporary backup file
            File tempBackupFile = new File(plugin.getDataFolder(), "temp/" + filename);
            
            // Step 1: Create tar.gz backup
            plugin.debug("Creating tar.gz backup: " + tempBackupFile.getAbsolutePath());
            boolean tarSuccess = tarGzUtil.createBackup(tempBackupFile);
            
            if (!tarSuccess) {
                plugin.log(Level.SEVERE, "Failed to create tar.gz backup!");
                return false;
            }
            
            plugin.log(Level.INFO, "tar.gz backup created successfully: " + filename);
            
            // Step 2: Upload based on configured method
            String uploadMethod = configManager.getUploadMethod();
            boolean uploadSuccess = false;
            
            if ("pterodactyl".equalsIgnoreCase(uploadMethod)) {
                plugin.debug("Uploading backup via Pterodactyl API...");
                uploadSuccess = pterodactylUtil.uploadBackup(tempBackupFile, filename);
                
                if (uploadSuccess) {
                    plugin.log(Level.INFO, "Backup uploaded successfully via Pterodactyl API");
                    // Cleanup old backups
                    pterodactylUtil.cleanupOldBackups();
                }
            } else if ("local".equalsIgnoreCase(uploadMethod)) {
                plugin.log(Level.INFO, "Local backup mode - skipping upload");
                uploadSuccess = true;
            } else {
                // Default to SFTP
                if (configManager.isSftpEnabled()) {
                    plugin.debug("Uploading backup to SFTP server...");
                    uploadSuccess = sftpUtil.uploadBackup(tempBackupFile, filename);
                    
                    if (uploadSuccess) {
                        plugin.log(Level.INFO, "Backup uploaded successfully to SFTP server");
                        // Cleanup old backups on remote server
                        sftpUtil.cleanupOldBackups();
                    }
                } else {
                    plugin.log(Level.WARNING, "SFTP is disabled, backup will only be stored locally");
                    uploadSuccess = true; // Consider local backup as success
                }
            }
            
            if (!uploadSuccess && !"local".equalsIgnoreCase(uploadMethod)) {
                plugin.log(Level.SEVERE, "Failed to upload backup!");
                return false;
            }
            
            // Step 3: Move backup to local backups folder
            File finalBackupFile = new File(plugin.getDataFolder(), "backups/" + filename);
            if (tempBackupFile.renameTo(finalBackupFile)) {
                plugin.debug("Backup moved to local backups folder");
            } else {
                plugin.log(Level.WARNING, "Failed to move backup to local backups folder");
            }
            
            // Step 4: Cleanup old local backups
            cleanupLocalBackups();
            
            plugin.log(Level.INFO, "Backup process completed successfully!");
            
            // Notify players if server is not empty
            if (Bukkit.getOnlinePlayers().size() > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.broadcastMessage("§a[Backey] Server backup completed successfully!");
                    }
                }.runTask(plugin);
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Backup process failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            isBackupRunning.set(false);
        }
    }
    
    public boolean testConnection() {
        String uploadMethod = configManager.getUploadMethod();
        
        if ("pterodactyl".equalsIgnoreCase(uploadMethod)) {
            plugin.log(Level.INFO, "Testing Pterodactyl connection is not implemented yet");
            return false;
        } else if ("local".equalsIgnoreCase(uploadMethod)) {
            plugin.log(Level.INFO, "Local backup mode - no connection to test");
            return true;
        } else {
            // Test SFTP connection
            return sftpUtil.testConnection();
        }
    }
    
    private void cleanupLocalBackups() {
        File backupsDir = new File(plugin.getDataFolder(), "backups");
        File[] backupFiles = backupsDir.listFiles((dir, name) -> name.endsWith(".tar.gz"));
        
        if (backupFiles != null && backupFiles.length > configManager.getKeepBackups()) {
            // Sort by last modified date
            java.util.Arrays.sort(backupFiles, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            
            // Delete oldest backups
            int toDelete = backupFiles.length - configManager.getKeepBackups();
            for (int i = 0; i < toDelete; i++) {
                if (backupFiles[i].delete()) {
                    plugin.debug("Deleted old local backup: " + backupFiles[i].getName());
                } else {
                    plugin.log(Level.WARNING, "Failed to delete old local backup: " + backupFiles[i].getName());
                }
            }
        }
    }
    
    public void shutdown() {
        // Wait for backup to complete
        while (isBackupRunning.get()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Cleanup connections
        sftpUtil.disconnect();
        pterodactylUtil.cleanup();
    }
}