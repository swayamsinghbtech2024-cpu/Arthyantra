#!/bin/bash
# ══════════════════════════════════════════════════════════════
# Run the Forex Portfolio Server
# Usage: ./run.sh
# ══════════════════════════════════════════════════════════════

cd "$(dirname "$0")/backend" || exit 1

echo "Starting Forex Portfolio Server..."
echo ""

java -cp "mysql-connector-j-9.7.0.jar:." MainServer
