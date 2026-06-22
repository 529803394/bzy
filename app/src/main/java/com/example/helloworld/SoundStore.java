package com.example.helloworld;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

// 统一的白噪音数据管理
public class SoundStore {

    private static final String PREFS = "whitenoise_store_v2";
    private static final String KEY_SOUNDS = "sounds";

    // 内置白噪音
    public static final String[] DEFAULT_NAMES = {"雨声", "海浪", "森林", "风声", "篝火"};
    public static final String[] DEFAULT_NAMES_CN = DEFAULT_NAMES; // 给 AI.java 引用

    // 把大模型返回的文字匹配到最接近的内置白噪音名
    public static String matchBuiltinName(String name) {
        if (name == null || name.isEmpty()) return DEFAULT_NAMES[0];
        String n = name.trim();
        for (String cn : DEFAULT_NAMES) {
            if (cn.equals(n)) return cn;
            if (n.contains(cn)) return cn;
            if (cn.contains(n)) return cn;
        }
        // 关键词匹配
        if (containsAny(n, "雨", "rain")) return "雨声";
        if (containsAny(n, "海", "浪", "ocean", "wave")) return "海浪";
        if (containsAny(n, "森", "林", "forest", "鸟")) return "森林";
        if (containsAny(n, "风", "wind")) return "风声";
        if (containsAny(n, "火", "篝", "burn", "fire")) return "篝火";
        // 最兜底
        return DEFAULT_NAMES[0];
    }
    private static boolean containsAny(String src, String... keys) {
        if (src == null) return false;
        String lower = src.toLowerCase();
        for (String k : keys) if (lower.contains(k.toLowerCase())) return true;
        return false;
    }
    public static final int[] DEFAULT_RES = {
        R.raw.rain, R.raw.ocean, R.raw.forest, R.raw.wind, R.raw.campfire
    };
    // 每个内置白噪音对应的主题色（渐变色）
    public static final int[][] DEFAULT_COLORS = {
        {0xFF1a3a5c, 0xFF0d7377}, // 雨声: 深蓝→青绿
        {0xFF0d3b66, 0xFFfaf0ca}, // 海浪: 不用于深色背景，见下
        {0xFF0b3d2e, 0xFF3b7a57}, // 森林: 深绿
        {0xFF334155, 0xFF475569}, // 风声: 灰蓝
        {0xFF7c2d12, 0xFFea580c}  // 篝火: 橙红
    };
    // 更适合做聊天背景的渐变色 (start, end) — 要高对比、鲜艳，让动画明显
    public static final int[][] CHAT_BG_COLORS = {
        {0xFF22d3ee, 0xFF0f172a}, // 雨声: 亮青 → 深海
        {0xFF38bdf8, 0xFF1e40af}, // 海浪: 天空蓝 → 深蓝
        {0xFF4ade80, 0xFF065f46}, // 森林: 亮绿 → 深林绿
        {0xFF94a3b8, 0xFF1e293b}, // 风声: 银灰 → 深灰蓝
        {0xFFfb923c, 0xFF7c2d12}  // 篝火: 亮橙 → 深橙红
    };
    // 白天模式用的浅色系(不那么深，让文字更清楚)
    public static final int[][] CHAT_BG_COLORS_LIGHT = {
        {0xFFbae6fd, 0xFF7dd3fc, 0xFF0ea5e9}, // 雨声: 浅蓝系
        {0xFFfef3c7, 0xFFfde68a, 0xFF38bdf8}, // 海浪: 沙金→浅水蓝
        {0xFFdcfce7, 0xFF86efac, 0xFF16a34a}, // 森林: 浅绿→深绿
        {0xFFf1f5f9, 0xFFcbd5e1, 0xFF64748b}, // 风声: 浅灰系
        {0xFFfed7aa, 0xFFfdba74, 0xFFea580c}  // 篝火: 浅橙→深橙
    };

    public static class Sound {
        public String id;              // 唯一ID
        public String name;
        public int resId;              // 内置资源ID，0表示自定义
        public String url;             // 自定义/网络音频URL
        public String bgImageUrl;      // 背景图片网络URL
        public String bgVideoUrl;      // 背景视频网络URL（智谱AI生成）
        public String localPath;       // 音频本地缓存路径（播放优先用此）
        public String bgImageLocalPath; // 背景图片本地缓存路径
        public String bgVideoLocalPath; // 背景视频本地缓存路径
        public boolean isCustom;
        public boolean isPinned;
        public boolean isDeleted;      // 是否被删除（移到乐库）
        public boolean isNetwork;      // 是否来自网络音乐列表
        public long fileSize;          // 本地缓存文件大小（字节）
        public String lastMessage;
        public long lastTime;
        public int themeIndex;

        public Sound(String id, String name, int resId) {
            this.id = id;
            this.name = name;
            this.resId = resId;
            this.url = null;
            this.bgImageUrl = null;
            this.bgVideoUrl = null;
            this.localPath = null;
            this.bgImageLocalPath = null;
            this.bgVideoLocalPath = null;
            this.isCustom = false;
            this.isPinned = false;
            this.isDeleted = false;
            this.isNetwork = false;
            this.fileSize = 0;
            this.lastMessage = "";
            this.lastTime = 0;
            this.themeIndex = Math.abs(id.hashCode()) % 5;
        }

        public Sound(String id, String name, String url, String bgImageUrl) {
            this.id = id;
            this.name = name;
            this.resId = 0;
            this.url = url;
            this.bgImageUrl = bgImageUrl;
            this.localPath = null;
            this.bgImageLocalPath = null;
            this.isCustom = true;
            this.isPinned = false;
            this.isDeleted = false;
            this.isNetwork = false;
            this.fileSize = 0;
            this.lastMessage = "";
            this.lastTime = 0;
            this.themeIndex = Math.abs(id.hashCode()) % 5;
        }

        // 网络音乐专用构造（支持 bgImageUrl）
        public static Sound fromNetwork(String url, String name) {
            return fromNetwork(url, name, null);
        }

        public static Sound fromNetwork(String url, String name, String bgImageUrl) {
            Sound s = new Sound("net_" + md5(url), name, 0);
            s.url = url;
            s.bgImageUrl = bgImageUrl;
            s.isNetwork = true;
            s.isCustom = false;
            s.localPath = null;
            s.bgImageLocalPath = null;
            s.fileSize = 0;
            return s;
        }

        private static String md5(String input) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return String.valueOf(input.hashCode());
            }
        }

        public int getSoundIndex() {
            if (isCustom) return Math.abs(id.hashCode()) % 5;
            for (int i = 0; i < DEFAULT_NAMES.length; i++) {
                if (name.equals(DEFAULT_NAMES[i])) return i;
            }
            return Math.abs(id.hashCode()) % 5;
        }

        public int[] getChatBgColors() {
            return CHAT_BG_COLORS[getSoundIndex()];
        }
        public int[] getChatBgColorsLight() {
            return CHAT_BG_COLORS_LIGHT[getSoundIndex()];
        }
    }

    public static class Message {
        public long time;
        public String text;
        public boolean fromUser; // true=用户发送的白气泡, false=系统回复的绿气泡

        public Message(String text, boolean fromUser) {
            this.text = text;
            this.fromUser = fromUser;
            this.time = System.currentTimeMillis();
        }
    }

    private static List<Sound> sounds = null;

    public static synchronized List<Sound> getAll(Context ctx) {
        if (sounds == null) load(ctx);
        return sounds;
    }

    public static Sound findById(Context ctx, String id) {
        for (Sound s : getAll(ctx)) if (s.id.equals(id)) return s;
        return null;
    }

    // 首页显示的：未删除的
    public static List<Sound> getHomeList(Context ctx) {
        List<Sound> result = new ArrayList<>();
        List<Sound> pinned = new ArrayList<>();
        List<Sound> normal = new ArrayList<>();
        for (Sound s : getAll(ctx)) {
            if (!s.isDeleted) {
                if (s.isPinned) pinned.add(s);
                else normal.add(s);
            }
        }
        result.addAll(pinned);
        result.addAll(normal);
        return result;
    }

    // 乐库显示的：被删除的 + 所有内置
    // 其实更简单：所有已存在的，不管是否删除，都显示（删除的就是"在乐库中"）
    // 这里简化：显示所有，isDeleted的做标记
    public static List<Sound> getLibraryList(Context ctx) {
        return getAll(ctx);
    }

    private static synchronized void load(Context ctx) {
        sounds = new ArrayList<>();
        // 添加内置
        for (int i = 0; i < DEFAULT_NAMES.length; i++) {
            sounds.add(new Sound("built_in_" + i, DEFAULT_NAMES[i], DEFAULT_RES[i]));
        }
        // 读取自定义和状态
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_SOUNDS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.getString("id");
                if (obj.optBoolean("isBuiltIn")) {
                    // 更新内置的状态
                    for (Sound s : sounds) {
                        if (s.id.equals(id)) {
                            s.isPinned = obj.optBoolean("isPinned", false);
                            s.isDeleted = obj.optBoolean("isDeleted", false);
                            s.lastMessage = obj.optString("lastMessage", "");
                            s.lastTime = obj.optLong("lastTime", 0);
                            String bg = obj.optString("bgImageUrl", null);
                            if (bg != null && !bg.isEmpty()) s.bgImageUrl = bg;
                            break;
                        }
                    }
                } else {
                    String savedUrl = obj.optString("url", "");
                    if (savedUrl != null && savedUrl.isEmpty()) savedUrl = null;
                    String savedBgImage = obj.optString("bgImageUrl", null);
                    if (savedBgImage != null && savedBgImage.isEmpty()) savedBgImage = null;
                    Sound s = new Sound(id, obj.getString("name"),
                        savedUrl == null ? "" : savedUrl,
                        savedBgImage);
                    s.isNetwork = obj.optBoolean("isNetwork", false);
                    // 兼容旧数据：如果 id 以 "net_" 开头，标记为网络音乐
                    if (!s.isNetwork && id != null && id.startsWith("net_")) {
                        s.isNetwork = true;
                    }
                    // 如果 isNetwork=true 且 url 为空，把 isCustom 也设为 true
                    // 这样 save() 时能正确保存 url 字段
                    if (s.isNetwork) s.isCustom = true;
                    s.localPath = obj.optString("localPath", null);
                    if (s.localPath != null && s.localPath.isEmpty()) s.localPath = null;
                    s.bgImageLocalPath = obj.optString("bgImageLocalPath", null);
                    if (s.bgImageLocalPath != null && s.bgImageLocalPath.isEmpty()) s.bgImageLocalPath = null;
                    s.bgVideoUrl = obj.optString("bgVideoUrl", null);
                    if (s.bgVideoUrl != null && s.bgVideoUrl.isEmpty()) s.bgVideoUrl = null;
                    s.bgVideoLocalPath = obj.optString("bgVideoLocalPath", null);
                    if (s.bgVideoLocalPath != null && s.bgVideoLocalPath.isEmpty()) s.bgVideoLocalPath = null;
                    s.isPinned = obj.optBoolean("isPinned", false);
                    s.isDeleted = obj.optBoolean("isDeleted", false);
                    s.lastMessage = obj.optString("lastMessage", "");
                    s.lastTime = obj.optLong("lastTime", 0);
                    sounds.add(s);
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (Sound s : sounds) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("name", s.name);
                obj.put("isBuiltIn", !s.isCustom);
                obj.put("isNetwork", s.isNetwork);
                if (s.isCustom || s.isNetwork) {
                    obj.put("url", s.url == null ? "" : s.url);
                    obj.put("bgImageUrl", s.bgImageUrl == null ? "" : s.bgImageUrl);
                    obj.put("bgVideoUrl", s.bgVideoUrl == null ? "" : s.bgVideoUrl);
                    obj.put("localPath", s.localPath == null ? "" : s.localPath);
                    obj.put("bgImageLocalPath", s.bgImageLocalPath == null ? "" : s.bgImageLocalPath);
                    obj.put("bgVideoLocalPath", s.bgVideoLocalPath == null ? "" : s.bgVideoLocalPath);
                } else {
                    obj.put("bgImageUrl", s.bgImageUrl == null ? "" : s.bgImageUrl);
                    obj.put("bgVideoUrl", s.bgVideoUrl == null ? "" : s.bgVideoUrl);
                    obj.put("bgVideoLocalPath", s.bgVideoLocalPath == null ? "" : s.bgVideoLocalPath);
                }
                obj.put("isPinned", s.isPinned);
                obj.put("isDeleted", s.isDeleted);
                obj.put("lastMessage", s.lastMessage == null ? "" : s.lastMessage);
                obj.put("lastTime", s.lastTime);
                arr.put(obj);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit().putString(KEY_SOUNDS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized void addCustom(Context ctx, String name, String url, String bgImageUrl) {
        getAll(ctx);
        String id = "custom_" + System.currentTimeMillis();
        addCustom(ctx, id, name, url, bgImageUrl);
    }

    public static synchronized void addCustom(Context ctx, String id, String name, String url, String bgImageUrl) {
        addCustom(ctx, id, name, url, bgImageUrl, null);
    }

    public static synchronized void addCustom(Context ctx, String id, String name, String url, String bgImageUrl, String localPath) {
        getAll(ctx);
        Sound s = new Sound(id, name, url == null ? "" : url, bgImageUrl);
        if (localPath != null && !localPath.isEmpty()) {
            s.localPath = localPath;
        }
        sounds.add(s);
        save(ctx);
    }

    public static synchronized void updateCustom(Context ctx, String id, String name, String url, String bgImageUrl) {
        Sound s = findById(ctx, id);
        if (s != null && s.isCustom) {
            s.name = name;
            s.url = url;
            s.bgImageUrl = bgImageUrl;
            save(ctx);
        }
    }

    // 同时支持设置 url（网络地址） 和 localPath（本地已缓存路径）
    public static synchronized void setUrlAndLocalPath(Context ctx, String id, String url, String localPath) {
        Sound s = findById(ctx, id);
        if (s != null) {
            if (url != null) s.url = url;
            if (localPath != null) s.localPath = localPath;
            save(ctx);
        }
    }

    // 判断一个 Sound 是否已下载完成（优先看 localPath）
    public static boolean isDownloaded(Sound s) {
        if (s == null) return false;
        if (s.localPath != null && !s.localPath.isEmpty()) {
            java.io.File f = new java.io.File(s.localPath);
            if (f.exists()) return true;
        }
        // 内置资源 id>0 视为已在本地
        return (s.resId > 0);
    }

    // 返回 sound 实际用于播放的源（localPath 优先，否则 url，否则 null）
    public static String getPlaySource(Sound s) {
        if (s == null) return null;
        if (s.localPath != null && !s.localPath.isEmpty()) {
            java.io.File f = new java.io.File(s.localPath);
            if (f.exists()) return s.localPath;
        }
        if (s.url != null && !s.url.isEmpty()) return s.url;
        if (s.resId > 0) return "#res:" + s.resId;
        return null;
    }

    // 设置本地缓存路径（下载完成后调用）
    public static synchronized void setLocalPath(Context ctx, String id, String localPath) {
        Sound s = findById(ctx, id);
        if (s != null && s.isCustom) {
            s.localPath = localPath;
            save(ctx);
        }
    }

    // 设置背景图片 URL（内置和自定义白噪音都支持）
    public static synchronized void setBgImageUrl(Context ctx, String id, String bgImageUrl) {
        Sound s = findById(ctx, id);
        if (s != null) {
            s.bgImageUrl = bgImageUrl;
            save(ctx);
        }
    }

    // 设置背景视频 URL（智谱AI生成后调用）
    public static synchronized void setBgVideoUrl(Context ctx, String id, String bgVideoUrl) {
        Sound s = findById(ctx, id);
        if (s != null) {
            s.bgVideoUrl = bgVideoUrl;
            save(ctx);
        }
    }

    // 设置背景视频本地缓存路径（下载完成后调用）
    public static synchronized void setBgVideoLocalPath(Context ctx, String id, String bgVideoLocalPath) {
        Sound s = findById(ctx, id);
        if (s != null) {
            s.bgVideoLocalPath = bgVideoLocalPath;
            save(ctx);
        }
    }

    // 重命名任意声音（内置或自定义）
    public static synchronized void rename(Context ctx, String id, String newName) {
        Sound s = findById(ctx, id);
        if (s != null && newName != null && !newName.trim().isEmpty()) {
            s.name = newName.trim();
            save(ctx);
        }
    }

    public static synchronized void deleteCustom(Context ctx, String id) {
        for (int i = 0; i < sounds.size(); i++) {
            if (sounds.get(i).id.equals(id) && sounds.get(i).isCustom) {
                sounds.remove(i);
                save(ctx);
                return;
            }
        }
    }

    public static synchronized int bulkDelete(Context ctx, java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int removed = 0;
        // 反向遍历避免索引错位
        for (int i = sounds.size() - 1; i >= 0; i--) {
            Sound s = sounds.get(i);
            if (ids.contains(s.id)) {
                if (s.isCustom) {
                    sounds.remove(i);
                    removed++;
                } else {
                    s.isDeleted = true;
                    removed++;
                }
            }
        }
        if (removed > 0) save(ctx);
        return removed;
    }

    public static synchronized void togglePin(Context ctx, String id) {
        Sound s = findById(ctx, id);
        if (s != null) { s.isPinned = !s.isPinned; save(ctx); }
    }

    public static synchronized void markDeleted(Context ctx, String id, boolean deleted) {
        Sound s = findById(ctx, id);
        if (s != null) { s.isDeleted = deleted; save(ctx); }
    }

    public static synchronized void setLastMessage(Context ctx, String id, String msg) {
        Sound s = findById(ctx, id);
        if (s != null) {
            s.lastMessage = msg;
            s.lastTime = System.currentTimeMillis();
            save(ctx);
        }
    }

    // -------- 导入 / 导出自定义白噪音 --------
    // 导出：把当前所有自定义白噪音序列化为 JSON 字符串（不含内置）
    public static String exportAllToJson(Context ctx) {
        List<SoundStore.Sound> customs = new ArrayList<>();
        for (Sound s : getAll(ctx)) {
            if (s.isCustom) customs.add(s);
        }
        try {
            JSONArray arr = new JSONArray();
            for (Sound s : customs) {
                JSONObject obj = new JSONObject();
                obj.put("name", s.name);
                obj.put("url", s.url == null ? "" : s.url);
                obj.put("bgImageUrl", s.bgImageUrl == null ? "" : s.bgImageUrl);
                arr.put(obj);
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("sounds", arr);
            return root.toString(2);
        } catch (Exception e) {
            return null;
        }
    }

    // 导入：从 JSON 字符串批量添加自定义白噪音（已存在的跳过，按 name 判断去重）
    public static int importFromJson(Context ctx, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("sounds");
            if (arr == null || arr.length() == 0) return 0;
            getAll(ctx); // 确保 sounds 列表已加载
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "").trim();
                String url = obj.optString("url", "").trim();
                String bgUrl = obj.optString("bgImageUrl", "").trim();
                if (name.isEmpty() || url.isEmpty()) continue;
                // 跳过重复（按 name 判断）
                boolean exists = false;
                for (Sound s : sounds) {
                    if (s.isCustom && s.name.equals(name)) { exists = true; break; }
                }
                if (exists) continue;
                Sound s = new Sound("custom_" + System.currentTimeMillis() + "_" + i,
                    name, url, bgUrl.isEmpty() ? null : bgUrl);
                sounds.add(s);
                count++;
            }
            if (count > 0) save(ctx);
            return count;
        } catch (Exception e) {
            return -1; // 解析失败
        }
    }

    // 消息持久化（每个Sound一个JSON文件）
    public static List<Message> loadMessages(Context ctx, String soundId) {
        List<Message> result = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences("wn_msgs_" + soundId, Context.MODE_PRIVATE);
            String json = sp.getString("msgs", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Message m = new Message(obj.optString("text", ""), obj.optBoolean("u", true));
                m.time = obj.optLong("t", System.currentTimeMillis());
                result.add(m);
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static void saveMessages(Context ctx, String soundId, List<Message> msgs) {
        try {
            JSONArray arr = new JSONArray();
            for (Message m : msgs) {
                JSONObject obj = new JSONObject();
                obj.put("text", m.text);
                obj.put("u", m.fromUser);
                obj.put("t", m.time);
                arr.put(obj);
            }
            ctx.getSharedPreferences("wn_msgs_" + soundId, Context.MODE_PRIVATE)
               .edit().putString("msgs", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    // 简易时间格式化
    public static String formatTime(long t) {
        if (t <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - t;
        if (diff < 60000) return "刚刚";
        if (diff < 3600000) return (diff / 60000) + "分钟前";
        if (diff < 86400000) return (diff / 3600000) + "小时前";
        if (diff < 86400000L * 7) return (diff / 86400000) + "天前";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd");
        return sdf.format(new java.util.Date(t));
    }

    // ========== 网络音乐缓存管理 ==========
    private static final String NET_CACHE_PREFS = "net_music_cache";
    private static final String NET_CACHE_KEY_PREFIX = "cache_";

    // 获取缓存目录（应用私有目录）
    public static java.io.File getNetworkCacheDir(Context ctx) {
        java.io.File dir = new java.io.File(ctx.getFilesDir(), "music_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // 根据 URL 生成缓存文件名
    public static String getCacheFileName(String url) {
        String md5val;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            md5val = sb.toString();
        } catch (Exception e) {
            md5val = String.valueOf(url.hashCode());
        }
        return "net_" + md5val + ".mp3";
    }

    // 检查网络音乐是否已缓存（更新 localPath 和 fileSize）
    public static void checkNetworkCache(Context ctx, Sound s) {
        if (!s.isNetwork || s.url == null) return;
        String fileName = getCacheFileName(s.url);
        java.io.File cached = new java.io.File(getNetworkCacheDir(ctx), fileName);
        if (cached.exists()) {
            s.localPath = cached.getAbsolutePath();
            s.fileSize = cached.length();
        } else {
            s.localPath = null;
            s.fileSize = 0;
        }
    }

    // 批量检查缓存（对网络列表用）
    public static void checkNetworkCacheBatch(Context ctx, List<Sound> list) {
        for (Sound s : list) checkNetworkCache(ctx, s);
    }

    // 格式化为可读文件大小
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // 通过 HTTP HEAD 请求获取远程文件大小（字节），失败返回 -1
    public static long getRemoteFileSize(String url) {
        if (url == null || url.isEmpty()) return -1;
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                long cl = conn.getContentLengthLong();
                if (cl > 0) return cl;
                // 兼容部分服务器返回 string 形式的 Content-Length
                String clStr = conn.getHeaderField("Content-Length");
                if (clStr != null) {
                    try { return Long.parseLong(clStr.trim()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
        }
        return -1;
    }
}
