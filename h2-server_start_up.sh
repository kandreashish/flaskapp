#!/bin/bash

echo "Checking for processes on port 9092..."
PID=$(lsof -ti:9092)
if [ ! -z "$PID" ]; then
    echo "Killing process $PID on port 9092..."
    kill -9 $PID
    sleep 2
else
    echo "No process found on port 9092"
fi

# Configuration
DB_NAME="expensedb"
H2_PORT=9092
WEB_PORT=8082
H2_USER="h2user"
DB_PASSWORD="ashish123"  # CHANGE THIS!
DATA_DIR="/var/lib/h2-data"         # Recommended production location
H2_JAR="/opt/h2/h2.jar"             # Official H2 jar location
LOG_FILE="/var/log/h2-server.log"   # Log file for background process

# ============= PRE-FLIGHT CHECKS =============
# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found! Install with:"
    echo "sudo apt install openjdk-17-jdk"
    exit 1
fi

# Check if H2 jar exists
if [ ! -f "$H2_JAR" ]; then
    echo "Downloading H2 database..."
    sudo mkdir -p /opt/h2
    sudo wget -q https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar -O "$H2_JAR"
    sudo chmod 644 "$H2_JAR"
fi

# ============= SETUP ENVIRONMENT =============
echo "Configuring H2 environment..."
sudo useradd --system --home "$DATA_DIR" --shell /bin/false "$H2_USER" 2>/dev/null || true
sudo mkdir -p "$DATA_DIR"
sudo chown -R "$H2_USER:$H2_USER" "$DATA_DIR"
sudo chmod 750 "$DATA_DIR"

# Create log file and set permissions
sudo touch "$LOG_FILE"
sudo chown "$H2_USER:$H2_USER" "$LOG_FILE"
sudo chmod 644 "$LOG_FILE"

# ============= START SERVER IN BACKGROUND =============
echo "Starting H2 Server in background..."
echo "• Data Directory: $DATA_DIR"
echo "• TCP Port: $H2_PORT (Remote Connections)"
echo "• Web Console: http://localhost:$WEB_PORT"
echo "• DB Password: ******** (set in script)"
echo "• Log File: $LOG_FILE"

# Start server in background with nohup
nohup sudo -u "$H2_USER" java -cp "$H2_JAR" org.h2.tools.Server \
    -tcp -tcpPort "$H2_PORT" -tcpAllowOthers -tcpPassword "$DB_PASSWORD" \
    -web -webPort "$WEB_PORT" -webAllowOthers \
    -baseDir "$DATA_DIR" \
    -ifNotExists \
    -properties "" \
    > "$LOG_FILE" 2>&1 &

# Get the process ID
SERVER_PID=$!

# Wait a moment to check if server started successfully
sleep 3

# Check if process is still running
if ps -p $SERVER_PID > /dev/null 2>&1; then
    echo "✓ H2 Server started successfully!"
    echo "✓ Process ID: $SERVER_PID"
    echo "✓ Check logs: tail -f $LOG_FILE"
    echo "✓ Web Console: http://localhost:$WEB_PORT"
    echo "✓ To stop server: kill $SERVER_PID"

    # Save PID to file for easy management
    echo $SERVER_PID | sudo tee /var/run/h2-server.pid > /dev/null
    echo "✓ PID saved to /var/run/h2-server.pid"
else
    echo "✗ Failed to start H2 Server"
    echo "Check logs: cat $LOG_FILE"
    exit 1
fi