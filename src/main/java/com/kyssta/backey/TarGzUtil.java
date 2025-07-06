package com.kyssta.backey;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.util.List;
import java.util.logging.Level;

public class TarGzUtil {
    
    private final BackeyPlugin plugin;
    private final ConfigManager configManager;
    
    public TarGzUtil(BackeyPlugin plugin, ConfigManager configManager) {
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
                 GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
                 TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
                
                // Set compression level (not directly available for gzip, but we can set buffer size)
                taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                
                // Add all files and directories to tar.gz
                addDirectoryToTar(taos, serverRoot, "", configManager.getExcludeList());
                
                taos.finish();
                
                plugin.debug("tar.gz backup created: " + outputFile.getAbsolutePath());
                plugin.debug("tar.gz backup size: " + (outputFile.length() / (1024 * 1024)) + " MB");
                
                return true;
            }
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to create tar.gz backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void addDirectoryToTar(TarArchiveOutputStream taos, File directory, String basePath, List<String> excludeList) throws IOException {
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
                TarArchiveEntry dirEntry = new TarArchiveEntry(file, relativePath + "/");
                taos.putArchiveEntry(dirEntry);
                taos.closeArchiveEntry();
                
                // Recursively add directory contents
                addDirectoryToTar(taos, file, relativePath, excludeList);
            } else {
                // Add file entry
                addFileToTar(taos, file, relativePath);
            }
        }
    }
    
    private void addFileToTar(TarArchiveOutputStream taos, File file, String relativePath) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, relativePath);
        taos.putArchiveEntry(entry);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                taos.write(buffer, 0, bytesRead);
            }
        }
        
        taos.closeArchiveEntry();
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