package com.example.helloworld;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一的 AI API 客户端
 *
 * - 模型固定：DeepSeek V4 Flash（deepseek-chat），设置中不对用户暴露选择
 * - 推荐分析：综合时间/天气/地点/最近聊天记录，给出名字/描述/配方和理由
 * - 声音创意生成：同样综合上下文，输出带理由的创意声音
 * - 聊天对话：带入最近 6 轮历史，保持自然亲切
 * - AI 生成背景图：智谱 AI cogview-3-plus 文生图
 * - AI 生成音频：程序化合成 wav（白噪音组件加权混合）
 *
 * API Key 来源：
 *   DEEPSEEK_KEY / ZHIPU_KEY 由构建脚本读取根目录 .env 文件并注入，
 *   方便统一管理而不需把密钥写进 git。
 *
 * 所有请求在后台线程执行，不能在主线程直接调用。
 */
public class AI {

    // ========== API KEY（由 build_apk.sh 从 .env 注入，保持下面占位行格式）==========
    private static final String DEEPSEEK_KEY = "YOUR_DEEPSEEK_API_KEY";
    private static final String ZHIPU_KEY = "YOUR_ZHIPU_API_KEY";
    private static final String ZHIPU_IMAGE_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/images/generations";

    // ========== 端点 ==========
    private static final String DEEPSEEK_CHAT = "https://api.deepseek.com/v1/chat/completions";

    // ========== 模型（固定：DeepSeek V4 Flash）==========
    // 注：默认模型锁定为 DeepSeek V4 Flash，不在设置中对用户暴露选择
    private static final String DEFAULT_MODEL_ID = "deepseek-chat";
    private static final String DEFAULT_ENDPOINT = DEEPSEEK_CHAT;
    private static final String DEFAULT_API_KEY  = DEEPSEEK_KEY;

    // ========== 聊天消息 ==========
    public static class ChatMessage {
        public String text;
        public boolean fromUser;
        public ChatMessage(String t, boolean u) { text = t; fromUser = u; }
    }

    // ========== 推荐结果 ==========
    public static class RecResult {
        public String soundName;      // 推荐的声音名（匹配 SoundStore 默认名之一）
        public String shortReason;    // 一句话理由（用户看的）
        public String detailedReason; // 详细推理过程（UI 展示用）
        public String creativeName;   // 如果有「AI 生成新声音」分支，这里是名字
        public String creativeDesc;   // 新声音描述
        public String recipe;         // 新声音配方
        public String recipeReason;   // 配方与用户描述关联的理由
    }

    // ========== AI 生成的自定义声音 ==========
    public static class CreativeSound {
        public String name;
        public String description;
        public String recipe;
        public String reason;  // 为什么这样配
    }

    // 配方可选组件（必须与 SoundGenerator 一致）
    private static final String[] RECIPE_COMPONENTS = {
        "rain", "wind", "ocean", "white", "pink", "brown",
        "forest", "fire", "rain_thunder", "cafe", "train",
        "heartbeat", "bell"
    };
    private static final String[] RECIPE_NAMES_CN = {
        "雨声", "风声", "海浪", "白噪音", "粉噪音", "棕噪音",
        "森林鸟鸣", "壁炉", "雷雨", "咖啡馆", "火车",
        "心跳", "钟声"
    };

    // ======================== 1) 聊天对话 ========================
    public static String chatWithSound(android.content.Context ctx, String soundName,
                                        String userText, List<ChatMessage> history) {
        final String MODEL_ID = DEFAULT_MODEL_ID;
        final String ENDPOINT = DEFAULT_ENDPOINT;
        final String API_KEY = DEFAULT_API_KEY;

        StringBuilder sys = new StringBuilder();
        sys.append("你是一个温柔、简洁、像朋友一样的「").append(soundName).append("」陪伴助手。\n");
        sys.append("用户正在听的白噪音/环境音是「").append(soundName).append("」。\n");
        sys.append("你的回复必须短（80字以内）、温暖、像在陪伴一样。\n");
        sys.append("可以偶尔提到声音的意象（如「雨声像有人在给你盖被子」「壁炉的噼啪声让人安心」），但不要说教。\n");
        sys.append("如果用户问问题，用常识回答，保持简短。\n");

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(jsonEscape(MODEL_ID)).append("\",");
        body.append("\"temperature\":0.85,\"max_tokens\":220,\"messages\":[");
        body.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(sys.toString())).append("\"},");

        int start = Math.max(0, (history == null ? 0 : history.size()) - 6);
        if (history != null) {
            for (int i = start; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                String role = msg.fromUser ? "user" : "assistant";
                body.append("{\"role\":\"").append(role)
                     .append("\",\"content\":\"").append(jsonEscape(msg.text)).append("\"},");
            }
        }
        body.append("{\"role\":\"user\",\"content\":\"").append(jsonEscape(userText)).append("\"}");
        body.append("]}");

        String result = postJson(ENDPOINT, API_KEY, body.toString(), ENDPOINT.contains("openrouter"));
        if (result == null || result.isEmpty()) {
            return "嗯，我在陪着你～（" + soundName + "）";
        }
        return result;
    }

    // ======================== 2) 推荐白噪音（聊天式） ========================
    public static RecResult recommend(android.content.Context ctx,
                                      String timeNow,      // "周五 晚上 22:30"
                                      String season,       // "夏季"
                                      String weather,      // "晴朗"
                                      String locationHint, // "家/卧室"
                                      String userInput,    // 用户说的话
                                      List<ChatMessage> history) { // 最近一天的聊天记录
        final String MODEL_ID = DEFAULT_MODEL_ID;
        final String ENDPOINT = DEFAULT_ENDPOINT;
        final String API_KEY = DEFAULT_API_KEY;

        StringBuilder sys = new StringBuilder();
        sys.append("你是一位白噪音推荐专家。根据用户当前状态和对话，你需要从「内置白噪音列表」中推荐一个最匹配的声音。\n\n");
        sys.append("【内置白噪音列表】（soundName 必须严格从以下选一个，直接写中文名字）:\n");
        for (String cn : SoundStore.DEFAULT_NAMES_CN) sys.append("- ").append(cn).append("\n");
        sys.append("\n【输出要求】严格 JSON，字段如下：\n");
        sys.append("  soundName     : string —— 从上面列表中严格选一个。\n");
        sys.append("  shortReason   : string —— 给用户看的一句话理由，不超过 30 字，温暖自然。\n");
        sys.append("  detailedReason: string —— 50~150 字。说明你是如何根据时间/季节/天气/地点/用户对话/用户输入来推理的。指出「最后一句话对推荐起了决定性作用」（如果有用户输入）。\n");
        sys.append("  recipe        : string —— 程序化音频配方，从以下组件中选 1~3 个，带权重（0.1~1.0），格式如 \"rain:0.7,wind:0.3\"。可用组件: ");
        for (int i = 0; i < RECIPE_COMPONENTS.length; i++) {
            sys.append(RECIPE_COMPONENTS[i]).append("(").append(RECIPE_NAMES_CN[i]).append(")");
            if (i < RECIPE_COMPONENTS.length - 1) sys.append(", ");
        }
        sys.append("\n  recipeReason  : string —— 解释配方为什么与用户描述相关（例如「你说『想有下雨天的感觉』，所以用了 rain 雨声作为主成分，再混入一点 wind 风声来营造自然层次感」）。\n");
        sys.append("\n【你的判断依据】\n");
        sys.append("- 优先参考用户『最后一次说的话』，它在推荐中起决定作用；\n");
        sys.append("- 其次结合时间、季节、天气、地点；\n");
        sys.append("- 再参考最近聊天记录；\n");
        sys.append("- 若用户明确说了想要某个声音（例如『给我来点雨声』『我要心跳声』），直接按用户意愿来。\n");
        sys.append("\n只输出 JSON，不要有多余文字，不要 markdown 代码块。\n");

        StringBuilder userMsg = new StringBuilder();
        userMsg.append("【当前状态】\n");
        userMsg.append("- 时间: ").append(safe(timeNow)).append("\n");
        userMsg.append("- 季节: ").append(safe(season)).append("\n");
        userMsg.append("- 天气: ").append(safe(weather)).append("\n");
        userMsg.append("- 地点: ").append(safe(locationHint)).append("\n\n");

        if (history != null && !history.isEmpty()) {
            userMsg.append("【最近一天的聊天记录】（由旧到新）：\n");
            int hStart = Math.max(0, history.size() - 20);
            for (int i = hStart; i < history.size(); i++) {
                ChatMessage cm = history.get(i);
                userMsg.append(cm.fromUser ? "- 用户：" : "- 助手：").append(cm.text).append("\n");
            }
            userMsg.append("\n");
        }
        if (userInput != null && !userInput.isEmpty()) {
            userMsg.append("【用户最后一句话（最重要，起决定性作用）】\n");
            userMsg.append(userInput).append("\n\n");
        }
        userMsg.append("请根据以上信息，输出 JSON。");

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(jsonEscape(MODEL_ID)).append("\",");
        body.append("\"temperature\":0.75,\"max_tokens\":500,\"messages\":[");
        body.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(sys.toString())).append("\"},");
        body.append("{\"role\":\"user\",\"content\":\"").append(jsonEscape(userMsg.toString())).append("\"}");
        body.append("]}");

        String raw = postJson(ENDPOINT, API_KEY, body.toString(), ENDPOINT.contains("openrouter"));
        RecResult r = parseRecResult(raw);

        // 兜底
        if (r == null || r.soundName == null || r.soundName.isEmpty()) {
            r = fallbackRecResult(userInput, season);
        }
        return r;
    }

    // ======================== 3) AI 生成新的创意白噪音 ========================
    public static CreativeSound generateCreativeSound(android.content.Context ctx,
                                                      String userInput,
                                                      List<ChatMessage> history,
                                                      String timeNow, String season, String weather, String locationHint) {
        final String MODEL_ID = DEFAULT_MODEL_ID;
        final String ENDPOINT = DEFAULT_ENDPOINT;
        final String API_KEY = DEFAULT_API_KEY;

        StringBuilder sys = new StringBuilder();
        sys.append("你是一位声音设计艺术家，擅长根据用户的状态与对话创造全新的白噪音/环境音体验。\n\n");
        sys.append("【可用配方组件】（recipe 必须从以下组件中选 1~3 个，带权重 0.1~1.0，组件名用英文）:\n");
        for (int i = 0; i < RECIPE_COMPONENTS.length; i++) {
            sys.append("- ").append(RECIPE_COMPONENTS[i]).append("（").append(RECIPE_NAMES_CN[i]).append("）\n");
        }
        sys.append("\n【输出格式】严格 JSON，字段：\n");
        sys.append("  name         : string —— 2~8 个字，有画面感的中文名字（如「雨夜书房」「海边黄昏」）。\n");
        sys.append("  description  : string —— 30 字以内，简短描述这个声音带来的感觉。\n");
        sys.append("  recipe       : string —— 例如 \"rain:0.7,fire:0.2,wind:0.1\"。\n");
        sys.append("  reason       : string —— 60~150 字。解释：(1)为什么这个名字/描述与用户描述/聊天内容匹配；(2)配方里每个组件的作用；(3)特别说明「用户最后一句话如何影响设计决策」。\n");
        sys.append("\n只输出 JSON，不要有多余文字，不要 markdown 代码块。\n");

        StringBuilder userMsg = new StringBuilder();
        userMsg.append("【当前状态】\n");
        userMsg.append("- 时间: ").append(safe(timeNow)).append("\n");
        userMsg.append("- 季节: ").append(safe(season)).append("\n");
        userMsg.append("- 天气: ").append(safe(weather)).append("\n");
        userMsg.append("- 地点: ").append(safe(locationHint)).append("\n\n");

        if (history != null && !history.isEmpty()) {
            userMsg.append("【最近一天的聊天记录】\n");
            int hStart = Math.max(0, history.size() - 20);
            for (int i = hStart; i < history.size(); i++) {
                ChatMessage cm = history.get(i);
                userMsg.append(cm.fromUser ? "- 用户：" : "- 助手：").append(cm.text).append("\n");
            }
            userMsg.append("\n");
        }
        if (userInput != null && !userInput.isEmpty()) {
            userMsg.append("【用户最后一句话（最重要，对设计起决定性作用）】\n");
            userMsg.append(userInput).append("\n\n");
        }
        userMsg.append("请为我创造一个全新的白噪音体验，输出 JSON。");

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"").append(jsonEscape(MODEL_ID)).append("\",");
        body.append("\"temperature\":0.9,\"max_tokens\":500,\"messages\":[");
        body.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(sys.toString())).append("\"},");
        body.append("{\"role\":\"user\",\"content\":\"").append(jsonEscape(userMsg.toString())).append("\"}");
        body.append("]}");

        String raw = postJson(ENDPOINT, API_KEY, body.toString(), ENDPOINT.contains("openrouter"));
        return parseCreativeSound(raw, userInput);
    }

    // ========== AI 生成结果 ==========
    public static class MediaResult {
        public String audioUrl;       // 本地文件路径（"file://" 开头或直接路径）或 http URL
        public String bgImageUrl;     // https 图片直链（智谱 AI 生成）
        public String recipe;         // 程序化音频配方（如 "rain:0.7,wind:0.3"）
        public String error;
        public boolean audioGenerated; // 音频是本地生成的（true）或网络 URL（false）
    }

    // 中文关键词 → 程序化音频组件（给 generateAudioSound / 兜底配方用）
    private static final String[][] COMPONENT_MAP = {
        {"雨", "rain:0.9,wind:0.1"},
        {"雨声", "rain:0.85,pink:0.15"},
        {"海", "ocean:0.9,wind:0.1"},
        {"海浪", "ocean:0.85,brown:0.15"},
        {"森", "forest:0.7,wind:0.3"},
        {"森林", "forest:0.8,wind:0.2"},
        {"风", "wind:0.85,brown:0.15"},
        {"风声", "wind:0.9,white:0.1"},
        {"火", "fire:0.9,brown:0.1"},
        {"火焰", "fire:0.85,brown:0.15"},
        {"雷", "rain_thunder:0.8,rain:0.2"},
        {"雷声", "rain_thunder:0.7,rain:0.3"},
        {"雷电", "rain_thunder:0.9,wind:0.1"},
        {"咖", "cafe:0.9,brown:0.1"},
        {"咖啡", "cafe:0.85,pink:0.15"},
        {"咖啡馆", "cafe:0.9,white:0.1"},
        {"鸟", "forest:0.9,wind:0.1"},
        {"鸟鸣", "forest:0.85,wind:0.15"},
        {"白噪音", "white:0.7,pink:0.3"},
        {"粉噪音", "pink:0.9,white:0.1"},
        {"棕噪音", "brown:0.9,pink:0.1"},
        {"雷雨", "rain_thunder:0.8,wind:0.2"},
        {"雪", "wind:0.7,pink:0.3"},
        {"火车", "train:0.85,brown:0.15"},
        {"心跳", "heartbeat:0.9,brown:0.1"},
        {"钟", "bell:0.8,white:0.2"},
        {"炉", "fire:0.85,brown:0.15"},
    };

    // ======================== 4) AI 生成（替代旧的网络搜索）========================
    // 背景图：调用智谱 AI 文生图；音频：优先程序化合成 wav（无需网络也能生成）
    public static MediaResult searchMedia(android.content.Context ctx, String soundName) {
        MediaResult r = new MediaResult();
        if (soundName == null) soundName = "白噪音";

        // ---- 音频：优先用 DeepSeek LLM 输出配方 → SoundGenerator 合成 wav ----
        String recipe = null;
        CreativeSound cs = null;
        try {
            cs = generateCreativeSound(ctx, soundName, null, null, null, null, null);
            if (cs != null && cs.recipe != null && !cs.recipe.isEmpty()) recipe = cs.recipe;
        } catch (Throwable ignored) {}

        // 兜底：按内置关键词匹配
        if (recipe == null || recipe.isEmpty()) {
            recipe = fallbackRecipe(soundName);
        }
        r.recipe = recipe;

        // 程序化合成：写本地 wav 文件，返回绝对路径
        if (ctx != null) {
            try {
                java.io.File outDir = new java.io.File(ctx.getFilesDir(), "ai_sounds");
                if (!outDir.exists()) outDir.mkdirs();
                java.io.File wavFile = new java.io.File(outDir,
                    "ai_" + Math.abs(soundName.hashCode()) + "_" + System.currentTimeMillis() + ".wav");
                SoundGenerator.generateWav(wavFile, recipe, 45);
                r.audioUrl = wavFile.getAbsolutePath();
                r.audioGenerated = true;
            } catch (Throwable t) {
                r.audioUrl = null;
                r.audioGenerated = false;
            }
        }

        // ---- 背景图：智谱 AI 文生图 ----
        try {
            String imagePrompt = soundName;
            if (imagePrompt.length() < 8) {
                imagePrompt = imagePrompt + "，舒缓放松，治愈氛围，高清壁纸，8K画质，真实感";
            }
            String imgUrl = generateImage(imagePrompt);
            if (imgUrl != null && !imgUrl.isEmpty()) {
                r.bgImageUrl = imgUrl;
            }
        } catch (Throwable ignored) {}

        if (r.audioUrl == null && r.bgImageUrl == null) {
            r.error = "生成失败，请检查网络后再试";
        }
        return r;
    }

    // 根据声音名给出程序化音频配方（当 LLM 不可用时的兜底）
    private static String fallbackRecipe(String soundName) {
        if (soundName == null) return "rain:0.7,wind:0.3";
        String lower = soundName.trim();
        // 精确优先
        for (String[] row : COMPONENT_MAP) {
            if (lower.equals(row[0])) return row[1];
        }
        // 包含匹配
        for (String[] row : COMPONENT_MAP) {
            if (lower.contains(row[0])) return row[1];
        }
        // 最后兜底：混合雨声+风
        return "rain:0.5,wind:0.3,pink:0.2";
    }

    // 快捷入口：从 Context+声音名 生成一个 45 秒 wav，返回文件绝对路径
    public static String generateAudioSound(android.content.Context ctx, String soundName) {
        String recipe = null;
        try {
            CreativeSound cs = generateCreativeSound(ctx, soundName, null, null, null, null, null);
            if (cs != null && cs.recipe != null && !cs.recipe.isEmpty()) recipe = cs.recipe;
        } catch (Throwable ignored) {}
        if (recipe == null || recipe.isEmpty()) recipe = fallbackRecipe(soundName);

        if (ctx == null) return null;
        try {
            java.io.File outDir = new java.io.File(ctx.getFilesDir(), "ai_sounds");
            if (!outDir.exists()) outDir.mkdirs();
            java.io.File wavFile = new java.io.File(outDir,
                "ai_" + Math.abs(soundName.hashCode()) + "_" + System.currentTimeMillis() + ".wav");
            SoundGenerator.generateWav(wavFile, recipe, 45);
            return wavFile.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    // ======================== 5) 智谱 AI 生成背景图 ========================
    // 调用 cogview-3-plus 文生图模型，返回图片直链 URL。9:16 比例（720x1280）
    public static String generateImage(String promptChinese) {
        String prompt = (promptChinese == null || promptChinese.isEmpty()) ? "白噪音背景图" : promptChinese;
        if (prompt.length() < 10) {
            prompt = prompt + "，舒缓放松风格，高清壁纸，治愈系氛围，8K画质";
        }

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"cogview-3-plus\",\"prompt\":\"")
            .append(jsonEscape(prompt)).append("\",\"size\":\"720x1280\"}");

        String raw = postJsonRaw(ZHIPU_IMAGE_ENDPOINT, ZHIPU_KEY, body.toString());
        if (raw == null || raw.isEmpty()) return null;
        return extractImageUrl(raw);
    }

    // ======================== 6) 智谱 AI 视频生成（图像转视频）========================
    private static final String ZHIPU_VIDEO_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/videos/generations";
    private static final String ZHIPU_ASYNC_RESULT_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/async-result";

    // 视频生成结果
    public static class VideoResult {
        public String taskId;
        public String videoUrl;
        public String error;
        public boolean success;
    }

    // 提交视频生成任务（图像转视频，5秒）
    public static VideoResult submitVideoTask(String imageUrl, String prompt) {
        VideoResult result = new VideoResult();
        if (imageUrl == null || imageUrl.isEmpty()) {
            result.error = "图片URL为空";
            return result;
        }
        String motionPrompt = (prompt == null || prompt.isEmpty()) ? "画面缓缓流动，柔和自然" : prompt;

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":\"cogvideox-3\",\"image_url\":\"")
            .append(jsonEscape(imageUrl)).append("\"");
        body.append(",\"prompt\":\"").append(jsonEscape(motionPrompt)).append("\"");
        body.append(",\"duration\":5,\"quality\":\"speed\"}");

        String raw = postJsonRaw(ZHIPU_VIDEO_ENDPOINT, ZHIPU_KEY, body.toString());
        if (raw == null || raw.isEmpty()) {
            result.error = "提交任务失败";
            return result;
        }
        // 提取 task id
        String taskId = extractJsonField(raw, "id");
        if (taskId == null || taskId.isEmpty()) {
            result.error = "未获取到任务ID";
            return result;
        }
        result.taskId = taskId;
        return result;
    }

    // 查询视频生成任务结果（轮询）
    public static VideoResult queryVideoResult(String taskId) {
        VideoResult result = new VideoResult();
        if (taskId == null || taskId.isEmpty()) {
            result.error = "任务ID为空";
            return result;
        }
        long start = System.currentTimeMillis();
        int code = -1;
        try {
            URL url = new URL(ZHIPU_ASYNC_RESULT_ENDPOINT + "/" + taskId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + ZHIPU_KEY);
            code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String raw = sb.toString();
                String status = extractJsonField(raw, "task_status");
                if ("SUCCESS".equals(status)) {
                    String videoUrl = extractVideoUrl(raw);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        result.videoUrl = videoUrl;
                        result.success = true;
                    } else {
                        result.error = "视频URL提取失败";
                    }
                } else if ("FAIL".equals(status)) {
                    result.error = "生成失败";
                } else {
                    result.taskId = taskId; // PROCESSING，继续轮询
                }
            } else {
                result.error = "查询失败: HTTP " + code;
            }
            conn.disconnect();
            HttpLogger.log("GET", url.toString(), code, System.currentTimeMillis() - start, result.error);
        } catch (Exception e) {
            HttpLogger.log("GET", ZHIPU_ASYNC_RESULT_ENDPOINT + "/" + taskId, code, System.currentTimeMillis() - start, e.getMessage());
            result.error = "查询异常: " + e.getMessage();
        }
        return result;
    }

    // 从视频生成响应中提取视频URL
    private static String extractVideoUrl(String json) {
        if (json == null || json.isEmpty()) return null;
        // 匹配 video_result.url
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"url\"\\s*:\\s*\"(https?://[^\"]+\\.mp4[^\"]*)\"",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
        if (m.find()) return m.group(1);
        // 兼容其他格式
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
            "\"video_url\"\\s*:\\s*\"(https?://[^\"]+)\"",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
        if (m2.find()) return m2.group(1);
        return null;
    }

    // 从智谱 images/generations 响应中提取图片 URL
    private static String extractImageUrl(String json) {
        if (json == null || json.isEmpty()) return null;
        // 优先匹配带扩展名的直链
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"url\"\\s*:\\s*\"(https?://[^\"]+\\.(?:png|jpg|jpeg|webp)[^\"]*)\"",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
        if (m.find()) return m.group(1);
        // 兼容不带扩展名的直链
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
            "\"url\"\\s*:\\s*\"(https?://[^\"]+)\"",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
        if (m2.find()) return m2.group(1);
        // b64_json 兜底
        java.util.regex.Matcher m3 = java.util.regex.Pattern.compile(
            "\"b64_json\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").matcher(json);
        if (m3.find()) return "data:image/png;base64," + m3.group(1);
        return null;
    }

    // 与 postJson 类似，但返回完整原始响应（用于图片生成）
    private static String postJsonRaw(String endpoint, String key, String body) {
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        int code = -1;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                try {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    HttpLogger.log("POST", endpoint, code, System.currentTimeMillis() - start, ex.getMessage());
                    return null;
                }
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            HttpLogger.log("POST", endpoint, code, System.currentTimeMillis() - start, null);
            return sb.toString();
        } catch (Throwable e) {
            HttpLogger.log("POST", endpoint, code, System.currentTimeMillis() - start, e.getMessage());
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    // ======================== 内部工具 ========================
    private static String safe(String s) { return (s == null) ? "（未提供）" : s; }

    private static RecResult parseRecResult(String jsonText) {
        if (jsonText == null || jsonText.isEmpty()) return null;
        try {
            RecResult r = new RecResult();
            r.soundName      = extractJsonField(jsonText, "soundName");
            r.shortReason    = extractJsonField(jsonText, "shortReason");
            r.detailedReason = extractJsonField(jsonText, "detailedReason");
            r.recipe         = extractJsonField(jsonText, "recipe");
            r.recipeReason   = extractJsonField(jsonText, "recipeReason");
            if (r.soundName == null || r.soundName.isEmpty()) return null;
            // 将大模型返回的 soundName 规范化到 SoundStore.DEFAULT_NAMES
            r.soundName = SoundStore.matchBuiltinName(r.soundName);
            if (r.shortReason == null || r.shortReason.isEmpty())
                r.shortReason = "根据你现在的状态推荐「" + r.soundName + "」";
            if (r.detailedReason == null) r.detailedReason = "";
            if (r.recipe == null) r.recipe = "";
            if (r.recipeReason == null) r.recipeReason = "";
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static CreativeSound parseCreativeSound(String jsonText, String userInput) {
        if (jsonText == null || jsonText.isEmpty()) return null;
        try {
            CreativeSound r = new CreativeSound();
            r.name        = extractJsonField(jsonText, "name");
            r.description = extractJsonField(jsonText, "description");
            r.recipe      = extractJsonField(jsonText, "recipe");
            r.reason      = extractJsonField(jsonText, "reason");
            if (r.name == null || r.name.isEmpty()) return null;
            if (r.description == null) r.description = "为你定制的一段环境音";
            if (r.recipe == null || r.recipe.isEmpty()) r.recipe = "rain:0.6,wind:0.4";
            if (r.reason == null || r.reason.isEmpty()) {
                if (userInput != null && !userInput.isEmpty()) {
                    r.reason = "你说「" + userInput + "」，据此设计了「" + r.name + "」。配方以 " + r.recipe + " 组合，呈现一种柔和自然的氛围。";
                } else {
                    r.reason = "根据当前时间与状态设计；配方以 " + r.recipe + " 组合，呈现一种柔和自然的氛围。";
                }
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    // 从文本 JSON 中提取字段（宽松解析，不依赖完整 JSON 库）
    private static String extractJsonField(String json, String field) {
        String marker = "\"" + field + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) return null;
        int j = colon + 1;
        while (j < json.length() && (json.charAt(j) == ' ' || json.charAt(j) == '\t'
               || json.charAt(j) == '\n' || json.charAt(j) == '\r' || json.charAt(j) == '"')) j++;
        if (j >= json.length()) return null;
        // 如果是字符串，读到未转义的 " 为止
        StringBuilder out = new StringBuilder();
        boolean inEscape = false;
        // 如果上一个 while 跳过了前引号，这里已经处于字符串内容
        // 为了正确处理两种入口（有/无 "），重新判断：
        if (json.charAt(colon + 1) == '"' || json.charAt(colon + 1) == '\''
            || Character.isWhitespace(json.charAt(colon + 1))) {
            // 字符串：从第一个 " 之后开始读
            int start = json.indexOf('"', colon);
            if (start < 0) return null;
            j = start + 1;
            while (j < json.length()) {
                char c = json.charAt(j);
                if (inEscape) {
                    if (c == 'n') out.append('\n');
                    else if (c == 't') out.append('\t');
                    else if (c == 'r') out.append('\r');
                    else if (c == '"') out.append('"');
                    else if (c == '\\') out.append('\\');
                    else out.append(c);
                    inEscape = false;
                } else if (c == '\\') {
                    inEscape = true;
                } else if (c == '"') {
                    break;
                } else {
                    out.append(c);
                }
                j++;
            }
        } else {
            // 数字/布尔 —— 读到逗号或大括号
            while (j < json.length()) {
                char c = json.charAt(j);
                if (c == ',' || c == '}' || c == ']') break;
                out.append(c);
                j++;
            }
        }
        return out.toString().trim();
    }

    private static RecResult fallbackRecResult(String userInput, String season) {
        RecResult r = new RecResult();
        if (userInput != null && !userInput.isEmpty()) {
            if (containsAny(userInput, "雨", "下雨", "雨声")) {
                r.soundName = "雨声";
                r.shortReason = "你提到想听听下雨，给你一段雨声陪你。";
                r.recipe = "rain:0.8,wind:0.2";
                r.recipeReason = "你提到了『雨』，配方以 rain 雨声为主，加少量 wind 风声制造风吹雨打感。";
                r.detailedReason = "根据你的描述，你希望听到下雨的感觉。雨声（rain）是主成分，再混入一点风声（wind）让场景更自然。时间、季节、天气辅助确认这是一个适合待在室内听雨的时刻。";
                return r;
            }
            if (containsAny(userInput, "海", "浪", "沙滩")) {
                r.soundName = "海浪";
                r.shortReason = "你提到了大海，给你一段海浪声。";
                r.recipe = "ocean:0.8,wind:0.2";
                r.recipeReason = "你提到了『海』，配方以 ocean 海浪为主；少量 wind 风声模拟海风。";
                r.detailedReason = "根据对话，你表达了对大海的向往，直接按你的意愿切换到海浪声。";
                return r;
            }
            if (containsAny(userInput, "睡", "困", "累", "眠", "睡不着", "失眠")) {
                r.soundName = "白噪音";
                r.shortReason = "助眠时间到，纯净白噪音帮你入睡。";
                r.recipe = "white:0.6,pink:0.3,brown:0.1";
                r.recipeReason = "你说想睡，混合 white/pink/brown 三色噪音，给你最稳定的助眠背景。";
                r.detailedReason = "你提到睡不着。稳定的白噪音最适合助眠，帮你屏蔽环境杂音，平滑入睡。";
                return r;
            }
            if (containsAny(userInput, "火", "壁炉", "温暖")) {
                r.soundName = "雨声";
                r.shortReason = "给你温暖的噼啪声。";
                r.recipe = "fire:0.7,brown:0.3";
                r.recipeReason = "你提到温暖，fire 壁炉噼啪声最合适；少量 brown 提供低沉背景。";
                r.detailedReason = "你提到想要温暖感，壁炉的噼啪声很有居家感。";
                return r;
            }
        }
        // 纯兜底
        r.soundName = "雨声";
        r.shortReason = "现在这个状态，雨声最合适。";
        r.recipe = "rain:0.8,wind:0.2";
        r.recipeReason = "默认以雨声为主，给你最通用的放松感。";
        r.detailedReason = "基于通用规则给出推荐，建议安装最新版本以获得更准确的个性化推荐。";
        return r;
    }

    private static boolean containsAny(String src, String... keys) {
        if (src == null) return false;
        for (String k : keys) if (src.contains(k)) return true;
        return false;
    }

    // ======================== HTTP POST JSON ========================
    private static String postJson(String endpoint, String key, String body, boolean isOpenRouter) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(40000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                try {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    return null;
                }
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String resp = sb.toString();
            return extractFirstContent(resp);
        } catch (Throwable e) {
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    // 从 OpenAI 兼容响应体中取出 assistant 的第一条 content
    private static String extractFirstContent(String json) {
        if (json == null || json.isEmpty()) return null;
        String marker = "\"content\":\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            // 兼容 content 后有空格
            marker = "\"content\": \"";
            idx = json.indexOf(marker);
        }
        if (idx < 0) return null;
        int start = idx + marker.length();
        StringBuilder out = new StringBuilder();
        boolean inEsc = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inEsc) {
                if (c == 'n') out.append('\n');
                else if (c == 't') out.append('\t');
                else if (c == 'r') out.append('\r');
                else if (c == '"') out.append('"');
                else if (c == '\\') out.append('\\');
                else out.append(c);
                inEsc = false;
            } else if (c == '\\') {
                inEsc = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(" ");
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
