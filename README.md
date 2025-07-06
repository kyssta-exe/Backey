# Backey - Minecraft Server Backup Plugin

![Java](https://img.shields.io/badge/Java-8+-orange.svg)
![Spigot](https://img.shields.io/badge/Spigot-1.20.1+-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

Backey is a powerful and flexible Minecraft server backup plugin that automatically creates compressed backups of your server and uploads them to remote storage. It supports multiple upload methods including SFTP and Pterodactyl Panel integration.

## Features

- 🔄 **Automatic Scheduled Backups** - Set custom intervals for automated backups
- 📦 **tar.gz Compression** - Efficient compression to minimize backup file sizes
- 🌐 **Multiple Upload Methods** - SFTP, Pterodactyl Panel, or local storage
- 🔐 **Secure Authentication** - SSH key-based authentication support
- 🗂️ **Smart File Exclusion** - Exclude unnecessary files and directories
- 🧹 **Automatic Cleanup** - Keep only a specified number of recent backups
- ⚡ **Asynchronous Operations** - Non-blocking backup process
- 🔧 **Easy Configuration** - Simple YAML configuration file
- 📊 **Debug Logging** - Detailed logging for troubleshooting

## Installation

1. Download the latest `Backey-1.0.0.jar` from the releases page
2. Place the JAR file in your server's `plugins` directory
3. Restart your server
4. Configure the plugin by editing `plugins/Backey/config.yml`
5. Reload the configuration with `/backup-reload`

## Configuration

### Basic Configuration

```yaml
# Upload method: "sftp", "pterodactyl", or "local"
upload-method: "sftp"

# Backup Configuration
backup:
  interval-minutes: 60        # Backup every hour
  keep-backups: 2            # Keep 2 most recent backups
  filename-prefix: "server-backup"
  compression-level: 6       # 1-9, higher = better compression
```

### SFTP Configuration

```yaml
sftp:
  enabled: true
  host: "your-server.com"
  port: 22
  username: "backup-user"
  password: ""               # Leave empty if using private key
  private-key-path: "plugins/Backey/ssh/id_rsa"
  private-key-passphrase: "" # If your key has a passphrase
  remote-directory: "/home/backups/minecraft"
  timeout: 30
```

### Pterodactyl Configuration

```yaml
pterodactyl:
  panel-url: "https://panel.example.com"
  client-api-key: "your-client-api-key"
  server-id: "your-server-id"
  backup-directory: "/backups"
```

### File Exclusions

```yaml
exclude:
  - "plugins/Backey/backups"
  - "plugins/Backey/temp"
  - "logs"
  - "crash-reports"
  - "*.log"
  - "*.tmp"
  - "cache"
  - "world/session.lock"
```

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/backup` | `backey.backup` | Manually trigger a backup |
| `/backup-reload` | `backey.reload` | Reload plugin configuration |
| `/backup-test` | `backey.backup` | Test SFTP connection |

## Permissions

- `backey.backup` - Allows manual backup triggering and connection testing
- `backey.reload` - Allows reloading plugin configuration

## Setup Guides

### SFTP Setup

#### 1. Generate SSH Key Pair (if you don't have one)

```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/minecraft_backup
```

#### 2. Copy Public Key to Remote Server

```bash
ssh-copy-id -i ~/.ssh/minecraft_backup.pub user@your-server.com
```

#### 3. Configure Backey

Place your private key in `plugins/Backey/ssh/id_rsa` and update the configuration:

```yaml
sftp:
  enabled: true
  host: "your-server.com"
  username: "your-username"
  private-key-path: "plugins/Backey/ssh/id_rsa"
  remote-directory: "/path/to/backup/directory"
  password: "if there otherwise leave empty"
```

### Pterodactyl Setup

#### 1. Get API Credentials

1. Log into your Pterodactyl panel
2. Go to Account Settings → API Credentials
3. Create a new API key with appropriate permissions

#### 2. Find Server ID

Your server ID can be found in the server URL or server settings.

#### 3. Configure Backey

```yaml
upload-method: "pterodactyl"
pterodactyl:
  panel-url: "https://your-panel.com"
  client-api-key: "ptlc_your-api-key-here"
  server-id: "your-server-id"
```

## Troubleshooting

### Common Issues

#### Authentication Failed (SFTP)

1. **Check credentials**: Verify username and password/key are correct
2. **Test manually**: Try connecting with an SFTP client
3. **Check permissions**: Ensure the user has SFTP access
4. **Verify key format**: Make sure the private key is in the correct format

```bash
# Test connection manually
ssh -i /path/to/private/key username@hostname
```

#### Algorithm Negotiation Fail

This usually indicates SSH server compatibility issues. Try:

1. **Update SSH server configuration** to allow legacy algorithms:

```bash
# Add to /etc/ssh/sshd_config
KexAlgorithms +diffie-hellman-group1-sha1,diffie-hellman-group14-sha1
Ciphers +aes128-cbc,3des-cbc
MACs +hmac-md5,hmac-sha1
```

2. **Restart SSH service**:

```bash
sudo systemctl restart ssh
```

#### Connection Timeout

1. Check firewall settings
2. Verify the host is reachable
3. Increase timeout value in configuration

### Debug Mode

Enable debug logging for detailed troubleshooting:

```yaml
debug: true
```

This will provide detailed logs about the backup process, connection attempts, and file operations.

## File Structure

```
plugins/Backey/
├── config.yml          # Main configuration file
├── backups/            # Local backup storage
├── temp/               # Temporary files during backup creation
└── ssh/                # SSH keys directory
    └── id_rsa          # Private key file
```

## Performance Considerations

- **Backup Size**: Large servers may create large backup files. Monitor disk space.
- **Compression**: Higher compression levels use more CPU but create smaller files.
- **Network**: Upload speed depends on your server's internet connection.
- **Timing**: Schedule backups during low-activity periods.

## Security Best Practices

1. **Use SSH Keys**: Prefer key-based authentication over passwords
2. **Restrict Permissions**: Create dedicated backup users with minimal permissions
3. **Secure Storage**: Ensure backup storage locations are properly secured
4. **Regular Testing**: Periodically test backup restoration procedures

## Building from Source

### Prerequisites

- Java 8 or higher
- Maven 3.6+

### Build Steps

```bash
git clone https://github.com/kyssta-exe/backey.git
cd backey
mvn clean package
```

The compiled JAR will be in the `target/` directory.

## Dependencies

- **Spigot API** 1.21+ (provided)
- **JSch** 0.1.55 (for SFTP functionality)
- **Apache Commons Compress** 1.21 (for tar.gz compression)
- **Gson** 2.8.9 (for JSON handling)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request


## Support

- **Issues**: Report bugs on the [GitHub Issues](https://github.com/kyssta-exe/backey/issues) page
- **Documentation**: Check this README and the configuration comments
- **Community**: Join our Discord server for community support

## Changelog

### Version 1.0.0
- Initial release
- SFTP upload support
- Pterodactyl Panel integration
- tar.gz compression
- Automatic cleanup
- Configurable exclusions
- Debug logging

---

**Made with ❤️ for the Server Owners**
