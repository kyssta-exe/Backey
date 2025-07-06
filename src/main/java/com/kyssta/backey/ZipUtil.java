package com.kyssta.backey;

import java.io.*;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
    
    private final BackeyPlugin plugin;
    private final ConfigManager configManager;
    
    public ZipUtil(BackeyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }
    
    public boolean createBackup(File outputFile) {
        try {
            // Get server root directory
            File serverRoot = new File(".");
            
            // Create parent directories if they don't exist
            outputFile.getParentFile().mkdirs();
            
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                
                // Set compression level
                zos.setLevel(configManager.getCompressionLevel());
                
                // Add all files and directories to zip
                addDirectoryToZip(zos, serverRoot, "", configManager.getExcludeList());
                
                plugin.debug("ZIP backup created: " + outputFile.getAbsolutePath());
                plugin.debug("ZIP backup size: " + (outputFile.length() / (1024 * 1024)) + " MB");
                
                return true;
            }
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to create ZIP backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void addDirectoryToZip(ZipOutputStream zos, File directory, String basePath, List<String> excludeList) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            String relativePath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
            
            // Check if file/directory should be excluded
            if (shouldExclude(relativePath, excludeList)) {
                plugin.debug("Excluding from backup: " + relativePath);
                continue;
            }
            
            if (file.isDirectory()) {
                // Add directory entry
                ZipEntry dirEntry = new ZipEntry(relativePath + "/");
                zos.putNextEntry(dirEntry);
                zos.closeEntry();
                
                // Recursively add directory contents
                addDirectoryToZip(zos, file, relativePath, excludeList);
            } else {
                // Add file entry
                addFileToZip(zos, file, relativePath);
            }
        }
    }
    
    private void addFileToZip(ZipOutputStream zos, File file, String relativePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(relativePath);
            entry.setTime(file.lastModified());
            zos.putNextEntry(entry);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
            
            zos.closeEntry();
        }
    }
    
    private boolean shouldExclude(String relativePath, List<String> excludeList) {
        for (String exclude : excludeList) {
            if (exclude.endsWith("*")) {
                // Wildcard matching
                String prefix = exclude.substring(0, exclude.length() - 1);
                if (relativePath.startsWith(prefix)) {
                    return true;
                }
            } else if (exclude.startsWith("*")) {
                // Suffix matching
                String suffix = exclude.substring(1);
                if (relativePath.endsWith(suffix)) {
                    return true;
                }
            } else if (relativePath.equals(exclude) || relativePath.startsWith(exclude + "/")) {
                // Exact match or directory match
                return true;
            }
        }
        return false;
    }
}