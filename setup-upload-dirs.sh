#!/bin/bash

# Script to setup upload directories with proper permissions for Docker container
# Run this on your Raspberry Pi before starting the containers

echo "Setting up upload directories..."

# Remove existing profile-pics directory if it has wrong ownership
if [ -d "/mnt/shared-images/profile-pics" ]; then
    echo "Removing existing profile-pics directory with incorrect ownership..."
    sudo rm -rf /mnt/shared-images/profile-pics
fi

# Create the upload directory if it doesn't exist
sudo mkdir -p /mnt/shared-images/profile-pics

# Set ownership to a user ID that matches the container (1000:1000)
# First set ownership on the parent directory
sudo chown 1000:1000 /mnt/shared-images

# Then set ownership on the profile-pics subdirectory
sudo chown 1000:1000 /mnt/shared-images/profile-pics

# Set permissions to allow read/write for owner and group
sudo chmod 755 /mnt/shared-images

# Make profile-pics subdirectory writable
sudo chmod 777 /mnt/shared-images/profile-pics

echo "Upload directories setup completed!"
echo "Directory: /mnt/shared-images"
echo "Subdirectory: /mnt/shared-images/profile-pics"
echo "Permissions set for container user (1000:1000)"

# List the directory to verify
ls -la /mnt/shared-images/
