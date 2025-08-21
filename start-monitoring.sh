#!/bin/bash
# Docker Monitoring Deployment Script for Raspberry Pi

echo "🐳 Setting up Docker Container Monitoring on Raspberry Pi..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker first."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose > /dev/null 2>&1; then
    print_error "Docker Compose is not installed. Please install it first."
    exit 1
fi

# Set environment variables to avoid buildkit warnings
export DOCKER_BUILDKIT=0
export COMPOSE_DOCKER_CLI_BUILD=0

print_status "Environment variables set to disable buildkit warnings"

# Create necessary directories
print_status "Creating monitoring directories..."
mkdir -p logs uploads monitoring/grafana/{dashboards,datasources}

# Set proper permissions for Grafana
sudo chown -R 472:472 monitoring/grafana/ 2>/dev/null || {
    print_warning "Could not set Grafana permissions. You may need to run with sudo."
}

# Deploy monitoring stack
print_status "Deploying Portainer (Docker GUI)..."
docker-compose -f docker-compose.monitoring.yml up -d

sleep 5

print_status "Deploying Prometheus + Grafana metrics stack..."
docker-compose -f docker-compose.metrics.yml up -d

sleep 10

print_status "Checking container status..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
print_status "🎉 Docker monitoring setup complete!"
echo ""
print_status "Access your monitoring tools:"
echo -e "${BLUE}Portainer (Docker GUI):${NC}     http://$(hostname -I | awk '{print $1}'):9000"
echo -e "${BLUE}Grafana (Metrics):${NC}          http://$(hostname -I | awk '{print $1}'):3001"
echo -e "${BLUE}Prometheus:${NC}                 http://$(hostname -I | awk '{print $1}'):9090"
echo -e "${BLUE}cAdvisor (Container Metrics):${NC} http://$(hostname -I | awk '{print $1}'):8080"
echo ""
print_status "Default credentials:"
echo -e "${YELLOW}Grafana:${NC} admin / admin123"
echo -e "${YELLOW}Portainer:${NC} Set up admin user on first login"
echo ""
print_warning "⚠️  Remember to change default passwords after first login!"

# Optional: Start your main application
read -p "Do you want to start your expense tracker application as well? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_status "Starting expense tracker application..."
    docker-compose -f docker-compose.h2-pi.yml up -d
    echo ""
    print_status "Expense Tracker: http://$(hostname -I | awk '{print $1}'):3000"
    print_status "H2 Database Console: http://$(hostname -I | awk '{print $1}'):8082"
fi

print_status "✅ All services are running! Check 'docker ps' to verify."
