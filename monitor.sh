#!/bin/bash
# Docker Monitoring Management Script for Raspberry Pi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "Docker Monitoring Management Script"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start     - Start all monitoring services"
    echo "  stop      - Stop all monitoring services"
    echo "  restart   - Restart all monitoring services"
    echo "  status    - Show status of all containers"
    echo "  logs      - Show logs for all services"
    echo "  cleanup   - Remove all monitoring containers and volumes"
    echo "  urls      - Show access URLs for all services"
    echo "  update    - Pull latest images and restart services"
    echo "  health    - Perform health checks on services"
    echo "  help      - Show this help message"
}

get_pi_ip() {
    hostname -I | awk '{print $1}'
}

show_urls() {
    local pi_ip=$(get_pi_ip)
    echo ""
    print_status "🌐 Access URLs for your Raspberry Pi monitoring services:"
    echo ""
    echo -e "${BLUE}Docker Management:${NC}"
    echo -e "  Portainer (Docker GUI):      http://${pi_ip}:9000"
    echo ""
    echo -e "${BLUE}Metrics & Monitoring:${NC}"
    echo -e "  Grafana (Dashboards):        http://${pi_ip}:3001"
    echo -e "  Prometheus (Metrics):        http://${pi_ip}:9090"
    echo -e "  cAdvisor (Container Metrics): http://${pi_ip}:8080"
    echo -e "  Node Exporter (System):      http://${pi_ip}:9100/metrics"
    echo ""
    echo -e "${BLUE}Your Applications:${NC}"
    echo -e "  Expense Tracker:             http://${pi_ip}:3000"
    echo -e "  H2 Database Console:         http://${pi_ip}:8082"
    echo ""
    print_warning "Default credentials: Grafana (admin/admin123)"
}

start_services() {
    print_status "Starting Docker monitoring services..."

    # Set environment to avoid buildkit warnings
    export DOCKER_BUILDKIT=0
    export COMPOSE_DOCKER_CLI_BUILD=0

    # Start monitoring services
    docker-compose -f docker-compose.monitoring.yml up -d
    docker-compose -f docker-compose.metrics.yml up -d

    sleep 5
    print_status "✅ Monitoring services started!"
    show_status
    show_urls
}

stop_services() {
    print_status "Stopping Docker monitoring services..."
    docker-compose -f docker-compose.monitoring.yml down
    docker-compose -f docker-compose.metrics.yml down
    print_status "✅ Monitoring services stopped!"
}

restart_services() {
    print_status "Restarting Docker monitoring services..."
    stop_services
    sleep 3
    start_services
}

show_status() {
    print_status "Docker container status:"
    echo ""
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(portainer|grafana|prometheus|cadvisor|node-exporter|watchtower|docker-socket-proxy)"
    echo ""

    # Check if main app is running
    if docker ps --format "{{.Names}}" | grep -q "expense-tracker\|h2"; then
        echo -e "${GREEN}Your applications:${NC}"
        docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(expense-tracker|h2)"
    fi
}

show_logs() {
    print_status "Recent logs from monitoring services:"
    echo ""
    echo -e "${BLUE}=== Portainer ===${NC}"
    docker logs --tail=10 portainer 2>/dev/null || echo "Portainer not running"
    echo ""
    echo -e "${BLUE}=== Grafana ===${NC}"
    docker logs --tail=10 grafana 2>/dev/null || echo "Grafana not running"
    echo ""
    echo -e "${BLUE}=== Prometheus ===${NC}"
    docker logs --tail=10 prometheus 2>/dev/null || echo "Prometheus not running"
}

cleanup_services() {
    print_warning "This will remove all monitoring containers and volumes!"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_status "Cleaning up monitoring services..."
        docker-compose -f docker-compose.monitoring.yml down -v
        docker-compose -f docker-compose.metrics.yml down -v
        docker volume prune -f
        print_status "✅ Cleanup complete!"
    else
        print_status "Cleanup cancelled."
    fi
}

update_services() {
    print_status "Updating monitoring services..."
    docker-compose -f docker-compose.monitoring.yml pull
    docker-compose -f docker-compose.metrics.yml pull
    restart_services
    print_status "✅ Update complete!"
}

show_health() {
    print_status "Performing health checks..."
    local app_url="http://localhost:3000/actuator/health"
    local metrics_url="http://localhost:3000/actuator/metrics"
    local prom_url="http://localhost:3000/actuator/prometheus"

    if command -v curl >/dev/null 2>&1; then
        echo -e "${BLUE}App Health:${NC}"; curl -fsS $app_url || echo "Unavailable"; echo
        echo -e "${BLUE}Disk & System (custom):${NC}"; curl -fsS $app_url | grep -E 'diskFreeMb|minRequiredMb' || true; echo
        echo -e "${BLUE}Metrics Endpoint (summary head):${NC}"; curl -fsS $prom_url | head -n 10 || echo "Unavailable"; echo
        echo -e "${BLUE}Key JVM Metrics:${NC}"; curl -fsS "$metrics_url/jvm.memory.used" 2>/dev/null | head -n 40 || echo "Unavailable"; echo
    else
        print_warning "curl not installed; skipping HTTP health checks"
    fi

    echo -e "${BLUE}Docker container states:${NC}"
    docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E '(expense-tracker|prometheus|grafana|cadvisor|node-exporter|portainer)' || true
}

# Main script logic
case "${1:-help}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    cleanup)
        cleanup_services
        ;;
    urls)
        show_urls
        ;;
    update)
        update_services
        ;;
    health)
        show_health
        ;;
    help|*)
        show_help
        ;;
esac
