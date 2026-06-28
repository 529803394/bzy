#!/usr/bin/env bash
set -e
# 手动构建 APK（不依赖 Android Studio）
# 用法: ./build_apk.sh   => 输出到 dist/app-debug.apk
# 签名使用 release.keystore（保存在项目目录，保证每次签名一致，避免升级时签名冲突）

if [ -d /root/.local/share/mise/installs/java/17 ]; then
    export JAVA_HOME=/root/.local/share/mise/installs/java/17
else
    # 自动发现当前环境的 Java（优先选 mise 下的版本）
    _JAVA_PATH=$(readlink -f $(which java 2>/dev/null) 2>/dev/null)
    if [ -n "$_JAVA_PATH" ] && [ -x "$_JAVA_PATH" ]; then
        export JAVA_HOME=$(dirname $(dirname "$_JAVA_PATH"))
    fi
fi
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=${ANDROID_HOME:-/tmp/android-sdk}
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34
KEYSTORE=/workspace/helloworld/release.keystore
KEYSTORE_PASS=android
KEY_ALIAS=androiddebugkey
KEY_PASS=android
ANDROIDX_CORE_JAR=/workspace/helloworld/libs/androidx-core.jar

ROOT=$(cd "$(dirname "$0")" && pwd)
DIST=$ROOT/dist
TMP=$ROOT/.build_tmp

# ======================== 从 .env 加载密钥与版本号 ========================
ENV_FILE="$ROOT/.env"
DEEPSEEK_API_KEY=""
ZHIPU_API_KEY=""
OSS_ACCESS_KEY_ID=""
OSS_ACCESS_KEY_SECRET=""
VERSION_NAME=""
if [ -f "$ENV_FILE" ]; then
    while IFS='=' read -r key value; do
        # 去掉 # 开头的注释与空行
        key=$(echo "$key" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed 's/^"//;s/"$//' | sed "s/^'//;s/'$//")
        [ -z "$key" ] && continue
        case "$key" in
            DEEPSEEK_API_KEY)      DEEPSEEK_API_KEY="$value" ;;
            ZHIPU_API_KEY)         ZHIPU_API_KEY="$value" ;;
            OSS_ACCESS_KEY_ID)     OSS_ACCESS_KEY_ID="$value" ;;
            OSS_ACCESS_KEY_SECRET) OSS_ACCESS_KEY_SECRET="$value" ;;
            VERSION_NAME)          VERSION_NAME="$value" ;;
        esac
    done < "$ENV_FILE"
    echo "[env] 已从 .env 加载配置"
else
    echo "[env] 未找到 .env，使用环境变量/默认值"
fi

# 默认值
: "${DEEPSEEK_API_KEY:=sk-000000000000000000000000000000000000000000000000000000000000}"
: "${ZHIPU_API_KEY:=00000000.00000000000000000000000000000000}"
: "${OSS_ACCESS_KEY_ID:=YOUR_OSS_ACCESS_KEY_ID}"
: "${OSS_ACCESS_KEY_SECRET:=}"
: "${VERSION_NAME:=2.23.0}"
VERSION_CODE=$(python3 -c "parts='${VERSION_NAME}'.split('.'); print(int(parts[0])*10000 + int(parts[1])*100 + min(int(parts[2]) if len(parts)>2 else 0, 99))")

echo "[env] 版本号: $VERSION_NAME (code=$VERSION_CODE)"
echo "[env] DeepSeek Key:  ${DEEPSEEK_API_KEY:0:6}..."
echo "[env] 智谱 Key:       ${ZHIPU_API_KEY:0:6}..."
echo "[env] OSS AK:         ${OSS_ACCESS_KEY_ID:0:6}..."
echo "[env] OSS SK:         $(if [ -n "$OSS_ACCESS_KEY_SECRET" ]; then echo "已配置"; else echo "未配置"; fi)"

# 写入 AI.java 的默认密钥常量（只在没有用户自选 Key 时起兜底作用）
AI_JAVA="$ROOT/app/src/main/java/com/example/helloworld/AI.java"
if [ -f "$AI_JAVA" ]; then
    # 使用 Python 做可靠替换，避免 sed 转义问题
    python3 - "$AI_JAVA" "$DEEPSEEK_API_KEY" "$ZHIPU_API_KEY" << 'PYEOF'
import sys, re
path, dk, zk = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, 'r', encoding='utf-8') as f: s = f.read()
# 替换 DEEPSEEK_KEY = "..."
s = re.sub(r'(DEEPSEEK_KEY\s*=\s*")[^"]*(")', r'\g<1>' + dk.replace('\\', '\\\\').replace('"', '\\"') + r'\g<2>', s)
# 替换 ZHIPU_KEY = "..."
s = re.sub(r'(ZHIPU_KEY\s*=\s*")[^"]*(")', r'\g<1>' + zk.replace('\\', '\\\\').replace('"', '\\"') + r'\g<2>', s)
with open(path, 'w', encoding='utf-8') as f: f.write(s)
PYEOF
    echo "[env] 已写入默认 API Key 到 AI.java"
fi

# 更新 AndroidManifest.xml 的 versionName / versionCode
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
    python3 - "$MANIFEST" "$VERSION_NAME" "$VERSION_CODE" << 'PYEOF'
import sys, re
path, vn, vc = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, 'r', encoding='utf-8') as f: s = f.read()
s = re.sub(r'android:versionName="[^"]+"', 'android:versionName="' + vn + '"', s)
s = re.sub(r'android:versionCode="[^"]+"', 'android:versionCode="' + vc + '"', s)
with open(path, 'w', encoding='utf-8') as f: f.write(s)
PYEOF
    echo "[env] 已更新 AndroidManifest.xml: versionName=$VERSION_NAME, versionCode=$VERSION_CODE"
fi

export OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET

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
# 自动准备 androidx.core.jar（用于 FileProvider 等 API）
ANDROIDX_DIR=$ROOT/libs
mkdir -p "$ANDROIDX_DIR"
if [ ! -f "$ANDROIDX_CORE_JAR" ]; then
    echo "  -> 下载 androidx.core.aar ..."
    curl -sL "https://dl.google.com/dl/android/maven2/androidx/core/core/1.13.1/core-1.13.1.aar" -o /tmp/core.aar 2>/dev/null
    if [ -f "/tmp/core.aar" ] && [ $(stat -c%s /tmp/core.aar) -gt 10000 ]; then
        cd /tmp && unzip -q core.aar -d /tmp/core_extract 2>/dev/null && cp /tmp/core_extract/classes.jar "$ANDROIDX_CORE_JAR" && cd "$ROOT"
        echo "  -> 准备完成 ($(stat -c%s $ANDROIDX_CORE_JAR) bytes)"
    else
        echo "  -> 下载失败，跳过（如无 FileProvider 引用仍可构建）"
        ANDROIDX_CORE_JAR=""
    fi
fi
CP="$PLATFORM/android.jar"
[ -n "$ANDROIDX_CORE_JAR" ] && CP="$CP:$ANDROIDX_CORE_JAR"
javac -d "$TMP/obj" -cp "$CP" --release 8 \
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
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass pass:"$KEYSTORE_PASS" --key-pass pass:"$KEY_PASS" \
    --out "$DIST/app-debug.apk" "$TMP/aligned.apk"

echo ""
echo "=== 签名验证 ==="
$BUILD_TOOLS/apksigner verify --verbose "$DIST/app-debug.apk" | head -8

echo ""
echo "=== manifest 信息 ==="
$BUILD_TOOLS/aapt dump badging "$DIST/app-debug.apk" | head -6

# 从 APK manifest 中提取 versionName
VERSION_NAME=$($BUILD_TOOLS/aapt dump badging "$DIST/app-debug.apk" 2>/dev/null | grep "versionName=" | head -1 | sed -E "s/.*versionName='([^']*)'.*/\1/")
if [ -z "$VERSION_NAME" ]; then
    VERSION_NAME="2.5.0"
fi

# OSS 配置（阿里云 OSS，bucket pic98，北京 region）
OSS_ENDPOINT="oss-cn-beijing.aliyuncs.com"
OSS_BUCKET="pic98"
OSS_PATH="bzy/apk"
OSS_AK="${OSS_ACCESS_KEY_ID:-YOUR_OSS_ACCESS_KEY_ID}"
OSS_SK="${OSS_ACCESS_KEY_SECRET:-}"
export OSS_ACCESS_KEY_ID="$OSS_AK"
export OSS_ACCESS_KEY_SECRET="$OSS_SK"
export OSS_ENDPOINT OSS_BUCKET OSS_PATH
TMP_VERSION_FILE="$TMP/upgrade.txt"

# APK 走 bzy/apk/，upgrade.txt 走 bzy/（根目录）
UPGRADE_PATH="bzy"
APK_OSS_PATH="oss://${OSS_BUCKET}/${OSS_PATH}/${VERSION_NAME}.apk"
UPGRADE_OSS_PATH="oss://${OSS_BUCKET}/${UPGRADE_PATH}/upgrade.txt"
APK_PUBLIC_URL="https://${OSS_BUCKET}.${OSS_ENDPOINT}/${OSS_PATH}/${VERSION_NAME}.apk"

echo ""
echo "[8/7] 上传 APK 到 OSS ($APK_OSS_PATH) ..."

# 写入 upgrade.txt（内容就是版本号）
echo -n "$VERSION_NAME" > "$TMP_VERSION_FILE"
# rm -rf version.txt 由上面清理

if [ -z "$OSS_SK" ]; then
    echo "⚠️  未配置 OSS_ACCESS_KEY_SECRET，跳过 OSS 上传"
    echo ""
    echo "请设置：export OSS_ACCESS_KEY_SECRET='<your-secret-key>'"
    echo ""
    echo "=== 手动上传命令 ==="
    echo "  APK:    ossutil -i $OSS_AK -k <secret-key> -e $OSS_ENDPOINT cp $DIST/app-debug.apk $APK_OSS_PATH -f"
    echo "  版本:   ossutil -i $OSS_AK -k <secret-key> -e $OSS_ENDPOINT cp <(echo -n $VERSION_NAME) $UPGRADE_OSS_PATH -f"
    echo ""
    echo "=== 文件信息 ==="
    echo "  版本: $VERSION_NAME"
    echo "  APK:  $DIST/app-debug.apk ($(ls -lh $DIST/app-debug.apk | awk '{print $5}'))"
    exit 0
fi

# 用 Python + OSS REST API 上传（不依赖 ossutil，更稳定）
UPGRADE_URL="https://${OSS_BUCKET}.${OSS_ENDPOINT}/bzy/upgrade.txt"
python3 - "$DIST/app-debug.apk" "$APK_PUBLIC_URL" "$TMP_VERSION_FILE" "$UPGRADE_URL" << 'PYEOF'
import hmac, hashlib, base64, datetime, urllib.request, sys

LOCAL_APK = sys.argv[1]
APK_URL = sys.argv[2]
LOCAL_VERSION = sys.argv[3]
VERSION_URL = sys.argv[4]

import os
OSS_AK = os.environ["OSS_ACCESS_KEY_ID"]
OSS_SK = os.environ["OSS_ACCESS_KEY_SECRET"]
OSS_ENDPOINT = os.environ["OSS_ENDPOINT"]
OSS_BUCKET = os.environ["OSS_BUCKET"]
OSS_PATH = os.environ["OSS_PATH"]

def sign(verb, content_type, md5, date, resource, headers=None):
    headers = headers or {}
    h_str = ""
    for k in sorted(headers.keys()):
        h_str += f"{k}:{headers[k]}\n"
    s = f"{verb}\n{md5}\n{content_type}\n{date}\n{h_str}{resource}"
    h = hmac.new(OSS_SK.encode('utf-8'), s.encode('utf-8'), hashlib.sha1)
    return base64.b64encode(h.digest()).decode('utf-8')

def upload(key, local_path, content_type, url_override=None, resource_path=None):
    with open(local_path, 'rb') as f:
        data = f.read()
    date = datetime.datetime.now(datetime.UTC).strftime("%a, %d %b %Y %H:%M:%S GMT")
    # 签名用的 resource：upgrade.txt 走 bzy/，APK 走 bzy/apk/
    if resource_path:
        resource = resource_path
    else:
        resource = f"/{OSS_BUCKET}/{OSS_PATH}/{key}"
    sig = sign("PUT", content_type, "", date, resource)
    # 上传的 URL
    url = VERSION_URL if (key == "upgrade.txt" and VERSION_URL) else f"https://{OSS_BUCKET}.{OSS_ENDPOINT}/{OSS_PATH}/{key}"
    headers = {
        "Host": f"{OSS_BUCKET}.{OSS_ENDPOINT}",
        "Date": date,
        "Content-Type": content_type,
        "Content-Length": str(len(data)),
        "Authorization": f"OSS {OSS_AK}:{sig}",
    }
    req = urllib.request.Request(url, data=data, headers=headers, method="PUT")
    try:
        resp = urllib.request.urlopen(req, timeout=120)
        print(f"✅ {key} 上传成功")
        return True
    except urllib.error.HTTPError as e:
        print(f"❌ {key} 上传失败 -> HTTP {e.code}")
        print(e.read().decode('utf-8')[:500])
        return False

apk_key = APK_URL.rsplit('/', 1)[1]
upload(apk_key, LOCAL_APK, "application/vnd.android.package-archive")
upload("upgrade.txt", LOCAL_VERSION, "text/plain", VERSION_URL, f"/{OSS_BUCKET}/bzy/upgrade.txt")
PYEOF
echo "✅ APK 上传完成: $APK_PUBLIC_URL"
echo "✅ upgrade.txt 上传完成: https://${OSS_BUCKET}.${OSS_ENDPOINT}/bzy/upgrade.txt"

# 同步更新 version.txt（便于 git 追踪，可删除）
if [ -f "$ROOT/version.txt" ]; then
    rm -f "$ROOT/version.txt"
fi
echo ""
echo "=== 版本信息 ==="
echo "  版本号: $VERSION_NAME"
echo "  APK:    $APK_PUBLIC_URL"
echo "  版本:   https://${OSS_BUCKET}.${OSS_ENDPOINT}/${OSS_PATH}/upgrade.txt"
echo ""

# 清理临时目录
rm -rf "$TMP"

