#!/usr/bin/env bash
# Installs ShieldLock on an NVIDIA Shield / Android TV device and turns the service on.
# On the device first: Developer options > Network debugging (or USB debugging) enabled.
#
#   ./scripts/install.sh 192.168.1.42
#   ./scripts/install.sh 192.168.1.42 ./ShieldLock-1.0.0.apk
#
set -euo pipefail
IP="${1:?usage: install.sh <device-ip> [apk] [port]}"
APK="${2:-}"
PORT="${3:-5555}"
PKG=app.shieldlock
SVC="$PKG/$PKG.LockService"
DEV="$IP:$PORT"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

command -v adb >/dev/null || { echo "adb not found: install Android platform-tools." >&2; exit 1; }
if [ -z "$APK" ]; then
  APK="$(ls -t "$ROOT"/build/ShieldLock-*.apk "$ROOT"/ShieldLock-*.apk 2>/dev/null | head -n 1 || true)"
fi
[ -n "$APK" ] && [ -f "$APK" ] || { echo "APK not found: pass it as 2nd argument or run scripts/build.sh first." >&2; exit 1; }

echo "Connecting to $DEV..."
adb connect "$DEV"
if [ "$(adb -s "$DEV" get-state 2>/dev/null)" != "device" ]; then
  echo "Device unauthorized or unreachable. Accept the debugging prompt on the TV, then run again." >&2
  exit 1
fi

echo "Installing $(basename "$APK")..."
adb -s "$DEV" install -r "$APK"

echo "Turning the accessibility service on..."
CURRENT="$(adb -s "$DEV" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then LIST="$SVC"
elif echo ":$CURRENT:" | grep -q ":$SVC:"; then LIST="$CURRENT"
else LIST="$CURRENT:$SVC"; fi
adb -s "$DEV" shell settings put secure enabled_accessibility_services "$LIST"
adb -s "$DEV" shell settings put secure accessibility_enabled 1
adb -s "$DEV" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

sleep 2
if adb -s "$DEV" shell dumpsys accessibility | grep -q 'label=ShieldLock'; then
  echo "Service active."
else
  echo "Service not active yet: check Settings > Accessibility on the TV."
fi

echo "Opening the setup wizard on the TV..."
adb -s "$DEV" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Done. Finish the wizard on the TV, then consider turning network debugging off."
