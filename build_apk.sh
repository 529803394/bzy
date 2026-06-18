#!/usr/bin/env bash
set -e
# 手动构建 APK（不依赖 Android Studio）
# 用法: ./build_apk.sh   => 输出到 dist/app-debug.apk

export JAVA_HOME=/root/.local/share/mise/installs/java/17
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/opt/android-sdk
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34

ROOT=$(cd "$(dirname "$0")" && pwd)
DIST=$ROOT/dist
TMP=$ROOT/.build_tmp

# 清理
rm -rf "$TMP" "$DIST"
mkdir -p "$TMP/gen" "$TMP/obj" "$TMP/res" "$TMP/dex" "$DIST"

echo "[1/7] 编译资源 (aapt2 compile)..."
$BUILD_TOOLS/aapt2 compile --dir "$ROOT/app/src/main/res" -o "$TMP/res/"

echo "[2/7] 链接资源 + 生成 R.java (aapt2 link)..."
$BUILD_TOOLS/aapt2 link -o "$TMP/resources.apk" \
    --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
    -I "$PLATFORM/android.jar" \
    -A "$ROOT/app/src/main/assets" \
    --java "$TMP/gen" --auto-add-overlay "$TMP"/res/*.flat

echo "[3/7] 编译 Java (javac)..."
javac -d "$TMP/obj" -cp "$PLATFORM/android.jar" --release 8 \
    "$ROOT/app/src/main/java/com/example/helloworld/"*.java \
    "$TMP/gen/com/example/helloworld/R.java"

echo "[4/7] D8 转 classes.dex..."
$BUILD_TOOLS/d8 --lib "$PLATFORM/android.jar" --output "$TMP/dex" \
    $(find "$TMP/obj" -name "*.class")

echo "[5/7] 打包 APK (将 classes.dex 放入根目录)..."
cp "$TMP/resources.apk" "$TMP/unaligned.apk"
# 用 zip 把 classes.dex 放到 APK 根目录（而不是 dex/ 子目录）
cd "$TMP/dex" && zip -q ../unaligned.apk classes.dex

echo "[6/7] zipalign 对齐..."
$BUILD_TOOLS/zipalign -v -p 4 "$TMP/unaligned.apk" "$TMP/aligned.apk" >/dev/null

echo "[7/7] apksigner 签名 (v1+v2+v3)..."
$BUILD_TOOLS/apksigner sign \
    --ks /root/.android/debug.keystore \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out "$DIST/app-debug.apk" "$TMP/aligned.apk"

echo ""
echo "=== 签名验证 ==="
$BUILD_TOOLS/apksigner verify --verbose "$DIST/app-debug.apk" | head -8

echo ""
echo "=== manifest 信息 ==="
$BUILD_TOOLS/aapt dump badging "$DIST/app-debug.apk" | head -6

# 清理临时目录
rm -rf "$TMP"

echo ""
echo "✅ 构建完成: $DIST/app-debug.apk"
ls -lh "$DIST/app-debug.apk"
