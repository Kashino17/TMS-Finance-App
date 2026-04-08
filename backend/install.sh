#!/usr/bin/env bash
# TMS Banking Backend — One-command installer
# Usage: ./install.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"

echo "🏦 TMS Banking Backend — Installation"
echo "======================================="
echo

# Check Python 3.12+
if ! command -v python3 &>/dev/null; then
    echo "❌ Python 3 not found. Install Python 3.12+ first."
    exit 1
fi

PY_VERSION=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
echo "✓ Python $PY_VERSION found"

# Create venv if needed
if [ ! -d "$VENV_DIR" ]; then
    echo "→ Creating virtual environment..."
    python3 -m venv "$VENV_DIR"
fi

# Install package
echo "→ Installing dependencies..."
"$VENV_DIR/bin/pip" install -e "$SCRIPT_DIR" -q

# Create symlink in ~/.local/bin (no sudo needed)
LOCAL_BIN="$HOME/.local/bin"
mkdir -p "$LOCAL_BIN"
SYMLINK_TARGET="$LOCAL_BIN/tms-bank"
ACTUAL_BIN="$VENV_DIR/bin/tms-bank"

if [ -L "$SYMLINK_TARGET" ] || [ -f "$SYMLINK_TARGET" ]; then
    echo "→ Updating existing tms-bank command..."
    rm -f "$SYMLINK_TARGET"
fi

echo "→ Installing tms-bank command to ~/.local/bin..."
ln -s "$ACTUAL_BIN" "$SYMLINK_TARGET"

# Ensure ~/.local/bin is in PATH
if ! echo "$PATH" | grep -q "$LOCAL_BIN"; then
    SHELL_RC=""
    if [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    elif [ -f "$HOME/.bashrc" ]; then
        SHELL_RC="$HOME/.bashrc"
    fi
    if [ -n "$SHELL_RC" ]; then
        if ! grep -q '.local/bin' "$SHELL_RC" 2>/dev/null; then
            echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$SHELL_RC"
            echo "→ Added ~/.local/bin to PATH in $SHELL_RC"
        fi
    fi
    export PATH="$LOCAL_BIN:$PATH"
fi

# Copy .env template if no .env exists
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
    echo "→ Created .env from template — edit it to add your bank credentials"
fi

echo
echo "✅ Installation complete!"
echo
echo "Usage:"
echo "  tms-bank              Start the server (default: 0.0.0.0:8000)"
echo "  tms-bank -p 9000      Start on a different port"
echo "  tms-bank --reload     Start with auto-reload (development)"
echo
echo "Config: $SCRIPT_DIR/.env"
echo
