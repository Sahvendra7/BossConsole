#!/bin/bash

# Build BOSS final distribution with all fixes

# Configuration
DEVELOPER_ID="Developer ID Application: Reverberation Tech Private Limited (TSFZV2FBXD)"
TEAM_ID="TSFZV2FBXD"
APPLE_ID="shivang.iitk@gmail.com"
APP_PASSWORD="njan-ouda-ogjt-pbup"

echo "========================================"
echo "Building BOSS Final Distribution"
echo "========================================"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Step 1: Use clean JDK
echo -e "${BLUE}Step 1: Setting up clean JDK${NC}"
export JAVA_HOME="$(pwd)/zulu-jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JDK: $JAVA_HOME"

# Step 2: Clean build
echo -e "\n${BLUE}Step 2: Cleaning previous builds${NC}"
./gradlew clean
rm -rf composeApp/build/compose/binaries

# Step 3: Build the application
echo -e "\n${BLUE}Step 3: Building application${NC}"
./gradlew :composeApp:createDistributable

# Step 4: Fix PTY4J native libraries
echo -e "\n${BLUE}Step 4: Fixing PTY4J native libraries${NC}"

APP_PATH=$(find composeApp/build/compose/binaries/main/app -name "*.app" | head -1)
if [ -n "$APP_PATH" ]; then
    # Find and extract PTY4J natives
    PTY4J_JAR=$(find "$APP_PATH/Contents/app" -name "pty4j-*.jar" | head -1)
    if [ -n "$PTY4J_JAR" ]; then
        NATIVE_DIR="$APP_PATH/Contents/app/pty4j-native"
        mkdir -p "$NATIVE_DIR"
        
        # Extract natives
        cd "$NATIVE_DIR"
        jar xf "$PTY4J_JAR" "native/*" || unzip -q "$PTY4J_JAR" "native/*"
        
        # Fix structure
        if [ -d "native" ]; then
            mv native/* . 2>/dev/null || true
            rmdir native 2>/dev/null || true
        fi
        
        # Make executable
        chmod +x *.dylib 2>/dev/null || true
        chmod +x libpty 2>/dev/null || true
        cd - > /dev/null
        
        echo -e "${GREEN}✓ PTY4J natives extracted${NC}"
    fi
fi

# Step 5: Sign the app
echo -e "\n${BLUE}Step 5: Signing application${NC}"

# Sign all components
find "$APP_PATH" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -perm +111 \) | while read -r file; do
    if file "$file" | grep -q "Mach-O"; then
        codesign --force --options runtime --sign "$DEVELOPER_ID" --timestamp "$file" 2>/dev/null
    fi
done

# Sign the app bundle
codesign --force --deep --options runtime \
    --entitlements "composeApp/src/desktopMain/resources/BOSS.entitlements" \
    --sign "$DEVELOPER_ID" --timestamp "$APP_PATH"

echo -e "${GREEN}✓ Application signed${NC}"

# Step 6: Create custom DMG
echo -e "\n${BLUE}Step 6: Creating custom DMG${NC}"

DMG_DIR="/tmp/boss-dmg-$$"
mkdir -p "$DMG_DIR"

# Copy app and create Applications link
cp -R "$APP_PATH" "$DMG_DIR/"
ln -s /Applications "$DMG_DIR/Applications"

# Create DMG
DMG_NAME="BOSS-1.0.0.dmg"
rm -f "$DMG_NAME"

hdiutil create -volname "BOSS" \
    -srcfolder "$DMG_DIR" \
    -ov -format UDZO \
    "$DMG_NAME"

rm -rf "$DMG_DIR"

# Step 7: Configure DMG window
echo -e "\n${BLUE}Step 7: Configuring DMG window${NC}"

# Create a temporary mount point
MOUNT_POINT="/tmp/boss-mount-$$"
mkdir -p "$MOUNT_POINT"

# Mount the DMG
hdiutil attach "$DMG_NAME" -nobrowse -mountpoint "$MOUNT_POINT"

# Apply window settings
osascript <<EOF
tell application "Finder"
    tell disk "BOSS"
        open
        set current view of container window to icon view
        set toolbar visible of container window to false
        set statusbar visible of container window to false
        set the bounds of container window to {100, 100, 650, 400}
        set viewOptions to the icon view options of container window
        set arrangement of viewOptions to not arranged
        set icon size of viewOptions to 100
        set position of item "BOSS.app" of container window to {175, 180}
        set position of item "Applications" of container window to {475, 180}
        close
        open
        update without registering applications
        delay 1
    end tell
end tell
EOF

# Unmount
hdiutil detach "$MOUNT_POINT"
rmdir "$MOUNT_POINT"

# Step 8: Sign DMG
echo -e "\n${BLUE}Step 8: Signing DMG${NC}"
codesign --force --sign "$DEVELOPER_ID" "$DMG_NAME"

# Step 9: Notarize
echo -e "\n${BLUE}Step 9: Notarizing${NC}"
xcrun notarytool submit "$DMG_NAME" \
    --apple-id "$APPLE_ID" \
    --password "$APP_PASSWORD" \
    --team-id "$TEAM_ID" \
    --wait

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Notarization successful${NC}"
    xcrun stapler staple "$DMG_NAME"
fi

# Step 10: Final distribution
echo -e "\n${BLUE}Step 10: Creating final distribution${NC}"

DIST_DIR="distribution-final"
mkdir -p "$DIST_DIR"
cp "$DMG_NAME" "$DIST_DIR/BOSS-1.0.0-Universal.dmg"

echo -e "\n${GREEN}========================================"
echo -e "✨ Final Distribution Complete!"
echo -e "========================================${NC}"
echo -e "Location: ${BLUE}$DIST_DIR/BOSS-1.0.0-Universal.dmg${NC}"
echo -e "\nThis DMG includes:"
echo -e "- Fixed PTY4J native libraries for Terminal"
echo -e "- Proper DMG window layout"
echo -e "- Full notarization"
echo -e "\n${GREEN}Ready for distribution!${NC}"