#!/bin/bash

# Enhanced H2 Server Startup Script with Better Error Handling
set -e  # Exit on any error

# Configuration
DB_NAME="expensedb"
H2_PORT=9092
WEB_PORT=8082
H2_USER="h2user"
DB_PASSWORD="ashish123"  # CHANGE THIS!
DATA_DIR="/var/lib/h2-data"
H2_JAR="/opt/h2/h2.jar"
LOG_FILE="/var/log/h2-server.log"
PID_FILE="/var/run/h2-server.pid"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Function to check if port is in use
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0  # Port is in use
    else
        return 1  # Port is free
    fi
}

# Function to kill process on port
kill_process_on_port() {
    local port=$1
    echo "Checking for processes on port $port..."

    if check_port $port; then
        local PID=$(lsof -ti:$port)
        print_warning "Found process $PID on port $port. Attempting to kill..."

        # Try graceful shutdown first
        kill $PID 2>/dev/null || true
        sleep 3

        # If still running, force kill
        if check_port $port; then
            print_warning "Process still running. Force killing..."
            kill -9 $PID 2>/dev/null || true
            sleep 2
        fi

        # Final check
        if check_port $port; then
            print_error "Unable to free port $port. Please manually stop the process."
            exit 1
        else
            print_status "Port $port is now free"
        fi
    else
        print_status "Port $port is already free"
    fi
}

# Function to validate Java installation
check_java() {
    if ! command -v java &> /dev/null; then
        print_error "Java not found! Please install Java:"
        echo "Ubuntu/Debian: sudo apt install openjdk-17-jdk"
        echo "CentOS/RHEL: sudo yum install java-17-openjdk"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    print_status "Java found: $java_version"
}

# Function to download H2 if needed
setup_h2() {
    if [ ! -f "$H2_JAR" ]; then
        print_warning "H2 jar not found. Downloading..."

        # Create directory
        sudo mkdir -p /opt/h2

        # Download H2 jar
        if ! sudo wget -q --timeout=30 https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar -O "$H2_JAR"; then
            print_error "Failed to download H2 jar. Please check your internet connection."
            exit 1
        fi

        sudo chmod 644 "$H2_JAR"
        print_status "H2 jar downloaded successfully"
    else
        print_status "H2 jar found at $H2_JAR"
    fi
}

# Function to setup environment
setup_environment() {
    print_status "Configuring H2 environment..."

    # Create user if doesn't exist
    if ! id "$H2_USER" &>/dev/null; then
        sudo useradd --system --home "$DATA_DIR" --shell /bin/false "$H2_USER"
        print_status "Created user: $H2_USER"
    else
        print_status "User $H2_USER already exists"
    fi

    # Create and configure data directory
    sudo mkdir -p "$DATA_DIR"
    sudo chown -R "$H2_USER:$H2_USER" "$DATA_DIR"
    sudo chmod 750 "$DATA_DIR"

    # Create log directory if it doesn't exist
    sudo mkdir -p "$(dirname "$LOG_FILE")"

    # Create and configure log file with proper permissions
    sudo touch "$LOG_FILE"
    sudo chown "$H2_USER:$H2_USER" "$LOG_FILE"
    sudo chmod 664 "$LOG_FILE"

    # Create PID file directory
    sudo mkdir -p "$(dirname "$PID_FILE")"

    print_status "Environment setup complete"
}

# Function to start H2 server
start_h2_server() {
    print_status "Starting H2 Server..."
    echo "• Data Directory: $DATA_DIR"
    echo "• TCP Port: $H2_PORT (Remote Connections)"
    echo "• Web Console: http://localhost:$WEB_PORT"
    echo "• Log File: $LOG_FILE"

    # Clear previous log content and ensure proper permissions
    sudo truncate -s 0 "$LOG_FILE"
    sudo chown "$H2_USER:$H2_USER" "$LOG_FILE"
    sudo chmod 664 "$LOG_FILE"

    # Start server in background with proper user context
    sudo -u "$H2_USER" bash -c "
        nohup java -cp '$H2_JAR' org.h2.tools.Server \
            -tcp -tcpPort $H2_PORT -tcpAllowOthers -tcpPassword '$DB_PASSWORD' \
            -web -webPort $WEB_PORT -webAllowOthers \
            -baseDir '$DATA_DIR' \
            -ifNotExists \
            > '$LOG_FILE' 2>&1 &
        echo \$!
    " > /tmp/h2_pid.tmp

    # Get the process ID
    local SERVER_PID=$(cat /tmp/h2_pid.tmp)
    rm -f /tmp/h2_pid.tmp

    # Wait for server to start
    sleep 5

    # Check if process is still running
    if ps -p $SERVER_PID > /dev/null 2>&1; then
        # Double check by testing the ports
        local tcp_ready=false
        local web_ready=false

        for i in {1..10}; do
            if check_port $H2_PORT; then
                tcp_ready=true
            fi
            if check_port $WEB_PORT; then
                web_ready=true
            fi

            if $tcp_ready && $web_ready; then
                break
            fi
            sleep 1
        done

        if $tcp_ready && $web_ready; then
            print_status "H2 Server started successfully!"
            print_status "Process ID: $SERVER_PID"
            print_status "TCP Port: $H2_PORT is active"
            print_status "Web Console: http://localhost:$WEB_PORT"

            # Save PID to file
            echo $SERVER_PID | sudo tee "$PID_FILE" > /dev/null
            print_status "PID saved to $PID_FILE"

            echo ""
            echo "Connection Details:"
            echo "• JDBC URL: jdbc:h2:tcp://localhost:$H2_PORT/$DATA_DIR/$DB_NAME"
            echo "• Username: sa"
            echo "• Password: (leave empty or set your own)"
            echo ""
            echo "Management Commands:"
            echo "• View logs: tail -f $LOG_FILE"
            echo "• Stop server: kill $SERVER_PID"
            echo "• Web console: http://localhost:$WEB_PORT"

        else
            print_error "Server process started but ports are not accessible"
            print_error "TCP Port $H2_PORT ready: $tcp_ready"
            print_error "Web Port $WEB_PORT ready: $web_ready"
            show_logs_and_exit
        fi
    else
        print_error "Failed to start H2 Server - process died"
        show_logs_and_exit
    fi
}

# Function to show logs and exit
show_logs_and_exit() {
    echo ""
    print_error "Server startup failed. Recent log entries:"
    echo "----------------------------------------"
    sudo tail -20 "$LOG_FILE" 2>/dev/null || echo "No log entries found"
    echo "----------------------------------------"
    echo ""
    echo "Troubleshooting tips:"
    echo "1. Check if ports $H2_PORT and $WEB_PORT are available"
    echo "2. Verify Java version compatibility"
    echo "3. Check file permissions for $DATA_DIR"
    echo "4. Review full logs: cat $LOG_FILE"
    exit 1
}

# ============= MAIN EXECUTION =============
echo "H2 Database Server Startup Script"
echo "=================================="

# Pre-flight checks
kill_process_on_port $H2_PORT
kill_process_on_port $WEB_PORT
check_java
setup_h2
setup_environment
start_h2_server

print_status "H2 Server startup completed successfully!"