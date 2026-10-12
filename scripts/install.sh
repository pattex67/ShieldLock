#!/usr/bin/env bash
# Installs ShieldLock on an NVIDIA Shield / Android TV device and turns the service on.
# On the device first: Developer options > Network debugging (or USB debugging) enabled.
#
#   bash install.sh <tv-ip>
#
# The APK is picked up automatically when it sits next to this script; otherwise pass it
# as the 2nd argument: bash install.sh <tv-ip> path/to/ShieldLock.apk
# If adb isn't installed, the script offers to download Google's platform-tools next to itself
# (nothing is installed system-wide; delete the platform-tools folder to remove it).
# ASSUME_YES=1 skips that confirmation.
#
set -euo pipefail
IP="${1:?usage: install.sh <tv-ip> [apk] [port]}"
APK="${2:-}"
PORT="${3:-5555}"
PKG=app.shieldlock
SVC="$PKG/$PKG.LockService"
DEV="$IP:$PORT"
HERE="$(cd "$(dirname "$0")" && pwd)"

find_adb() {
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  local c
  for c in "$HERE/platform-tools/adb" "$HERE/platform-tools/adb.exe" \
           "${ANDROID_HOME:-/nonexistent}/platform-tools/adb" \
           "${ANDROID_SDK_ROOT:-/nonexistent}/platform-tools/adb" \
           "$HOME/Library/Android/sdk/platform-tools/adb" \
           "$HOME/Android/Sdk/platform-tools/adb"; do
    if [ -x "$c" ]; then echo "$c"; return; fi
  done
}

download_platform_tools() {
  local os zip
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) os=linux ;;
    MINGW*|MSYS*|CYGWIN*) os=windows ;;
    *) echo "Unsupported system: $(uname -s). Install Android platform-tools manually." >&2; exit 1 ;;
  esac
  echo "adb (Android platform-tools) was not found on this computer." >&2
  if [ "${ASSUME_YES:-}" != "1" ]; then
    if [ ! -t 0 ]; then
      echo "Run again with ASSUME_YES=1 to download it, or install Android platform-tools." >&2; exit 1
    fi
    read -r -p "Download it from Google now (about 7 MB, into $HERE/platform-tools)? [Y/n] " answer
    case "${answer:-y}" in
      [yYoO]*) ;;
      *) echo "adb is required. Install Android platform-tools, or run again and accept the download." >&2; exit 1 ;;
    esac
  fi
  command -v unzip >/dev/null || { echo "unzip is required to extract platform-tools." >&2; exit 1; }
  zip="$(mktemp "${TMPDIR:-/tmp}/platform-tools.XXXXXX")"
  local url="https://dl.google.com/android/repository/platform-tools-latest-$os.zip"
  echo "Downloading $url..." >&2
  if command -v curl >/dev/null; then curl -fsSL -o "$zip" "$url"
  elif command -v wget >/dev/null; then wget -q -O "$zip" "$url"
  else echo "curl or wget is required to download platform-tools." >&2; rm -f "$zip"; exit 1; fi
  unzip -q -o "$zip" -d "$HERE"
  rm -f "$zip"
  find_adb
}

ADB="$(find_adb || true)"
[ -n "$ADB" ] || ADB="$(download_platform_tools)"
[ -n "$ADB" ] || { echo "adb not found after download." >&2; exit 1; }
echo "Using adb: $ADB"
adb() { "$ADB" "$@"; }

if [ -z "$APK" ]; then
  # Next to the script (release download), or the repo's build output.
  APK="$(ls -t "$HERE"/ShieldLock-*.apk "$HERE"/../build/ShieldLock-*.apk 2>/dev/null | head -n 1 || true)"
fi
[ -n "$APK" ] && [ -f "$APK" ] || { echo "APK not found: put ShieldLock-x.y.z.apk next to this script, or pass it as 2nd argument." >&2; exit 1; }

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
