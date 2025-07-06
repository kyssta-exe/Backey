package com.kyssta.backey;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;

public class SftpUtil {
    
    private final BackeyPlugin plugin;
    private final ConfigManager configManager;
    private Session session;
    private ChannelSftp sftpChannel;
    
    public SftpUtil(BackeyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    public boolean uploadBackup(File localFile, String remoteFileName) {
        try {
            if (!connect()) {
                return false;
            }
            
            // Create remote directory if it doesn't exist
            createRemoteDirectory(configManager.getRemoteDirectory());
            
            // Change to remote directory
            sftpChannel.cd(configManager.getRemoteDirectory());
            
            // Upload file
            plugin.debug("Uploading file: " + localFile.getAbsolutePath() + " -> " + remoteFileName);
            
            try (FileInputStream fis = new FileInputStream(localFile)) {
                sftpChannel.put(fis, remoteFileName);
            }
            
            plugin.log(Level.INFO, "Successfully uploaded backup: " + remoteFileName);
            return true;
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to upload backup via SFTP: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            disconnect();
        }
    }
    
    public void cleanupOldBackups() {
        try {
            if (!connect()) {
                return;
            }
            
            // Change to remote directory
            sftpChannel.cd(configManager.getRemoteDirectory());
            
            // List all backup files
            Vector<ChannelSftp.LsEntry> files = sftpChannel.ls("*.tar.gz");
            List<BackupFile> backupFiles = new ArrayList<>();
            
            for (ChannelSftp.LsEntry entry : files) {
                if (entry.getFilename().startsWith(configManager.getFilenamePrefix())) {
                    backupFiles.add(new BackupFile(entry.getFilename(), entry.getAttrs().getMTime()));
                }
            }
            
            // Sort by modification time (newest first)
            Collections.sort(backupFiles, (a, b) -> Long.compare(b.timestamp, a.timestamp));
            
            // Delete old backups
            int keepCount = configManager.getKeepBackups();
            for (int i = keepCount; i < backupFiles.size(); i++) {
                try {
                    sftpChannel.rm(backupFiles.get(i).filename);
                    plugin.log(Level.INFO, "Deleted old backup: " + backupFiles.get(i).filename);
                } catch (SftpException e) {
                    plugin.log(Level.WARNING, "Failed to delete old backup: " + backupFiles.get(i).filename);
                }
            }
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to cleanup old backups: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }
    
    private boolean connect() {
        try {
            JSch jsch = new JSch();
            
            // Setup private key if specified
            String privateKeyPath = configManager.getPrivateKeyPath();
            if (!privateKeyPath.isEmpty()) {
                String passphrase = configManager.getPrivateKeyPassphrase();
                if (passphrase.isEmpty()) {
                    jsch.addIdentity(privateKeyPath);
                } else {
                    jsch.addIdentity(privateKeyPath, passphrase);
                }
                plugin.debug("Using private key authentication: " + privateKeyPath);
            } else {
                plugin.debug("Using password authentication");
            }
            
            // Create session
            session = jsch.getSession(configManager.getSftpUsername(), configManager.getSftpHost(), configManager.getSftpPort());
            
            // Set password if not using key authentication
            if (privateKeyPath.isEmpty()) {
                String password = configManager.getSftpPassword();
                if (password.isEmpty()) {
                    plugin.log(Level.SEVERE, "No password or private key specified for SFTP authentication!");
                    return false;
                }
                session.setPassword(password);
            }
            
            // Configure session properties
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,password");
            session.setConfig("UserKnownHostsFile", "/dev/null");
            
            // Enable compression
            session.setConfig("compression.s2c", "zlib,none");
            session.setConfig("compression.c2s", "zlib,none");
            
            // Set timeout
            session.setTimeout(configManager.getSftpTimeout());
            
            plugin.debug("Connecting to SFTP server: " + configManager.getSftpUsername() + "@" + configManager.getSftpHost() + ":" + configManager.getSftpPort());
            
            // Connect
            session.connect();
            
            // Open SFTP channel
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;
            
            plugin.debug("SFTP connection established successfully");
            return true;
            
        } catch (JSchException e) {
            plugin.log(Level.SEVERE, "SFTP Connection failed: " + e.getMessage());
            
            // Provide more specific error messages
            if (e.getMessage().contains("Auth fail")) {
                plugin.log(Level.SEVERE, "Authentication failed. Please check:");
                plugin.log(Level.SEVERE, "1. Username and password are correct");
                plugin.log(Level.SEVERE, "2. If using private key, ensure the key file exists and is readable");
                plugin.log(Level.SEVERE, "3. The user has SSH/SFTP access enabled on the server");
                plugin.log(Level.SEVERE, "4. Try connecting manually with an SFTP client to verify credentials");
            } else if (e.getMessage().contains("Connection refused")) {
                plugin.log(Level.SEVERE, "Connection refused. Please check:");
                plugin.log(Level.SEVERE, "1. The host and port are correct");
                plugin.log(Level.SEVERE, "2. SSH service is running on the remote server");
                plugin.log(Level.SEVERE, "3. Firewall allows connections on port " + configManager.getSftpPort());
            } else if (e.getMessage().contains("timeout")) {
                plugin.log(Level.SEVERE, "Connection timeout. Please check:");
                plugin.log(Level.SEVERE, "1. The host is reachable");
                plugin.log(Level.SEVERE, "2. Network connectivity");
                plugin.log(Level.SEVERE, "3. Consider increasing the timeout value");
            }
            
            return false;
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to connect to SFTP server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
            plugin.debug("SFTP channel disconnected");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            plugin.debug("SFTP session disconnected");
        }
    }
    
    private void createRemoteDirectory(String path) {
        try {
            String[] dirs = path.split("/");
            String currentPath = "";
            
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;
                
                currentPath += "/" + dir;
                
                try {
                    sftpChannel.stat(currentPath);
                    plugin.debug("Directory exists: " + currentPath);
                } catch (SftpException e) {
                    // Directory doesn't exist, create it
                    try {
                        sftpChannel.mkdir(currentPath);
                        plugin.debug("Created remote directory: " + currentPath);
                    } catch (SftpException ex) {
                        plugin.log(Level.WARNING, "Failed to create remote directory: " + currentPath + " - " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error creating remote directory: " + e.getMessage());
        }
    }
    
    // Test connection method for troubleshooting
    public boolean testConnection() {
        plugin.log(Level.INFO, "Testing SFTP connection...");
        plugin.log(Level.INFO, "Host: " + configManager.getSftpHost());
        plugin.log(Level.INFO, "Port: " + configManager.getSftpPort());
        plugin.log(Level.INFO, "Username: " + configManager.getSftpUsername());
        plugin.log(Level.INFO, "Using private key: " + (!configManager.getPrivateKeyPath().isEmpty()));
        plugin.log(Level.INFO, "Remote directory: " + configManager.getRemoteDirectory());
        
        boolean result = connect();
        disconnect();
        
        if (result) {
            plugin.log(Level.INFO, "SFTP connection test successful!");
        } else {
            plugin.log(Level.SEVERE, "SFTP connection test failed!");
        }
        
        return result;
    }
    
    private static class BackupFile {
        final String filename;
        final long timestamp;
        
        BackupFile(String filename, long timestamp) {
            this.filename = filename;
            this.timestamp = timestamp;
        }
    }
}