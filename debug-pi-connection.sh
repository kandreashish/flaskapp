#!/bin/bash

echo "=== H2 Database Connection Troubleshooting Script for Raspberry Pi ==="
echo "Database Server: 103.80.162.46:9092"
echo ""

# Function to test network connectivity
test_connectivity() {
    echo "1. Testing basic network connectivity..."

    # Test ping to server
    echo "   Testing ping to 103.80.162.46..."
    if ping -c 3 103.80.162.46 > /dev/null 2>&1; then
        echo "   ✅ Ping successful - Server is reachable"
    else
        echo "   ❌ Ping failed - Check network connection"
        return 1
    fi

    # Test port connectivity
    echo "   Testing port 9092 connectivity..."
    if command -v nc >/dev/null 2>&1; then
        if nc -zv 103.80.162.46 9092 2>&1 | grep -q "succeeded\|open"; then
            echo "   ✅ Port 9092 is accessible"
        else
            echo "   ❌ Port 9092 is not accessible"
            echo "   This is likely the main issue!"
        fi
    elif command -v telnet >/dev/null 2>&1; then
        echo "   Using telnet to test (will timeout if blocked)..."
        timeout 5 telnet 103.80.162.46 9092 && echo "   ✅ Port accessible" || echo "   ❌ Port not accessible"
    else
        echo "   ⚠️  Neither nc nor telnet available for port testing"
    fi
}

# Function to check local firewall
check_local_firewall() {
    echo ""
    echo "2. Checking local firewall settings..."

    if command -v ufw >/dev/null 2>&1; then
        echo "   UFW Status:"
        sudo ufw status
    elif command -v iptables >/dev/null 2>&1; then
        echo "   Checking iptables for outbound restrictions..."
        sudo iptables -L OUTPUT -n | grep -E "(9092|REJECT|DROP)" || echo "   No obvious outbound restrictions found"
    else
        echo "   No common firewall tools found"
    fi
}

# Function to test DNS resolution
test_dns() {
    echo ""
    echo "3. Testing DNS resolution..."

    if nslookup 103.80.162.46 >/dev/null 2>&1; then
        echo "   ✅ DNS resolution working"
    else
        echo "   ⚠️  DNS resolution issue (though IP should work directly)"
    fi
}

# Function to test with different methods
test_connection_methods() {
    echo ""
    echo "4. Testing H2 connection with different methods..."

    # Test with curl if available
    if command -v curl >/dev/null 2>&1; then
        echo "   Testing with curl..."
        if curl -s --connect-timeout 5 telnet://103.80.162.46:9092 >/dev/null 2>&1; then
            echo "   ✅ Curl connection successful"
        else
            echo "   ❌ Curl connection failed"
        fi
    fi

    # Test with Java if available
    if command -v java >/dev/null 2>&1; then
        echo "   Java version: $(java -version 2>&1 | head -n 1)"
        echo "   If Java is available, H2 client should work"
    else
        echo "   ⚠️  Java not found - this could be an issue for H2 connectivity"
    fi
}

# Function to provide network troubleshooting steps
provide_solutions() {
    echo ""
    echo "=== TROUBLESHOOTING STEPS ==="
    echo ""
    echo "If port 9092 is not accessible from Pi, try these solutions:"
    echo ""
    echo "1. CHECK RASPBERRY PI NETWORK:"
    echo "   - Ensure Pi has internet connectivity"
    echo "   - Try: ping google.com"
    echo "   - Check if Pi is on same network or has route to 103.80.162.46"
    echo ""
    echo "2. CHECK H2 SERVER (on 103.80.162.46):"
    echo "   - SSH to server: ssh user@103.80.162.46"
    echo "   - Check if H2 server is running: ps aux | grep h2"
    echo "   - Restart H2 server with proper settings:"
    echo "     java -cp h2-*.jar org.h2.tools.Server \\"
    echo "       -tcp -tcpAllowOthers -tcpPort 9092 \\"
    echo "       -baseDir /opt/h2-data -ifNotExists"
    echo ""
    echo "3. CHECK SERVER FIREWALL:"
    echo "   - Allow port: sudo ufw allow 9092"
    echo "   - Or disable firewall temporarily: sudo ufw disable"
    echo ""
    echo "4. CHECK ROUTER/NETWORK:"
    echo "   - Port 9092 might be blocked by router/ISP"
    echo "   - Try different port (e.g., 9093) if possible"
    echo ""
    echo "5. ALTERNATIVE: USE SSH TUNNEL"
    echo "   - From Pi: ssh -L 9092:localhost:9092 user@103.80.162.46"
    echo "   - Then connect to localhost:9092 instead"
    echo ""
}

# Run all tests
test_connectivity
check_local_firewall
test_dns
test_connection_methods
provide_solutions

echo ""
echo "=== END OF DIAGNOSTICS ==="
echo "Run this script on your Raspberry Pi to diagnose the connection issue."
