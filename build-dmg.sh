#!/bin/bash

echo "Building BOSS DMG distribution for macOS..."

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build the application
echo "Building application..."
./gradlew :composeApp:build

# Create DMG package
echo "Creating DMG package..."
./gradlew :composeApp:createDistributable
./gradlew :composeApp:packageDmg

# Find the created DMG
DMG_PATH=$(find composeApp/build/compose/binaries/main/dmg -name "*.dmg" 2>/dev/null | head -1)

if [ -n "$DMG_PATH" ]; then
    echo "✅ DMG created successfully: $DMG_PATH"
    echo "📦 Size: $(du -h "$DMG_PATH" | cut -f1)"
    
    # Copy to Downloads for easy access
    cp "$DMG_PATH" ~/Downloads/
    echo "📂 Copied to: ~/Downloads/$(basename "$DMG_PATH")"
else
    echo "❌ DMG creation failed. Check the build logs above."
    exit 1
fi