# BOSS Distribution Guide

## macOS Distribution

The BOSS application is distributed as a DMG file for macOS with the following features:

- **Application Name**: BOSS
- **Version**: 1.0.0
- **Icon**: Custom Risa Labs icon (risa_icon.icns)
- **Bundle ID**: ai.rever.boss
- **Vendor**: Risa Labs Inc.
- **Copyright**: © 2024 Risa Labs Inc. All rights reserved.

### Building the DMG

To build a new DMG distribution:

```bash
./build-dmg.sh
```

This script will:
1. Clean previous builds
2. Build the application
3. Create the DMG package
4. Copy it to ~/Downloads/

### DMG Contents

The DMG contains:
- BOSS.app - The main application bundle
- Application icon with Risa Labs branding
- All required dependencies bundled

### System Requirements

- macOS 10.15 (Catalina) or later
- 64-bit Intel or Apple Silicon processor
- 4GB RAM minimum (8GB recommended)
- 500MB free disk space

### Installation

1. Download BOSS-1.0.0.dmg
2. Double-click to mount the DMG
3. Drag BOSS.app to Applications folder
4. Eject the DMG
5. Launch BOSS from Applications

### First Launch

On first launch, macOS may show a security warning. To open:
1. Right-click on BOSS.app
2. Select "Open" from the context menu
3. Click "Open" in the security dialog

### Features Included

- Terminal integration with zsh/bash support
- Browser automation (Fluck)
- Plugin system for extensibility
- Dark theme UI
- Multi-tab interface
- Keyboard shortcuts (Cmd+N, Cmd+T, Cmd+W)

### Troubleshooting

If the app doesn't launch:
1. Check Console.app for error messages
2. Ensure you have the required macOS version
3. Try running from Terminal: `/Applications/BOSS.app/Contents/MacOS/BOSS`

### Building from Source

Requirements:
- JDK 17 or later
- Gradle 8.x
- Kotlin 1.9.x

Build command:
```bash
./gradlew :composeApp:packageDmg
```

The DMG will be created in:
`composeApp/build/compose/binaries/main/dmg/`