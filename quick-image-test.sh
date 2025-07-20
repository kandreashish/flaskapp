#!/bin/bash

echo "🧪 Quick Image Access Test"
echo "=========================="

# Test if we can upload and access an image
echo "1. Creating a test image..."
convert -size 100x100 xc:red test-image.jpg 2>/dev/null || echo -e '\x89PNG\x0D\x0A\x1A\x0A\x00\x00\x00\x0DIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xDE' > test-image.png

echo "2. Testing upload directory access..."
docker exec expense-tracker-app-1 ls -la /app/uploads/profile-pics/ 2>/dev/null || echo "Cannot access profile-pics directory"

echo "3. Copying test image to uploads..."
if [ -f "test-image.jpg" ]; then
    docker cp test-image.jpg expense-tracker-app-1:/app/uploads/profile-pics/test-image.jpg
    TEST_FILE="test-image.jpg"
elif [ -f "test-image.png" ]; then
    docker cp test-image.png expense-tracker-app-1:/app/uploads/profile-pics/test-image.png
    TEST_FILE="test-image.png"
else
    echo "Creating simple test file..."
    echo "test image data" | docker exec -i expense-tracker-app-1 sh -c 'cat > /app/uploads/profile-pics/test-image.txt'
    TEST_FILE="test-image.txt"
fi

echo "4. Verifying file exists in container..."
docker exec expense-tracker-app-1 ls -la /app/uploads/profile-pics/$TEST_FILE

echo "5. Testing nginx access to file..."
docker exec expense-tracker-nginx ls -la /app/uploads/profile-pics/$TEST_FILE 2>/dev/null || echo "Nginx cannot see the file"

echo "6. Testing HTTP access..."
curl -I "http://localhost/api/files/profile-pics/$TEST_FILE" 2>/dev/null || echo "Cannot access via HTTP"

echo "7. Testing direct backend access..."
curl -I "http://localhost:3001/api/files/profile-pics/$TEST_FILE" 2>/dev/null || echo "Cannot access backend directly"

echo "8. Checking file permissions..."
docker exec expense-tracker-app-1 stat /app/uploads/profile-pics/$TEST_FILE 2>/dev/null || echo "Cannot stat file"

echo ""
echo "Test completed!"
