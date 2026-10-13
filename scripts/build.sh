#!/usr/bin/env bash
# Builds ShieldLock without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner
#
#   ./scripts/build.sh
#
# Requirements: Android SDK (build-tools + one platform) and a JDK 11+.
# Signing: uses signing.properties at the repo root if present (see README), otherwise
# the Android debug keystore.
set -euo pipefail
VERSION_NAME=1.0.1
VERSION_CODE=2

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# --- Locate the SDK, build-tools, platform and JDK ---------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$d" ] && SDK="$d" && break
  done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "Android SDK not found. Set ANDROID_HOME." >&2; exit 1; }

BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -n 1)"; BT="${BT%/}"
PLATFORM="$(ls -d "$SDK"/platforms/android-*/ | sort -V | tail -n 1)"; PLATFORM="${PLATFORM%/}"
JAR="$PLATFORM/android.jar"
[ -f "$JAR" ] || { echo "No platform with android.jar in $SDK/platforms." >&2; exit 1; }

if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null || { echo "javac not found. Install a JDK or set JAVA_HOME." >&2; exit 1; }

# Windows (Git Bash) ships .bat wrappers for d8 and apksigner.
D8="$BT/d8"; APKSIGNER="$BT/apksigner"
[ -f "$D8" ] || D8="$BT/d8.bat"
[ -f "$APKSIGNER" ] || APKSIGNER="$BT/apksigner.bat"

echo "SDK: $SDK"
echo "Build-tools: $(basename "$BT"), platform: $(basename "$PLATFORM")"

# --- Build --------------------------------------------------------------------------------
OUT="$ROOT/build"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest "$ROOT/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 30 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$OUT/res.zip"

# javac reads paths from an argfile: quote them (spaces) and, on Git Bash, convert to Windows form.
native() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
find "$ROOT/src" "$OUT/gen" -name '*.java' | while IFS= read -r f; do printf '"%s"\n' "$(native "$f")"; done > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$JAR" \
  -classpath "$BT/core-lambda-stubs.jar" -d "$OUT/classes" @"$OUT/sources.txt"

CLASSES=()
while IFS= read -r f; do CLASSES+=("$f"); done < <(find "$OUT/classes" -name '*.class')
"$D8" --release --min-api 26 --lib "$JAR" --output "$OUT/dex" "${CLASSES[@]}"

(cd "$OUT/dex" && jar uf "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# --- Sign -----------------------------------------------------------------------------------
PROPS="$ROOT/signing.properties"
prop() { grep -E "^\s*$1\s*=" "$PROPS" | head -n 1 | sed -E 's/^[^=]*=\s*//' | tr -d '\r'; }
if [ -f "$PROPS" ]; then
  KS="$(prop storeFile)"; STORE_PASS="$(prop storePassword)"; ALIAS="$(prop keyAlias)"; KEY_PASS="$(prop keyPassword)"
  echo "Signing with release key: $KS"
else
  KS="$HOME/.android/debug.keystore"; STORE_PASS=android; ALIAS=androiddebugkey; KEY_PASS=android
  if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
      -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
  fi
  echo "Signing with the debug key (no signing.properties found)."
fi

APK="$OUT/ShieldLock-$VERSION_NAME.apk"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$APK" "$OUT/aligned.apk"

echo "OK -> $APK"
