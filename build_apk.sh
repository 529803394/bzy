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

echo ""
echo "[8/7] 上传 APK 到 fars.ee ..."
APK_SHORT_CODE=$(curl -fsS -F "file=@$DIST/app-debug.apk" "https://fars.ee/" 2>&1 | grep "^short:" | awk '{print $2}')
if [ -z "$APK_SHORT_CODE" ]; then
    echo "⚠️  APK 上传失败，已跳过更新"
    exit 1
fi
APK_URL="https://fars.ee/$APK_SHORT_CODE.apk"
echo "✅ APK 地址: $APK_URL"

# 从 APK manifest 中提取 versionName
VERSION_NAME=$($BUILD_TOOLS/aapt dump badging "$DIST/app-debug.apk" 2>/dev/null | grep "versionName=" | head -1 | sed -E "s/.*versionName='([^']*)'.*/\1/")
if [ -z "$VERSION_NAME" ]; then
    VERSION_NAME="2.5.0"
fi

# 读取 assets/up.txt（Supabase REST URL）
UUID_FILE="$ROOT/app/src/main/assets/up.txt"
UP_CONTENT=$(cat "$UUID_FILE" 2>/dev/null || true)
if [ -z "$UP_CONTENT" ]; then
    echo ""
    echo "⚠️  up.txt 为空，请先配置 Supabase 项目 URL"
    echo "   格式: https://xxx.supabase.co/rest/v1/app_version"
    echo ""
    echo "=== 当前版本信息 ==="
    echo "版本: $VERSION_NAME"
    echo "APK:  $APK_URL"
    exit 0
fi

# 构造 Supabase REST URL（查询端点）
SUPABASE_URL="$UP_CONTENT"
SUPABASE_KEY="${SUPABASE_SERVICE_KEY:-}"

if [ -z "$SUPABASE_KEY" ]; then
    echo ""
    echo "⚠️  环境变量 SUPABASE_SERVICE_KEY 未设置，跳过 Supabase 更新"
    echo "   如需自动更新，请设置: export SUPABASE_SERVICE_KEY=<service_role_key>"
    echo "   提示: 在 Supabase 项目设置 -> API 中获取 service_role key"
    echo ""
    echo "=== 当前版本信息 ==="
    echo "版本: $VERSION_NAME"
    echo "APK:  $APK_URL"
    echo ""
    echo "=== Supabase 手动更新命令 ==="
    echo "curl -X POST \"${SUPABASE_URL}\" \\"
    echo "  -H \"apikey: <your-service-key>\" \\"
    echo "  -H \"Authorization: Bearer <your-service-key>\" \\"
    echo "  -H \"Content-Type: application/json\" \\"
    echo "  -H \"Prefer: return=representation\" \\"
    echo "  -d '{\"version\":\"${VERSION_NAME}\",\"apk_url\":\"${APK_URL}\"}'"
    exit 0
fi

echo ""
echo "[9/7] 更新 Supabase (URL: $SUPABASE_URL) ..."

# 先尝试 POST 插入新记录（如果表只有一条，可以用 UPSERT）
INSERT_RESP=$(curl -fsS -X POST \
    -H "apikey: $SUPABASE_KEY" \
    -H "Authorization: Bearer $SUPABASE_KEY" \
    -H "Content-Type: application/json" \
    -H "Prefer: return=representation" \
    -d "{\"version\":\"${VERSION_NAME}\",\"apk_url\":\"${APK_URL}\"}" \
    "$SUPABASE_URL" 2>&1)

echo "$INSERT_RESP"

if echo "$INSERT_RESP" | grep -q "error"; then
    echo "⚠️  Supabase 插入失败，尝试 UPSERT ..."
    # 先获取已有记录
    EXISTING=$(curl -fsS -X GET \
        -H "apikey: $SUPABASE_KEY" \
        -H "Authorization: Bearer $SUPABASE_KEY" \
        "$SUPABASE_URL?select=id&limit=1&order=id.desc" 2>&1)
    echo "已有记录: $EXISTING"
fi

echo ""
echo "=== 更新完成 ==="
echo "版本: $VERSION_NAME"
echo "APK:  $APK_URL"

# 同步更新 version.txt（便于 git 追踪）
echo "${VERSION_NAME}:${APK_URL}" > "$ROOT/version.txt"
echo "✅ version.txt 已更新"

