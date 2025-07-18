#!/bin/bash

# Script to fix directory permissions before starting Docker containers

echo "Setting up directories and permissions for Docker containers..."

# Create directories if they don't exist
mkdir -p ./logs
mkdir -p ./uploads
mkdir -p ./uploads/profile-pics

# Set proper permissions for the directories
echo "Setting directory permissions..."
chmod 755 ./logs
chmod 755 ./uploads
chmod 755 ./uploads/profile-pics

# For macOS/Linux compatibility, try to set ownership to current user
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    chown -R $(whoami):staff ./logs ./uploads 2>/dev/null || true
else
    # Linux
    chown -R $(whoami):$(whoami) ./logs ./uploads 2>/dev/null || true
fi

echo "Directory setup completed:"
echo "  ./logs - $(ls -ld ./logs)"
echo "  ./uploads - $(ls -ld ./uploads)"
echo "  ./uploads/profile-pics - $(ls -ld ./uploads/profile-pics)"

echo ""
echo "You can now run: docker-compose up --build"
