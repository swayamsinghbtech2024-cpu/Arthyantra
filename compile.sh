#!/bin/bash
# ══════════════════════════════════════════════════════════════
# Compile all Java files
# Usage: ./compile.sh
# ══════════════════════════════════════════════════════════════

echo "╔══════════════════════════════════════════════════╗"
echo "║   Compiling Forex Portfolio System...            ║"
echo "╚══════════════════════════════════════════════════╝"

cd "$(dirname "$0")/backend" || exit 1

# Clean old class files
rm -f *.class

# Compile all Java files with MySQL connector
javac -cp "mysql-connector-j-9.7.0.jar:." *.java

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Compilation successful!"
    echo ""
    echo "To run the server:  ./run.sh"
    echo "To run tests:       ./test.sh"
else
    echo ""
    echo "❌ Compilation failed. Check errors above."
    exit 1
fi
