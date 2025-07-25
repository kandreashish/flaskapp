#!/bin/bash

# Script to setup upload directories with proper permissions for Docker container
# Run this on your Raspberry Pi before starting the containers

echo "Setting up upload directories..."

# Create the upload directory if it doesn't exist
sudo mkdir -p /mnt/shared-images/profile-pics

# Set ownership to a user ID that matches the container (1000:1000)
sudo chown -R 1000:1000 /mnt/shared-images

# Set permissions to allow read/write for owner and group
sudo chmod -R 755 /mnt/shared-images

# Make profile-pics subdirectory writable
sudo chmod -R 777 /mnt/shared-images/profile-pics

echo "Upload directories setup completed!"
echo "Directory: /mnt/shared-images"
echo "Subdirectory: /mnt/shared-images/profile-pics"
echo "Permissions set for container user (1000:1000)"

# List the directory to verify
ls -la /mnt/shared-images/
