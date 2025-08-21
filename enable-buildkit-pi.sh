#!/bin/bash
# Script to enable Docker Buildkit on Raspberry Pi

echo "Enabling Docker Buildkit on Raspberry Pi..."

# Method 1: Set environment variable for current session
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Method 2: Add to docker daemon configuration
sudo mkdir -p /etc/docker
echo '{
    "features": {
        "buildkit": true
    }
}' | sudo tee /etc/docker/daemon.json

# Method 3: Add to user profile for permanent setting
echo "# Docker Buildkit environment variables" >> ~/.bashrc
echo "export DOCKER_BUILDKIT=1" >> ~/.bashrc
echo "export COMPOSE_DOCKER_CLI_BUILD=1" >> ~/.bashrc

# Restart Docker service
echo "Restarting Docker service..."
sudo systemctl restart docker

# Wait for Docker to start
sleep 5

echo "Docker Buildkit enabled successfully!"
echo "Please run 'source ~/.bashrc' or restart your terminal session."

# Verify buildkit is enabled
echo "Verifying buildkit status..."
docker buildx version
