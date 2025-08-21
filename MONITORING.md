# Docker Container Monitoring for Raspberry Pi

This setup provides comprehensive Docker container monitoring for your expense tracker application running on Raspberry Pi.

## 🎯 What's Included

### 1. **Portainer** - Docker GUI Management
- **URL**: `http://your-pi-ip:9000`
- **Purpose**: Visual Docker container management, logs, console access
- **Features**: Start/stop containers, view logs, manage volumes, networks

### 2. **Grafana** - Metrics Dashboard
- **URL**: `http://your-pi-ip:3001`
- **Default Login**: `admin / admin123`
- **Purpose**: Beautiful dashboards for system and container metrics
- **Features**: CPU, memory, disk usage, container performance graphs

### 3. **Prometheus** - Metrics Collection
- **URL**: `http://your-pi-ip:9090`
- **Purpose**: Collects and stores metrics from all sources
- **Features**: Query metrics, set up alerts, data retention

### 4. **cAdvisor** - Container Metrics
- **URL**: `http://your-pi-ip:8080`
- **Purpose**: Real-time container resource usage and performance
- **Features**: Per-container CPU, memory, network, filesystem metrics

### 5. **Node Exporter** - System Metrics
- **URL**: `http://your-pi-ip:9100/metrics`
- **Purpose**: System-level metrics (CPU, memory, disk, network)
- **Features**: Hardware monitoring, OS-level statistics

## 🚀 Quick Start

### 1. Initial Setup (One-time)
```bash
# Copy files to your Raspberry Pi
scp -r * pi@your-pi-ip:~/expense-tracker/

# SSH to your Raspberry Pi
ssh pi@your-pi-ip
cd ~/expense-tracker

# Run the setup script
./start-monitoring.sh
```

### 2. Daily Management
```bash
# Start all monitoring services
./monitor.sh start

# Check status of all containers
./monitor.sh status

# View access URLs
./monitor.sh urls

# View recent logs
./monitor.sh logs

# Stop monitoring services
./monitor.sh stop

# Restart everything
./monitor.sh restart
```

## 📊 Dashboard Setup

### Grafana Dashboard Configuration

1. **Access Grafana**: `http://your-pi-ip:3001`
2. **Login**: `admin / admin123`
3. **Import Dashboard**:
   - Go to **+** → **Import**
   - Use Dashboard ID: `179` (Docker Prometheus Monitoring)
   - Or ID: `893` (Docker and System Monitoring)

### Popular Dashboard IDs for Import:
- **179**: Docker Prometheus Monitoring
- **893**: Docker and System Monitoring  
- **1860**: Node Exporter Full
- **10619**: Docker Host & Container Overview

## 🔧 Configuration Files

### Prometheus Configuration (`monitoring/prometheus.yml`)
```yaml
scrape_configs:
  - job_name: 'expense-tracker'
    static_configs:
      - targets: ['expense-tracker-pi:3000']
  - job_name: 'cadvisor'
    static_configs:
      - targets: ['cadvisor:8080']
  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']
```

### Environment Variables (Create `.env` file)
```bash
# Grafana
GF_SECURITY_ADMIN_PASSWORD=your-secure-password

# Portainer
PORTAINER_PASSWORD=your-secure-password

# Your application
SPRING_DATASOURCE_URL=jdbc:h2:tcp://h2:9092//opt/h2-data/expensedb
JWT_SECRET=your-jwt-secret
FIREBASE_STORAGE_BUCKET=your-firebase-bucket
```

## 🛡️ Security Best Practices

### 1. Change Default Passwords
```bash
# Grafana - Change on first login
# Portainer - Set admin password on first access
```

### 2. Firewall Configuration (Optional)
```bash
# Allow only specific ports
sudo ufw allow 22      # SSH
sudo ufw allow 3000    # Your app
sudo ufw allow 9000    # Portainer
sudo ufw allow 3001    # Grafana
sudo ufw enable
```

### 3. Reverse Proxy with Nginx (Optional)
- Access all services through subdomains
- Add SSL certificates
- Basic authentication

## 📱 Mobile Access

### Grafana Mobile App
1. Download **Grafana Mobile** app
2. Add server: `http://your-pi-ip:3001`
3. Login with your credentials
4. View dashboards on mobile

### Portainer Mobile Access
- Portainer web interface is mobile-responsive
- Access directly via browser

## 🔍 Monitoring Your Expense Tracker

### Application Metrics
- **HTTP Request Count**: Number of API calls
- **Response Times**: Average response time for endpoints
- **Error Rates**: Failed requests and exceptions
- **Active Users**: Current logged-in users

### Database Metrics (H2)
- **Connection Pool**: Active/idle connections
- **Query Performance**: Slow queries
- **Database Size**: Storage usage
- **Transaction Rates**: Commits/rollbacks per second

### System Metrics
- **CPU Usage**: Per-core utilization
- **Memory Usage**: Available/used RAM
- **Disk I/O**: Read/write operations
- **Network Traffic**: Inbound/outbound data

## 🚨 Alerting Setup

### Grafana Alerts
1. Go to **Alerting** → **Alert Rules**
2. Create alerts for:
   - High CPU usage (>80%)
   - Low disk space (<10%)
   - Container crashes
   - High memory usage (>90%)

### Notification Channels
- **Email**: Configure SMTP in Grafana
- **Discord/Slack**: Webhook notifications
- **Telegram**: Bot notifications

## 🔧 Troubleshooting

### Common Issues

#### Containers Not Starting
```bash
# Check logs
./monitor.sh logs

# Check Docker status
docker ps -a

# Restart Docker service
sudo systemctl restart docker
```

#### Can't Access Web Interfaces
```bash
# Check if ports are open
netstat -tlnp | grep -E "(9000|3001|9090)"

# Check firewall
sudo ufw status

# Check container status
docker ps
```

#### High Resource Usage
```bash
# Check system resources
htop

# Check container resources
docker stats

# Restart resource-heavy containers
docker restart cadvisor grafana
```

### Performance Optimization for Raspberry Pi

#### Memory Optimization
```bash
# Add to docker-compose.yml for each service:
deploy:
  resources:
    limits:
      memory: 256M
    reservations:
      memory: 128M
```

#### Storage Optimization
```bash
# Clean up old data
docker system prune -a

# Limit Prometheus retention
# In prometheus.yml: --storage.tsdb.retention.time=7d
```

## 📊 Sample Queries

### Prometheus Queries
```promql
# Container CPU usage
rate(container_cpu_usage_seconds_total[5m]) * 100

# Container memory usage
container_memory_usage_bytes / container_spec_memory_limit_bytes * 100

# Disk usage
(node_filesystem_size_bytes - node_filesystem_free_bytes) / node_filesystem_size_bytes * 100

# Network traffic
rate(container_network_receive_bytes_total[5m])
```

## 🔄 Backup and Restore

### Backup Configuration
```bash
# Create backup script
./backup-monitoring.sh

# Backup includes:
# - Grafana dashboards and settings
# - Prometheus data
# - Container configurations
```

### Restore Process
```bash
# Restore from backup
./restore-monitoring.sh backup-date

# Or manual restore:
docker-compose down
# Copy backup files
docker-compose up -d
```

## 📞 Support

For issues or questions:
1. Check the troubleshooting section above
2. View container logs: `./monitor.sh logs`
3. Check system resources: `htop` or `docker stats`
4. Restart services: `./monitor.sh restart`

---

## 🎉 You're All Set!

Your Raspberry Pi now has enterprise-grade Docker monitoring! 

**Next Steps:**
1. Access Portainer at `http://your-pi-ip:9000` for container management
2. Set up Grafana dashboards at `http://your-pi-ip:3001`
3. Configure alerts for proactive monitoring
4. Enjoy monitoring your expense tracker application! 📊

**Remember**: Change default passwords and secure your setup!
