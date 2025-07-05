# Remote H2 Database Server Setup Guide

## Current Issue
Your application is trying to connect to: `103.80.162.46:9092`
But the connection test shows: ❌ Port 9092 is not accessible

## Setup Remote H2 Server (on 103.80.162.46)

### Step 1: SSH into the remote server
```bash
ssh user@103.80.162.46
```

### Step 2: Download H2 Database (if not already installed)
```bash
# Download H2 database
wget https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar

# Or if you have it in your project, copy the jar file
# scp ~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.2.224/7bdade27d8cd197d9b5ce9dc251f41d2edc5f7ad/h2-2.2.224.jar user@103.80.162.46:/path/to/h2/
```

### Step 3: Create database directory with proper permissions
```bash
mkdir -p /home/pi/Desktop/flaskapp/data/h2
chmod 755 /home/pi/Desktop/flaskapp/data/h2
```

### Step 4: Start H2 TCP Server with write permissions
```bash
# Start H2 server with full read-write access
java -cp h2-2.2.224.jar org.h2.tools.Server \
    -tcp -tcpAllowOthers -tcpPort 9092 \
    -web -webAllowOthers -webPort 8082 \
    -baseDir /home/pi/Desktop/flaskapp/data/h2 \
    -ifNotExists

# Or run in background
nohup java -cp h2-2.2.224.jar org.h2.tools.Server \
    -tcp -tcpAllowOthers -tcpPort 9092 \
    -web -webAllowOthers -webPort 8082 \
    -baseDir /home/pi/Desktop/flaskapp/data/h2 \
    -ifNotExists > h2-server.log 2>&1 &
```

### Step 5: Configure Firewall (Ubuntu/Debian)
```bash
# Allow port 9092 for H2 TCP connections
sudo ufw allow 9092
sudo ufw allow 8082  # For web console (optional)

# Check firewall status
sudo ufw status
```

### Step 6: For CentOS/RHEL/Rocky Linux
```bash
# Allow port 9092
sudo firewall-cmd --permanent --add-port=9092/tcp
sudo firewall-cmd --permanent --add-port=8082/tcp
sudo firewall-cmd --reload

# Check firewall status
sudo firewall-cmd --list-ports
```

### Step 7: Verify H2 server is running
```bash
# Check if H2 is listening on port 9092
netstat -tlnp | grep 9092
# or
lsof -i :9092

# Check H2 server logs
tail -f h2-server.log
```

## Testing the Connection

### From your local machine:
```bash
# Test TCP connection
telnet 103.80.162.46 9092

# Test with timeout
timeout 10 bash -c 'echo > /dev/tcp/103.80.162.46/9092' && echo "Connected" || echo "Failed"
```

## Database File Permissions
Ensure the database files have proper permissions:
```bash
# On the remote server
chmod 664 /home/pi/Desktop/flaskapp/data/h2/expensedb.mv.db
chmod 664 /home/pi/Desktop/flaskapp/data/h2/expensedb.trace.db
chown -R pi:pi /home/pi/Desktop/flaskapp/data/h2/
```

## Troubleshooting

### Common Issues:
1. **Port blocked**: Check firewall settings
2. **Permission denied**: Ensure database directory is writable
3. **Database locked**: Stop all connections and restart H2 server
4. **Connection refused**: H2 server not running or wrong port

### Useful Commands:
```bash
# Check what's using port 9092
sudo lsof -i :9092

# Kill H2 process if needed
pkill -f "h2.*Server"

# Check H2 server status
ps aux | grep h2
```
