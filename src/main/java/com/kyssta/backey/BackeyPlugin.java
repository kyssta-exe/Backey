package com.kyssta.backey;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.logging.Level;

public class BackeyPlugin extends JavaPlugin {
    
    private BackupManager backupManager;
    private BukkitTask backupTask;
    private ConfigManager configManager;
    
    @Override
    public void onEnable() {
        // Create plugin directory structure
        createDirectories();
        
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        // Initialize backup manager
        backupManager = new BackupManager(this, configManager);
        
        // Schedule periodic backups
        scheduleBackups();
        
        getLogger().info("Backey has been enabled!");
        getLogger().info("Backup interval: " + configManager.getBackupInterval() + " minutes");
        getLogger().info("Keeping " + configManager.getKeepBackups() + " backups on remote server");
        getLogger().info("Upload method: " + configManager.getUploadMethod());
    }
    
    @Override
    public void onDisable() {
        // Cancel scheduled tasks
        if (backupTask != null) {
            backupTask.cancel();
        }
        
        // Wait for any running backup to complete
        backupManager.shutdown();
        
        getLogger().info("Backey has been disabled!");
    }
    
    private void createDirectories() {
        File backupsDir = new File(getDataFolder(), "backups");
        File tempDir = new File(getDataFolder(), "temp");
        
        if (!backupsDir.exists()) {
            backupsDir.mkdirs();
        }
        
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }
    
    private void scheduleBackups() {
        if (backupTask != null) {
            backupTask.cancel();
        }
        
        long intervalTicks = configManager.getBackupInterval() * 60 * 20; // Convert minutes to ticks
        
        backupTask = new BukkitRunnable() {
            @Override
            public void run() {
                backupManager.performBackup();
            }
        }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        
        getLogger().info("Scheduled automatic backups every " + configManager.getBackupInterval() + " minutes");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backup")) {
            if (!sender.hasPermission("backey.backup")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            
            sender.sendMessage("§6Starting manual backup...");
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success = backupManager.performBackup();
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (success) {
                                sender.sendMessage("§aBackup completed successfully!");
                            } else {
                                sender.sendMessage("§cBackup failed! Check console for details.");
                            }
                        }
                    }.runTask(BackeyPlugin.this);
                }
            }.runTaskAsynchronously(this);
            
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("backup-reload")) {
            if (!sender.hasPermission("backey.reload")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            
            configManager.loadConfig();
            scheduleBackups();
            sender.sendMessage("§aConfiguration reloaded!");
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("backup-test")) {
            if (!sender.hasPermission("backey.backup")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            
            sender.sendMessage("§6Testing SFTP connection...");
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success = backupManager.testConnection();
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (success) {
                                sender.sendMessage("§aConnection test successful!");
                            } else {
                                sender.sendMessage("§cConnection test failed! Check console for details.");
                            }
                        }
                    }.runTask(BackeyPlugin.this);
                }
            }.runTaskAsynchronously(this);
            
            return true;
        }
        
        return false;
    }
    
    public void log(Level level, String message) {
        getLogger().log(level, message);
    }
    
    public void debug(String message) {
        if (configManager.isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}