#!/bin/bash

echo "Fixing permissions for upload directories..."

# Create local directories if they don't exist
mkdir -p ./uploads
mkdir -p ./uploads/profile-pics

# Set permissions for local directories
chmod 755 ./uploads
chmod 755 ./uploads/profile-pics

# Also ensure logs directory exists and has correct permissions
mkdir -p ./logs

# Fix ownership if running on macOS/Linux
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    chown -R $(whoami):staff ./logs ./uploads 2>/dev/null || true
else
    # Linux
    chown -R $(whoami):$(whoami) ./logs ./uploads 2>/dev/null || true
fi

echo "Local permissions fixed!"
echo "Note: For production on Raspberry Pi, make sure /usb1_1/uploads/profile-pics exists and has proper permissions"
echo "Run setup-upload-dirs.sh on the Pi to create the production directories"
