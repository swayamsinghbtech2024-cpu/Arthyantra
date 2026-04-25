#!/bin/bash
# ══════════════════════════════════════════════════════════════
# Run Test Suite
# Usage: ./test.sh
# ══════════════════════════════════════════════════════════════

cd "$(dirname "$0")/backend" || exit 1

echo "Running Forex Portfolio Test Suite..."
echo ""

java -cp "mysql-connector-j-9.7.0.jar:." TestRunner
