#!/bin/bash

echo "🔍 Diagnosing Docker image access issues on Raspberry Pi"
echo "=============================================="
echo "Hostname: $(hostname)"
echo "Date: $(date)"
echo "Architecture: $(uname -m)"
echo "OS: $(uname -a)"
echo ""

# Function to run command and show result
run_check() {
    echo "🔍 $1"
    echo "----------------------------------------"
    eval "$2"
    echo ""
}

# Check Docker and Docker Compose versions
run_check "Docker Version" "docker --version"
run_check "Docker Compose Version" "docker-compose --version"

# Check if containers are running
run_check "Container Status" "docker-compose ps"

# Check volume mounts
run_check "Volume List" "docker volume ls | grep app"

# Inspect the uploads volume
run_check "App-uploads Volume Details" "docker volume inspect app-uploads 2>/dev/null || echo 'Volume not found'"

# Health check status
run_check "Health Check Status" "docker-compose ps --format 'table {{.Name}}\t{{.Status}}'"

# Check nginx configuration
run_check "Nginx Configuration Test" "docker exec expense-tracker-nginx nginx -t 2>&1 || echo 'Nginx container not running or config invalid'"

# Test file access from nginx container
echo "🔍 Testing file access from nginx container:"
echo "----------------------------------------"
if docker exec expense-tracker-nginx ls -la /app/uploads/ 2>/dev/null; then
    echo "✅ Nginx can access uploads directory"
    echo "Profile pics directory:"
    docker exec expense-tracker-nginx ls -la /app/uploads/profile-pics/ 2>/dev/null || echo "❌ profile-pics subdirectory not found"
    echo ""
    echo "Sample files (if any):"
    docker exec expense-tracker-nginx find /app/uploads -type f -name "*.jpg" -o -name "*.png" -o -name "*.jpeg" 2>/dev/null | head -5
else
    echo "❌ Nginx cannot access uploads directory"
fi
echo ""

# Test file access from app container
echo "🔍 Testing file access from app container:"
echo "----------------------------------------"
if docker exec expense-tracker-app-1 ls -la /app/uploads/ 2>/dev/null; then
    echo "✅ App can access uploads directory"
    echo "Profile pics directory:"
    docker exec expense-tracker-app-1 ls -la /app/uploads/profile-pics/ 2>/dev/null || echo "❌ profile-pics subdirectory not found"
    echo ""
    echo "Sample files (if any):"
    docker exec expense-tracker-app-1 find /app/uploads -type f -name "*.jpg" -o -name "*.png" -o -name "*.jpeg" 2>/dev/null | head -5
else
    echo "❌ App cannot access uploads directory"
fi
echo ""

# Detailed permission check from app container
echo "🔍 Detailed directory permissions from app container:"
echo "----------------------------------------"
docker exec expense-tracker-app-1 sh -c "
echo 'Current user info:'
whoami
id
echo ''
echo 'Upload directory info:'
stat /app/uploads/ 2>/dev/null || echo 'Cannot stat /app/uploads'
echo ''
if [ -d '/app/uploads/profile-pics' ]; then
    echo 'Profile pics directory info:'
    stat /app/uploads/profile-pics/
    echo ''
    echo 'Files in profile pics:'
    ls -la /app/uploads/profile-pics/
else
    echo 'Profile pics directory does not exist, attempting to create...'
    mkdir -p /app/uploads/profile-pics && echo 'Created successfully' || echo 'Failed to create'
fi
echo ''
echo 'Testing write access:'
touch /app/uploads/test-write-$(date +%s) 2>/dev/null && echo '✅ Can write to uploads' || echo '❌ Cannot write to uploads'
echo ''
echo 'Mount points:'
mount | grep uploads || echo 'No uploads mount found'
echo ''
echo 'Disk usage:'
df -h /app/uploads/ 2>/dev/null || echo 'Cannot check disk usage'
" 2>/dev/null || echo "❌ Cannot execute commands in app container"
echo ""

# Network and connectivity tests
run_check "Testing Health Endpoint" "curl -s -o /dev/null -w 'Health endpoint HTTP status: %{http_code}\n' http://localhost/health 2>/dev/null || echo 'Cannot reach health endpoint'"

run_check "Testing Debug Endpoint" "curl -s -w 'Debug endpoint HTTP status: %{http_code}\n' -o debug-output.json http://localhost/api/files/debug/uploads 2>/dev/null && echo 'Debug response saved to debug-output.json' || echo 'Cannot reach debug endpoint'"

# Show debug output if available
if [ -f "debug-output.json" ]; then
    echo "🔍 Debug Endpoint Response:"
    echo "----------------------------------------"
    cat debug-output.json | head -20
    echo ""
fi

# Container logs
echo "🔍 Recent Application Logs (last 20 lines):"
echo "----------------------------------------"
docker-compose logs --tail=20 app-1 2>/dev/null || echo "Cannot get app logs"
echo ""

echo "🔍 Recent Nginx Logs (last 20 lines):"
echo "----------------------------------------"
docker-compose logs --tail=20 nginx 2>/dev/null || echo "Cannot get nginx logs"
echo ""

# System resources
run_check "System Resources" "
echo 'Memory usage:'
free -h 2>/dev/null || echo 'Cannot get memory info'
echo ''
echo 'CPU info:'
cat /proc/cpuinfo | grep 'model name' | head -1 2>/dev/null || echo 'Cannot get CPU info'
echo ''
echo 'Load average:'
uptime 2>/dev/null || echo 'Cannot get load average'
"

# Docker system info
run_check "Docker System Info" "docker system df 2>/dev/null || echo 'Cannot get docker system info'"

# Test image serving directly
echo "🔍 Testing Image Serving:"
echo "----------------------------------------"
# First, let's see if there are any images to test with
TEST_IMAGE=$(docker exec expense-tracker-app-1 find /app/uploads -name "*.jpg" -o -name "*.png" | head -1 2>/dev/null)
if [ ! -z "$TEST_IMAGE" ]; then
    IMAGE_NAME=$(basename "$TEST_IMAGE")
    echo "Testing with image: $IMAGE_NAME"
    curl -I -s "http://localhost/api/files/profile-pics/$IMAGE_NAME" 2>/dev/null | head -5 || echo "Cannot test image serving"
else
    echo "No test images found in uploads directory"
fi
echo ""

echo "🔧 Potential Fixes:"
echo "----------------------------------------"
echo "If issues found above, trying automatic fixes..."

# Check if we need to fix permissions
if ! docker exec expense-tracker-app-1 test -w /app/uploads/ 2>/dev/null; then
    echo "❌ Upload directory not writable, attempting fixes..."

    echo "1. Stopping services..."
    docker-compose down

    echo "2. Creating local uploads directory with proper permissions..."
    sudo mkdir -p ./uploads/profile-pics 2>/dev/null || mkdir -p ./uploads/profile-pics
    sudo chown -R 1000:1000 ./uploads 2>/dev/null || echo "Cannot change ownership (might not have sudo)"
    sudo chmod -R 755 ./uploads 2>/dev/null || chmod -R 755 ./uploads

    echo "3. Restarting services..."
    docker-compose up -d

    echo "4. Waiting for services to start..."
    sleep 30

    echo "5. Re-testing after fixes..."
    docker exec expense-tracker-app-1 test -w /app/uploads/ && echo "✅ Upload directory now writable" || echo "❌ Still not writable"
else
    echo "✅ Upload directory is writable, no permission fixes needed"
fi

echo ""
echo "📋 Summary and Next Steps:"
echo "----------------------------------------"
echo "✅ Diagnosis complete! Check the output above for issues."
echo ""
echo "🔍 Key things to check:"
echo "1. Are all containers running? (see Container Status above)"
echo "2. Can both nginx and app containers access /app/uploads/?"
echo "3. Are there permission issues? (check Directory permissions above)"
echo "4. Are there any errors in the logs? (see Recent Logs above)"
echo ""
echo "💡 If issues persist:"
echo "1. Share this complete output for analysis"
echo "2. Try: docker-compose down && docker-compose up -d --force-recreate"
echo "3. Check if there's enough disk space"
echo "4. Verify your docker-compose.yml volume mounts"
echo ""
echo "🌐 Test URLs to try:"
echo "- Health: http://$(hostname -I | awk '{print $1}' 2>/dev/null || echo 'YOUR_PI_IP')/health"
echo "- Debug: http://$(hostname -I | awk '{print $1}' 2>/dev/null || echo 'YOUR_PI_IP')/api/files/debug/uploads"
echo ""

# Clean up
rm -f debug-output.json 2>/dev/null

echo "🎯 Diagnostic script completed at $(date)"
