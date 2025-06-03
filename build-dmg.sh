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

# Step 1: Use system JDK
echo -e "${BLUE}Step 1: Setting up JDK${NC}"
# Use the system's OpenJDK 17 from Homebrew
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [ ! -d "$JAVA_HOME" ]; then
    echo -e "${NC}Error: OpenJDK 17 not found at $JAVA_HOME"
    echo "Please install OpenJDK 17 with: brew install openjdk@17"
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JDK: $JAVA_HOME"
java -version

# Step 2: Clean build
echo -e "\n${BLUE}Step 2: Cleaning previous builds${NC}"
./gradlew clean
rm -rf composeApp/build/compose/binaries

# Step 3: Build the application
echo -e "\n${BLUE}Step 3: Building application${NC}"
./gradlew :composeApp:createDistributable

if [ $? -ne 0 ]; then
    echo -e "${NC}Error: Gradle build failed"
    exit 1
fi

# Step 4: Fix PTY4J native libraries
echo -e "\n${BLUE}Step 4: Fixing PTY4J native libraries${NC}"

APP_PATH=$(find composeApp/build/compose/binaries/main/app -name "*.app" | head -1)
if [ -z "$APP_PATH" ]; then
    echo -e "${NC}Error: Could not find built .app file"
    echo "Expected location: composeApp/build/compose/binaries/main/app/*.app"
    exit 1
fi

echo "Found app at: $APP_PATH"

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

# Step 5: Bundle Homebrew dependencies
echo -e "\n${BLUE}Step 5: Bundling Homebrew dependencies${NC}"

if [ -n "$APP_PATH" ]; then
    # Create libs directory in the JVM runtime
    RUNTIME_LIB_DIR="$APP_PATH/Contents/runtime/Contents/Home/lib"
    
    # List of Homebrew libraries that might be needed
    BREW_LIBS=(
        "harfbuzz|libharfbuzz.0.dylib"
        "freetype|libfreetype.6.dylib"
        "glib|libglib-2.0.0.dylib"
        "graphite2|libgraphite2.3.dylib"
        "pcre2|libpcre2-8.0.dylib"
        "gettext|libintl.8.dylib"
        "brotli|libbrotlidec.1.dylib"
        "brotli|libbrotlicommon.1.dylib"
        "libpng|libpng16.16.dylib"
        "bzip2|libbz2.1.0.dylib"
    )
    
    # Copy each library if it exists
    for lib_spec in "${BREW_LIBS[@]}"; do
        pkg_name="${lib_spec%|*}"
        lib_name="${lib_spec#*|}"
        
        # Try both common Homebrew locations
        for brew_prefix in "/opt/homebrew" "/usr/local"; do
            lib_path="$brew_prefix/opt/$pkg_name/lib/$lib_name"
            if [ -f "$lib_path" ]; then
                echo "  Copying $lib_name..."
                cp "$lib_path" "$RUNTIME_LIB_DIR/" 2>/dev/null || true
                
                # Fix the library ID and dependencies
                chmod +w "$RUNTIME_LIB_DIR/$lib_name" 2>/dev/null || true
                
                # Update the library's own ID
                install_name_tool -id "@loader_path/$lib_name" "$RUNTIME_LIB_DIR/$lib_name" 2>/dev/null || true
                
                # Update dependencies to use @loader_path
                otool -L "$RUNTIME_LIB_DIR/$lib_name" | grep -E "(opt/homebrew|usr/local)" | awk '{print $1}' | while read dep; do
                    dep_name=$(basename "$dep")
                    install_name_tool -change "$dep" "@loader_path/$dep_name" "$RUNTIME_LIB_DIR/$lib_name" 2>/dev/null || true
                done
                
                break
            fi
        done
    done
    
    # Fix libfontmanager.dylib to use bundled libraries
    if [ -f "$RUNTIME_LIB_DIR/libfontmanager.dylib" ]; then
        echo "  Fixing libfontmanager.dylib dependencies..."
        
        # Update harfbuzz dependency
        install_name_tool -change "/opt/homebrew/opt/harfbuzz/lib/libharfbuzz.0.dylib" \
            "@loader_path/libharfbuzz.0.dylib" \
            "$RUNTIME_LIB_DIR/libfontmanager.dylib" 2>/dev/null || true
            
        # Update freetype dependency if it exists
        install_name_tool -change "/opt/homebrew/opt/freetype/lib/libfreetype.6.dylib" \
            "@loader_path/libfreetype.6.dylib" \
            "$RUNTIME_LIB_DIR/libfontmanager.dylib" 2>/dev/null || true
    fi
    
    echo -e "${GREEN}✓ Dependencies bundled${NC}"
fi

# Step 6: Sign the app
echo -e "\n${BLUE}Step 6: Signing application${NC}"

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

# Step 7: Create custom DMG
echo -e "\n${BLUE}Step 7: Creating custom DMG${NC}"

DMG_DIR="/tmp/boss-dmg-$$"
mkdir -p "$DMG_DIR"

# Copy app and create Applications link
cp -R "$APP_PATH" "$DMG_DIR/"
ln -s /Applications "$DMG_DIR/Applications"

# Create DMG
DMG_NAME="BOSS-1.0.4.dmg"
rm -f "$DMG_NAME"

hdiutil create -volname "BOSS" \
    -srcfolder "$DMG_DIR" \
    -ov -format UDZO \
    "$DMG_NAME"

rm -rf "$DMG_DIR"

# Step 8: Configure DMG window
echo -e "\n${BLUE}Step 8: Configuring DMG window${NC}"

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

# Step 9: Sign DMG
echo -e "\n${BLUE}Step 9: Signing DMG${NC}"
codesign --force --sign "$DEVELOPER_ID" "$DMG_NAME"

# Step 10: Notarize
echo -e "\n${BLUE}Step 10: Notarizing${NC}"
xcrun notarytool submit "$DMG_NAME" \
    --apple-id "$APPLE_ID" \
    --password "$APP_PASSWORD" \
    --team-id "$TEAM_ID" \
    --wait

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Notarization successful${NC}"
    xcrun stapler staple "$DMG_NAME"
fi

# Step 11: Final distribution
echo -e "\n${BLUE}Step 11: Creating final distribution${NC}"

DIST_DIR="distribution-final"
mkdir -p "$DIST_DIR"
cp "$DMG_NAME" "$DIST_DIR/BOSS-1.0.4-Universal.dmg"

echo -e "\n${GREEN}========================================"
echo -e "✨ Final Distribution Complete!"
echo -e "========================================${NC}"
echo -e "Location: ${BLUE}$DIST_DIR/BOSS-1.0.4-Universal.dmg${NC}"
echo -e "\nThis DMG includes:"
echo -e "- Fixed PTY4J native libraries for Terminal"
echo -e "- Bundled Homebrew dependencies (harfbuzz, freetype, etc.)"
echo -e "- Proper DMG window layout"
echo -e "- Full notarization"
echo -e "\n${GREEN}Ready for distribution!${NC}"