#!/bin/bash

# H2 Database Auto-Startup Setup Script
echo "Setting up H2 Database for automatic startup..."

# Create necessary directories
echo "Creating H2 data directory..."
mkdir -p /Users/ashish/h2-data
mkdir -p /usr/local/lib/h2

# Download H2 if it doesn't exist
if [ ! -f "/usr/local/lib/h2/h2-2.2.224.jar" ]; then
    echo "Downloading H2 Database..."
    cd /usr/local/lib/h2
    curl -L -o h2-2.2.224.jar "https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar"
    echo "H2 downloaded successfully!"
else
    echo "H2 already exists at /usr/local/lib/h2/"
fi

# Update the launch agent with the correct jar path
echo "Updating launch agent configuration..."
sed -i '' 's|/usr/local/lib/h2/h2\*.jar|/usr/local/lib/h2/h2-2.2.224.jar|g' /Users/ashish/Library/LaunchAgents/com.h2database.server.plist

# Load the launch agent
echo "Loading H2 launch agent..."
launchctl load /Users/ashish/Library/LaunchAgents/com.h2database.server.plist

# Start the service
echo "Starting H2 service..."
launchctl start com.h2database.server

echo ""
echo "✅ H2 Database setup complete!"
echo ""
echo "H2 Server will now start automatically when you log in."
echo "- Database TCP port: 9092"
echo "- Web console: http://localhost:8082"
echo "- Data directory: /Users/ashish/h2-data"
echo "- Logs: /Users/ashish/h2-data/h2-server.log"
echo ""
echo "To manage the service:"
echo "  Stop:    launchctl stop com.h2database.server"
echo "  Start:   launchctl start com.h2database.server"
echo "  Restart: launchctl kickstart -k gui/$(id -u)/com.h2database.server"
echo "  Unload:  launchctl unload /Users/ashish/Library/LaunchAgents/com.h2database.server.plist"
