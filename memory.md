# Memory · 长期记忆

> 存放需要长期记住的信息、约定、偏好。
> 每次会话开始前会读取；新增内容以日期分节追加。

---

## 2026-06-21

- **Android 白噪音 App 发布流程**：版本号 +1（如 `2.23.3 → 2.23.4`），更新 `.env` 与 `AndroidManifest.xml`，再运行 `./build_apk.sh` 自动构建并上传到 OSS。
- **OSS 路径**：APK 存 `bzy/apk/{version}.apk`，版本信息存 `bzy/upgrade.txt`（根目录）。
- **APK 下载地址模板**：`https://pic98.oss-cn-beijing.aliyuncs.com/bzy/apk/{version}.apk`
- **AI 模型约定**：`AI.java` 固定使用 `DeepSeek V4 Flash`（modelId=`deepseek-chat`），**不在设置界面暴露模型选择**；推荐/聊天/生成创意共用该模型。
- **最新已发布版本**：`2.23.4`（2026-06-21）

