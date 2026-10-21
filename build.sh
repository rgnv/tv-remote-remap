#!/usr/bin/env bash
#
# Gradle-free build for TV Remote Remap.
#
# Needs: Android SDK (ANDROID_HOME or ANDROID_SDK_ROOT) with build-tools and a
# platform, a JDK 11+ (javac), python3 (to append classes.dex to the APK).
#
# Signing (first match wins):
#   1. KEYSTORE_BASE64          base64 of a JKS/PKCS12 keystore, decoded here
#   2. KEYSTORE_FILE            path to a keystore
#   3. neither                  an ephemeral debug keystore is generated
#   plus KEYSTORE_PASSWORD (default: android), KEY_ALIAS (default: tvremap),
#   KEY_PASSWORD (default: same as KEYSTORE_PASSWORD).
#
set -euo pipefail

ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
if [ ! -d "$SDK" ] && [ -d /usr/local/lib/android/sdk ]; then
  SDK=/usr/local/lib/android/sdk   # GitHub-hosted runner image
fi
[ -d "$SDK" ] || { echo "Android SDK not found (set ANDROID_HOME)" >&2; exit 1; }

# Pin the toolchain: a moving build-tools/platform makes CI and local builds
# silently disagree. Fall back only when the pinned versions are absent.
BT="$SDK/build-tools/35.0.0"
if [ ! -x "$BT/aapt2" ]; then
  BT=$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)
  echo "warning: build-tools 35.0.0 missing, using $BT" >&2
fi
[ -x "$BT/aapt2" ] || { echo "aapt2 not found under $SDK/build-tools" >&2; exit 1; }

PLATFORM="$SDK/platforms/android-35"
if [ ! -f "$PLATFORM/android.jar" ]; then
  for candidate in $(find "$SDK/platforms" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -Vr); do
    if [ -f "$candidate/android.jar" ]; then PLATFORM=$candidate; break; fi
  done
  echo "warning: platform android-35 missing, using $PLATFORM" >&2
fi
[ -n "$PLATFORM" ] || { echo "no platform android.jar found" >&2; exit 1; }

JAR="$PLATFORM/android.jar"
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)
VERSION=${VERSION:-0.0.0}

echo "== build =="
echo "sdk:      $SDK"
echo "tools:    $BT"
echo "platform: $PLATFORM"
echo "version:  $VERSION"

rm -rf build dist
mkdir -p build/classes build/dex build/gen dist

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk \
  -I "$JAR" --manifest AndroidManifest.xml build/res.zip --auto-add-overlay

mapfile -t JAVA_SRC < <(find src -name '*.java')
javac --release 11 -classpath "$JAR" -d build/classes "${JAVA_SRC[@]}"

mapfile -t CLASS_FILES < <(find build/classes -name '*.class')
"$BT/d8" --lib "$JAR" --min-api 21 --output build/dex "${CLASS_FILES[@]}"

# No `zip` on minimal runners: append the dex with python instead of re-linking.
python3 - "$ROOT" <<'PY'
import sys, zipfile, shutil
root = sys.argv[1]
shutil.copyfile(root + '/build/base.apk', root + '/build/withdex.apk')
with zipfile.ZipFile(root + '/build/withdex.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(root + '/build/dex/classes.dex', 'classes.dex')
PY

"$BT/zipalign" -f 4 build/withdex.apk build/aligned.apk

echo "== sign =="
KS_PASS=${KEYSTORE_PASSWORD:-android}
KEY_ALIAS=${KEY_ALIAS:-tvremap}
KEY_PASS=${KEY_PASSWORD:-$KS_PASS}
KEYSTORE=build/signing.jks

if [ -n "${CI:-}" ] || [ -n "${REQUIRE_KEYSTORE:-}" ]; then
  if [ -z "${KEYSTORE_BASE64:-}" ] && [ -z "${KEYSTORE_FILE:-}" ]; then
    echo "refusing to ship a release build without a keystore" >&2
    exit 1
  fi
fi

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  echo "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE"
  echo "using keystore from KEYSTORE_BASE64"
elif [ -n "${KEYSTORE_FILE:-}" ]; then
  cp "$KEYSTORE_FILE" "$KEYSTORE"
  echo "using keystore from KEYSTORE_FILE"
else
  echo "no keystore provided: generating an ephemeral debug keystore"
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

APK="dist/tv-remote-remap-$VERSION.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KEY_PASS" --out "$APK" build/aligned.apk
"$BT/apksigner" verify "$APK"
"$BT/apksigner" verify --print-certs "$APK" | head -3

cd dist
sha256sum "$(basename "$APK")" > SHA256SUMS
cd "$ROOT"

echo "== done =="
ls -l "$APK" dist/SHA256SUMS
