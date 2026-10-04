#!/bin/bash
set -e

APP_NAME="airmesh"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
BIN_DIR="$HOME/.local/bin"
LAUNCH_AGENTS_DIR="$HOME/Library/LaunchAgents"
PLIST_FILE="$LAUNCH_AGENTS_DIR/com.wifisync.app.plist"

echo "==> [macOS] Compiling AirMesh project via Maven..."
mvn clean package -DskipTests

echo "==> Creating macOS installation directories..."
mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$LAUNCH_AGENTS_DIR"

JAR_PATH=$(find target -name "*.jar" ! -name "*javadoc*" ! -name "*sources*" | head -n 1)
if [ -z "$JAR_PATH" ]; then
    echo "Error: Output JAR file not found in target/"
    exit 1
fi

cp "$JAR_PATH" "$INSTALL_DIR/airmesh.jar"

echo "==> Generating wrapper script at $BIN_DIR/airmesh..."
cat << 'EOF' > "$BIN_DIR/airmesh"
#!/bin/bash
JAVA_BIN=$(which java)
if [ -z "$JAVA_BIN" ]; then
    echo "Error: Java JRE not found in system PATH."
    exit 1
fi
exec "$JAVA_BIN" -Dapple.awt.UIElement=true -jar "$HOME/.local/share/airmesh/airmesh.jar" "$@"
EOF
chmod +x "$BIN_DIR/airmesh"

echo "==> Registering launchd LaunchAgent..."
JAVA_PATH=$(which java)

cat << EOF > "$PLIST_FILE"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.wifisync.app</string>
    <key>ProgramArguments</key>
    <array>
        <string>${JAVA_PATH}</string>
        <string>-Dapple.awt.UIElement=true</string>
        <string>-jar</string>
        <string>${INSTALL_DIR}/airmesh.jar</string>
        <string>--minimized</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <!-- Without this, launchd may start AirMesh outside a normal GUI (Aqua)
         session, which can silently block access to the pasteboard/window
         server -- this is the most common cause of "clipboard sync just
         doesn't do anything" on macOS when launched at login. -->
    <key>LimitLoadToSessionType</key>
    <string>Aqua</string>
    <key>ProcessType</key>
    <string>Interactive</string>
    <key>StandardOutPath</key>
    <string>/tmp/airmesh.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/airmesh.err</string>
</dict>
</plist>
EOF

echo "==> Loading launchd background service..."
launchctl unload "$PLIST_FILE" 2>/dev/null || true
launchctl load "$PLIST_FILE"

echo "==> [macOS] AirMesh installation finished successfully."
