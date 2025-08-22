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

# Primary host IP (first address)
PRIMARY_IP=$(hostname -I | awk '{print $1}')

# Helper to check container running
is_container_running() {
  local name="$1"
  docker ps --format '{{.Names}}' | grep -qx "$name"
}

# Helper to (re)start a compose file if container not running
ensure_service_running() {
  local compose_file="$1";
  local container_name="$2";
  local service_hint="$3"; # optional service name to limit up
  if is_container_running "$container_name"; then
     print_status "$container_name already running; skipping start"
  else
     print_status "Starting $container_name..."
     if [[ -n "$service_hint" ]]; then
       docker-compose -f "$compose_file" up -d "$service_hint"
     else
       docker-compose -f "$compose_file" up -d
     fi
  fi
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

# Ensure monitoring network exists (needed by metrics stack which expects external network)
if ! docker network inspect monitoring >/dev/null 2>&1; then
    print_status "Creating 'monitoring' docker network..."
    if ! docker network create monitoring; then
        print_error "Failed to create monitoring network"; exit 1; fi
else
    print_status "Docker network 'monitoring' already exists"
fi

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
# Replace inline hostname -I calls with $PRIMARY_IP
echo -e "${BLUE}Portainer (Docker GUI):${NC}     http://$PRIMARY_IP:9000"
echo -e "${BLUE}Grafana (Metrics):${NC}          http://$PRIMARY_IP:3001"
echo -e "${BLUE}Prometheus:${NC}                 http://$PRIMARY_IP:9090"
echo -e "${BLUE}cAdvisor (Container Metrics):${NC} http://$PRIMARY_IP:8080"
echo ""
print_status "Default credentials:"
echo -e "${YELLOW}Grafana:${NC} admin / admin123"
echo -e "${YELLOW}Portainer:${NC} Set up admin user on first login"
echo ""
print_warning "⚠️  Remember to change default passwords after first login!"

# Quick health check for Prometheus & Grafana
print_status "Performing quick health checks..."
if docker ps --format '{{.Names}}' | grep -q '^prometheus$'; then
  if docker logs --tail 20 prometheus 2>&1 | grep -qi 'server is ready'; then
    print_status "Prometheus is up and ready"
  else
    print_warning "Prometheus container running but not yet ready; check: docker logs prometheus"
  fi
else
  print_error "Prometheus container not running"
fi

if docker ps --format '{{.Names}}' | grep -q '^grafana$'; then
  if docker logs --tail 20 grafana 2>&1 | grep -qi 'HTTP Server Listen'; then
    print_status "Grafana is up"
  else
    print_warning "Grafana container running but readiness not confirmed; check: docker logs grafana"
  fi
else
  print_error "Grafana container not running"
fi

# Optional: Start your main application
read -p "Do you want to start your expense tracker application (and H2 DB) as well? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Expense tracker (compose file docker-compose.yml, container expense-tracker-app)
    if [[ -f docker-compose.yml ]]; then
        ensure_service_running "docker-compose.yml" "expense-tracker-app" "expense-tracker"
    else
        print_warning "docker-compose.yml not found; cannot start expense tracker"
    fi

    # H2 database (compose file docker-compose.h2-pi.yml, container h2) - skip if IP is 192.168.1.6
    if [[ -f docker-compose.h2-pi.yml ]]; then
        if [[ "$PRIMARY_IP" == "192.168.1.6" ]]; then
          print_warning "Primary IP is 192.168.1.6; skipping H2 start per policy"
        else
          ensure_service_running "docker-compose.h2-pi.yml" "h2"
        fi
    else
        print_warning "docker-compose.h2-pi.yml not found; cannot start H2 database"
    fi

    # Summary URLs
    if is_container_running "expense-tracker-app"; then
      print_status "Expense Tracker: http://$PRIMARY_IP:3000"
    fi
    if is_container_running "h2"; then
      print_status "H2 Database Console: http://$PRIMARY_IP:8082"
    fi
fi

print_status "✅ All services are running! Check 'docker ps' to verify."
