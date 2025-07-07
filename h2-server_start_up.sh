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

# ============= START SERVER =============
echo "Starting H2 Server..."
echo "• Data Directory: $DATA_DIR"
echo "• TCP Port: $H2_PORT (Remote Connections)"
echo "• Web Console: http://localhost:$WEB_PORT"
echo "• DB Password: ******** (set in script)"

exec sudo -u "$H2_USER" java -cp "$H2_JAR" org.h2.tools.Server \
    -tcp -tcpPort "$H2_PORT" -tcpAllowOthers -tcpPassword "$DB_PASSWORD" \
    -web -webPort "$WEB_PORT" -webAllowOthers \
    -baseDir "$DATA_DIR" \
    -ifNotExists \
    -properties ""
