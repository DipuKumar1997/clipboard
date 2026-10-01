#!/bin/bash
set -e

APP_NAME="airmesh"
INSTALL_DIR="$HOME/.local/share/$APP_NAME"
BIN_DIR="$HOME/.local/bin"
SYSTEMD_DIR="$HOME/.config/systemd/user"
SERVICE_FILE="$SYSTEMD_DIR/airmesh.service"

echo "==> [Linux] Compiling AirMesh project via Maven..."
mvn clean package -DskipTests

echo "==> Creating system installation directories..."
mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$SYSTEMD_DIR"

JAR_PATH=$(find target -name "*.jar" ! -name "*javadoc*" ! -name "*sources*" | head -n 1)
if [ -z "$JAR_PATH" ]; then
    echo "Error: Output JAR file not found in target/"
    exit 1
fi

cp "$JAR_PATH" "$INSTALL_DIR/airmesh.jar"

echo "==> Generating binary launcher script at $BIN_DIR/airmesh..."
cat << 'EOF' > "$BIN_DIR/airmesh"
#!/bin/bash
JAVA_BIN=$(which java)
if [ -z "$JAVA_BIN" ]; then
    echo "Error: Java JRE not found in system PATH."
    exit 1
fi
exec "$JAVA_BIN" -jar "$HOME/.local/share/airmesh/airmesh.jar" "$@"
EOF
chmod +x "$BIN_DIR/airmesh"

echo "==> Registering systemd user service..."
JAVA_PATH=$(which java)

cat << EOF > "$SERVICE_FILE"
[Unit]
Description=AirMesh Peer-to-Peer Clipboard & File Sync Service
After=network.target graphical-session.target

[Service]
Type=simple
ExecStart=${JAVA_PATH} -jar ${INSTALL_DIR}/airmesh.jar --minimized
Restart=on-failure
RestartSec=3s
Environment=DISPLAY=:0
Environment=XDG_RUNTIME_DIR=/run/user/%U

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable airmesh.service
systemctl --user restart airmesh.service

echo "==> [Linux] AirMesh installation finished successfully."