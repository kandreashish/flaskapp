#!/bin/bash

# H2 Database Setup Script for Raspberry Pi
# This script will install and configure H2 database with automatic startup

set -e  # Exit on any error

echo "🚀 Starting H2 Database setup on Raspberry Pi..."
echo "================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
if [[ $EUID -eq 0 ]]; then
   print_error "This script should not be run as root. Please run as a regular user (pi)."
   exit 1
fi

# Step 1: Update system and install Java
print_status "Step 1: Updating system and installing Java..."
sudo apt update
sudo apt install openjdk-11-jre-headless wget curl -y
print_success "Java installation completed"

# Check Java installation
java_version=$(java -version 2>&1 | head -n 1)
print_status "Installed Java version: $java_version"

# Step 2: Create directories and download H2
print_status "Step 2: Setting up H2 directories and downloading H2..."
sudo mkdir -p /opt/h2
sudo mkdir -p /opt/h2-data

# Download H2 if not exists
if [ ! -f "/opt/h2/h2-2.2.224.jar" ]; then
    print_status "Downloading H2 Database JAR..."
    sudo wget -O /opt/h2/h2-2.2.224.jar https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar
    print_success "H2 JAR downloaded successfully"
else
    print_warning "H2 JAR already exists, skipping download"
fi

# Set proper permissions
sudo chown -R pi:pi /opt/h2-data
sudo chmod 755 /opt/h2
sudo chmod 644 /opt/h2/h2-2.2.224.jar

print_success "H2 setup completed"

# Step 3: Create systemd service
print_status "Step 3: Creating systemd service for auto-startup..."

sudo tee /etc/systemd/system/h2-database.service > /dev/null <<EOF
[Unit]
Description=H2 Database Server
Documentation=http://www.h2database.com/
After=network.target

[Service]
Type=simple
User=pi
Group=pi
WorkingDirectory=/opt/h2-data
ExecStart=/usr/bin/java -cp /opt/h2/h2-2.2.224.jar org.h2.tools.Server -tcp -tcpAllowOthers -tcpPort 9092 -web -webAllowOthers -webPort 8082 -baseDir /opt/h2-data
ExecStop=/bin/kill -TERM \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

print_success "Systemd service created"

# Step 4: Enable and start the service
print_status "Step 4: Enabling and starting H2 service..."
sudo systemctl daemon-reload
sudo systemctl enable h2-database.service
sudo systemctl start h2-database.service

# Wait a moment for service to start
sleep 3

# Check service status
if sudo systemctl is-active --quiet h2-database.service; then
    print_success "H2 service is running!"
else
    print_error "H2 service failed to start. Checking logs..."
    sudo systemctl status h2-database.service
    exit 1
fi

# Step 5: Configure firewall (if UFW is installed)
if command -v ufw &> /dev/null; then
    print_status "Step 5: Configuring firewall..."
    sudo ufw allow 9092/tcp comment "H2 Database TCP"
    sudo ufw allow 8082/tcp comment "H2 Web Console"
    print_success "Firewall rules added"
else
    print_warning "UFW not installed, skipping firewall configuration"
fi

# Step 6: Get system information
print_status "Getting system information..."
PI_IP=$(hostname -I | awk '{print $1}')
HOSTNAME=$(hostname)

# Step 7: Create management scripts
print_status "Creating H2 management scripts..."

# Create start script
sudo tee /usr/local/bin/h2-start > /dev/null <<EOF
#!/bin/bash
sudo systemctl start h2-database.service
echo "H2 Database service started"
sudo systemctl status h2-database.service --no-pager
EOF

# Create stop script
sudo tee /usr/local/bin/h2-stop > /dev/null <<EOF
#!/bin/bash
sudo systemctl stop h2-database.service
echo "H2 Database service stopped"
EOF

# Create restart script
sudo tee /usr/local/bin/h2-restart > /dev/null <<EOF
#!/bin/bash
sudo systemctl restart h2-database.service
echo "H2 Database service restarted"
sudo systemctl status h2-database.service --no-pager
EOF

# Create status script
sudo tee /usr/local/bin/h2-status > /dev/null <<EOF
#!/bin/bash
echo "H2 Database Service Status:"
echo "=========================="
sudo systemctl status h2-database.service --no-pager
echo ""
echo "Active connections:"
sudo netstat -tlnp | grep :9092 || echo "No active connections on port 9092"
echo ""
echo "Recent logs:"
sudo journalctl -u h2-database.service --no-pager -n 10
EOF

# Create logs script
sudo tee /usr/local/bin/h2-logs > /dev/null <<EOF
#!/bin/bash
echo "Following H2 Database logs (Ctrl+C to exit):"
sudo journalctl -u h2-database.service -f
EOF

# Make scripts executable
sudo chmod +x /usr/local/bin/h2-{start,stop,restart,status,logs}

print_success "Management scripts created"

# Final status check and information display
echo ""
echo "🎉 H2 Database Setup Completed Successfully!"
echo "==========================================="
echo ""
echo "📊 System Information:"
echo "  Raspberry Pi IP: $PI_IP"
echo "  Hostname: $HOSTNAME"
echo ""
echo "🔗 Connection Details:"
echo "  Database TCP URL: jdbc:h2:tcp://$PI_IP:9092/expensedb"
echo "  Web Console: http://$PI_IP:8082"
echo "  Username: sa"
echo "  Password: (leave empty)"
echo ""
echo "📁 File Locations:"
echo "  H2 JAR: /opt/h2/h2-2.2.224.jar"
echo "  Data Directory: /opt/h2-data"
echo "  Service File: /etc/systemd/system/h2-database.service"
echo ""
echo "🛠️  Management Commands:"
echo "  Start:   h2-start"
echo "  Stop:    h2-stop"
echo "  Restart: h2-restart"
echo "  Status:  h2-status"
echo "  Logs:    h2-logs"
echo ""
echo "🔧 Manual Service Commands:"
echo "  sudo systemctl start h2-database.service"
echo "  sudo systemctl stop h2-database.service"
echo "  sudo systemctl restart h2-database.service"
echo "  sudo systemctl status h2-database.service"
echo ""
echo "📝 Update your application's .env file with:"
echo "  SPRING_DATASOURCE_URL=jdbc:h2:tcp://$PI_IP:9092/expensedb"
echo ""

# Test the web console
print_status "Testing web console availability..."
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8082 | grep -q "200\|302"; then
    print_success "Web console is accessible at http://$PI_IP:8082"
else
    print_warning "Web console test failed, but service appears to be running"
fi

print_success "Setup complete! H2 will automatically start on boot."
