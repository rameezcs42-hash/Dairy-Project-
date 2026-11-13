#!/usr/bin/env bash
# DairyFlow — offline APK build (no Gradle).
# Requirements: Android SDK (aapt2, zipalign), D8 + apksigner (from Android SDK build-tools),
#               an Eclipse JDT compiler jar (ecj), and a Java 8+ runtime (JRE 25 works).
# Usage: TOOLCHAIN=/path/to/toolchain ./tools/build_apk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLCHAIN="${TOOLCHAIN:-/home/user/toolchain}"
ANDROID_JAR="$TOOLCHAIN/sdk/android-30/public/android.jar"
LAMBDA_STUBS="$TOOLCHAIN/sdk/tools/linux/bin/core-lambda-stubs.jar"
AAPT2="$TOOLCHAIN/sdk/tools/linux/bin/aapt2"
D8_JAR="$TOOLCHAIN/sdk/tools/linux/lib/d8.jar"
APKSIGNER="$TOOLCHAIN/sdk/tools/linux/lib/apksigner.jar"
ZIPALIGN="$TOOLCHAIN/sdk/tools/linux/bin/zipalign"
ECJ="$TOOLCHAIN/ecj/ecj-4.6.1.jar"
JAVA_BIN="${JAVA_BIN:-$TOOLCHAIN/jdk4py-src/jdk4py/java-runtime/bin/java}"
KEYSTORE="${KEYSTORE:-$ROOT/tools/dairyflow-debug.keystore}"
KS_PASS="${KS_PASS:-dairyflow}"

BUILD="$ROOT/build"
SRC_MAIN="$ROOT/app/src/main"

rm -rf "$BUILD"
mkdir -p "$BUILD/aapt2" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "==> aapt2 compile (resources)"
"$AAPT2" compile --dir "$SRC_MAIN/res" -o "$BUILD/compiled.zip"

echo "==> aapt2 link (R.java + unsigned APK)"
"$AAPT2" link -o "$BUILD/app-unsigned.apk" \
    --manifest "$SRC_MAIN/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    --java "$BUILD/gen" \
    --min-sdk-version 21 --target-sdk-version 30 \
    -z "$BUILD/compiled.zip"

echo "==> ECJ compile (Java 8)"
find "$SRC_MAIN/java" -name '*.java' > "$BUILD/sources.txt"
find "$BUILD/gen" -name 'R.java' >> "$BUILD/sources.txt"
"$JAVA_BIN" -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -source 1.8 -target 1.8 -nowarn -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR:$LAMBDA_STUBS" \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "==> D8 dex"
"$JAVA_BIN" -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api 21 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
    $(find "$BUILD/classes" -name "*.class")

echo "==> package + zipalign + sign"
cp "$BUILD/app-unsigned.apk" "$BUILD/app-with-dex.apk"
(cd "$BUILD/dex" && zip -q -X ../app-with-dex.apk classes.dex)
"$ZIPALIGN" -f -p 4 "$BUILD/app-with-dex.apk" "$BUILD/app-aligned.apk"
"$JAVA_BIN" -jar "$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$ROOT/DairyFlow-debug.apk" "$BUILD/app-aligned.apk"
"$JAVA_BIN" -jar "$APKSIGNER" verify --verbose "$ROOT/DairyFlow-debug.apk" || true
echo "==> Done: $ROOT/DairyFlow-debug.apk"
