#!/usr/bin/env bash
#
# End-to-end verification of the Kotlin <-> Lightweight Charts bridge.
#
#   1. runs the JVM unit tests, which regenerate the bridge fixture
#      (`app/build/chart-bridge-fixture/payload.json`) from real `ChartCommands`
#      output over 1000 synthetic candles;
#   2. replays that exact fixture against the real TradingView Lightweight
#      Charts v5.2.1 bundle inside jsdom + node-canvas.
#
# Requirements:
#   JDK 17 + Android SDK (same as any other Gradle task)
#   node            (v18+)
#   jsdom + canvas  ->  npm install --prefix /tmp/opencode/jstest jsdom canvas
#                       and point JSDOM_PATH / CANVAS_PATH at the results.
#
# Usage:
#   tools/verify-chart-bridge.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/usr/local/sdkman/candidates/java/17.0.20-tem}"
export ANDROID_HOME="${ANDROID_HOME:-/tmp/opencode/sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

NODE_MODULES="${NODE_MODULES:-/tmp/opencode/jstest/node_modules}"
export JSDOM_PATH="${JSDOM_PATH:-$NODE_MODULES/jsdom}"
export CANVAS_PATH="${CANVAS_PATH:-$NODE_MODULES/canvas}"

echo "==> regenerating the bridge fixture (./gradlew testDebugUnitTest)"
./gradlew --console=plain -q testDebugUnitTest

FIXTURE="app/build/chart-bridge-fixture/payload.json"
if [[ ! -f "$FIXTURE" ]]; then
  echo "fixture was not produced: $FIXTURE" >&2
  exit 1
fi
echo "    fixture: $FIXTURE ($(wc -c < "$FIXTURE") bytes)"

echo "==> replaying the fixture against lightweight-charts 5.2.1"
node tools/chart-bridge-harness.js

echo "==> chart bridge verified"
