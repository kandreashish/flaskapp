#!/bin/bash

echo "Setting up upload directories..."

# Remove old directory if it exists
if [ -d "/usb1_1/uploads/profile-pics" ]; then
    echo "Removing existing directory..."
    sudo rm -rf /usb1_1/uploads/profile-pics
fi

# Create the new directory structure
sudo mkdir -p /usb1_1/uploads/profile-pics

# Set ownership to container user (1000:1000)
sudo chown 1000:1000 /usb1_1/uploads

# Set ownership for subdirectory
sudo chown 1000:1000 /usb1_1/uploads/profile-pics

# Set permissions for base directory
sudo chmod 755 /usb1_1/uploads

# Set permissions for profile-pics subdirectory (more permissive for uploads)
sudo chmod 777 /usb1_1/uploads/profile-pics

echo "Directory: /usb1_1/uploads"
echo "Subdirectory: /usb1_1/uploads/profile-pics"
echo "Permissions set for container user (1000:1000)"

# List the directory structure
ls -la /usb1_1/
