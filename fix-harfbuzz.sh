#!/bin/bash

# Fix for BOSS harfbuzz dependency issue

echo "BOSS Harfbuzz Dependency Fix"
echo "============================"

# Option 1: Install harfbuzz via Homebrew (easiest)
if command -v brew &> /dev/null; then
    echo "Installing harfbuzz via Homebrew..."
    brew install harfbuzz
    echo "✓ Harfbuzz installed!"
else
    echo "Homebrew not found. Installing Homebrew first..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    # Add Homebrew to PATH for Apple Silicon Macs
    if [[ $(uname -m) == "arm64" ]]; then
        echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
    
    brew install harfbuzz
    echo "✓ Homebrew and harfbuzz installed!"
fi

echo ""
echo "Fix applied! You can now run BOSS."
echo "Try: open /Applications/BOSS.app"