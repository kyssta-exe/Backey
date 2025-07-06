package com.kyssta.backey;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class PterodactylUtil {
    
    private final BackeyPlugin plugin;
    private final ConfigManager configManager;
    private final Gson gson;
    
    public PterodactylUtil(BackeyPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.gson = new Gson();
    }
    
    public boolean uploadBackup(File localFile, String remoteFileName) {
        try {
            // Step 1: Get upload URL
            String uploadUrl = getUploadUrl();
            if (uploadUrl == null) {
                plugin.log(Level.SEVERE, "Failed to get upload URL from Pterodactyl");
                return false;
            }
            
            // Step 2: Upload file
            boolean uploadSuccess = uploadFile(uploadUrl, localFile, remoteFileName);
            if (!uploadSuccess) {
                plugin.log(Level.SEVERE, "Failed to upload file to Pterodactyl");
                return false;
            }
            
            // Step 3: Move file to backup directory (if not root)
            String backupDir = configManager.getPterodactylBackupDirectory();
            if (!backupDir.equals("/") && !backupDir.isEmpty()) {
                createDirectory(backupDir);
                moveFile(remoteFileName, backupDir + "/" + remoteFileName);
            }
            
            plugin.log(Level.INFO, "Successfully uploaded backup via Pterodactyl: " + remoteFileName);
            return true;
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to upload backup via Pterodactyl: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private String getUploadUrl() {
        try {
            String panelUrl = configManager.getPterodactylPanelUrl();
            String serverId = configManager.getPterodactylServerId();
            String apiKey = configManager.getPterodactylClientApiKey();
            
            URL url = new URL(panelUrl + "/api/client/servers/" + serverId + "/files/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String response = readResponse(conn.getInputStream());
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                return jsonResponse.getAsJsonObject("attributes").get("url").getAsString();
            } else {
                plugin.log(Level.SEVERE, "Failed to get upload URL. Response code: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Error getting upload URL: " + e.getMessage());
            return null;
        }
    }
    
    private boolean uploadFile(String uploadUrl, File file, String fileName) {
        try {
            URL url = new URL(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
                
                // Add file part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"files\"; filename=\"").append(fileName).append("\"\r\n");
                writer.append("Content-Type: application/gzip\r\n");
                writer.append("\r\n");
                writer.flush();
                
                // Write file content
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--\r\n");
            }
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200 || responseCode == 204;
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Error uploading file: " + e.getMessage());
            return false;
        }
    }
    
    private void createDirectory(String path) {
        try {
            String panelUrl = configManager.getPterodactylPanelUrl();
            String serverId = configManager.getPterodactylServerId();
            String apiKey = configManager.getPterodactylClientApiKey();
            
            URL url = new URL(panelUrl + "/api/client/servers/" + serverId + "/files/create-folder");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("root", "/");
            requestBody.addProperty("name", path);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }
            
            conn.getResponseCode(); // Execute request
            
        } catch (Exception e) {
            plugin.debug("Error creating directory (may already exist): " + e.getMessage());
        }
    }
    
    private void moveFile(String from, String to) {
        try {
            String panelUrl = configManager.getPterodactylPanelUrl();
            String serverId = configManager.getPterodactylServerId();
            String apiKey = configManager.getPterodactylClientApiKey();
            
            URL url = new URL(panelUrl + "/api/client/servers/" + serverId + "/files/rename");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("root", "/");
            requestBody.addProperty("from", from);
            requestBody.addProperty("to", to);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }
            
            conn.getResponseCode(); // Execute request
            
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error moving file: " + e.getMessage());
        }
    }
    
    public void cleanupOldBackups() {
        try {
            List<BackupFile> backupFiles = listBackupFiles();
            
            // Sort by modification time (newest first)
            Collections.sort(backupFiles, (a, b) -> Long.compare(b.timestamp, a.timestamp));
            
            // Delete old backups
            int keepCount = configManager.getKeepBackups();
            for (int i = keepCount; i < backupFiles.size(); i++) {
                deleteFile(backupFiles.get(i).filename);
                plugin.log(Level.INFO, "Deleted old backup: " + backupFiles.get(i).filename);
            }
            
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to cleanup old backups: " + e.getMessage());
        }
    }
    
    private List<BackupFile> listBackupFiles() {
        List<BackupFile> backupFiles = new ArrayList<>();
        
        try {
            String panelUrl = configManager.getPterodactylPanelUrl();
            String serverId = configManager.getPterodactylServerId();
            String apiKey = configManager.getPterodactylClientApiKey();
            String backupDir = configManager.getPterodactylBackupDirectory();
            
            URL url = new URL(panelUrl + "/api/client/servers/" + serverId + "/files/list?directory=" + backupDir);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String response = readResponse(conn.getInputStream());
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                JsonArray files = jsonResponse.getAsJsonArray("data");
                
                for (int i = 0; i < files.size(); i++) {
                    JsonObject file = files.get(i).getAsJsonObject().getAsJsonObject("attributes");
                    String filename = file.get("name").getAsString();
                    
                    if (filename.endsWith(".tar.gz") && filename.startsWith(configManager.getFilenamePrefix())) {
                        long timestamp = file.get("modified_at").getAsLong();
                        backupFiles.add(new BackupFile(filename, timestamp));
                    }
                }
            }
            
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error listing backup files: " + e.getMessage());
        }
        
        return backupFiles;
    }
    
    private void deleteFile(String filename) {
        try {
            String panelUrl = configManager.getPterodactylPanelUrl();
            String serverId = configManager.getPterodactylServerId();
            String apiKey = configManager.getPterodactylClientApiKey();
            String backupDir = configManager.getPterodactylBackupDirectory();
            
            URL url = new URL(panelUrl + "/api/client/servers/" + serverId + "/files/delete");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("root", backupDir);
            JsonArray files = new JsonArray();
            files.add(filename);
            requestBody.add("files", files);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }
            
            conn.getResponseCode(); // Execute request
            
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error deleting file: " + e.getMessage());
        }
    }
    
    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
    
    public void cleanup() {
        // No persistent connections to clean up for HTTP-based API
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