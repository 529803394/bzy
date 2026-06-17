package com.example.helloworld;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// 统一的白噪音数据管理
public class SoundStore {

    private static final String PREFS = "whitenoise_store_v2";
    private static final String KEY_SOUNDS = "sounds";

    // 内置白噪音
    public static final String[] DEFAULT_NAMES = {"雨声", "海浪", "森林", "风声", "篝火"};
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
    // 更适合做聊天背景的渐变色 (start, end)
    public static final int[][] CHAT_BG_COLORS = {
        {0xFF1a2a4a, 0xFF0a1628}, // 雨声
        {0xFF0a2540, 0xFF164e63}, // 海浪
        {0xFF0b3d2e, 0xFF064e3b}, // 森林
        {0xFF1e293b, 0xFF334155}, // 风声
        {0xFF451a03, 0xFF7c2d12}  // 篝火
    };

    public static class Sound {
        public String id;          // 唯一ID
        public String name;
        public int resId;          // 内置资源ID，0表示自定义
        public String url;         // 自定义URL
        public boolean isCustom;
        public boolean isPinned;
        public boolean isDeleted;  // 是否被删除（移到乐库）
        public String lastMessage; // 最后一条消息（显示在聊天列表上）
        public long lastTime;
        public int themeIndex;     // 主题色索引

        public Sound(String id, String name, int resId) {
            this.id = id;
            this.name = name;
            this.resId = resId;
            this.url = null;
            this.isCustom = false;
            this.isPinned = false;
            this.isDeleted = false;
            this.lastMessage = "";
            this.lastTime = 0;
            this.themeIndex = Math.abs(id.hashCode()) % 5;
        }

        public Sound(String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.resId = 0;
            this.url = url;
            this.isCustom = true;
            this.isPinned = false;
            this.isDeleted = false;
            this.lastMessage = "";
            this.lastTime = 0;
            this.themeIndex = Math.abs(id.hashCode()) % 5;
        }

        public int[] getChatBgColors() {
            if (isCustom) return CHAT_BG_COLORS[themeIndex];
            int idx = -1;
            for (int i = 0; i < DEFAULT_NAMES.length; i++) {
                if (name.equals(DEFAULT_NAMES[i])) { idx = i; break; }
            }
            if (idx < 0) idx = Math.abs(id.hashCode()) % 5;
            return CHAT_BG_COLORS[idx];
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
                            break;
                        }
                    }
                } else {
                    Sound s = new Sound(id, obj.getString("name"), obj.optString("url", ""));
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
                if (s.isCustom) obj.put("url", s.url);
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

    public static synchronized void addCustom(Context ctx, String name, String url) {
        getAll(ctx);
        String id = "custom_" + System.currentTimeMillis();
        Sound s = new Sound(id, name, url);
        sounds.add(s);
        save(ctx);
    }

    public static synchronized void updateCustom(Context ctx, String id, String name, String url) {
        Sound s = findById(ctx, id);
        if (s != null && s.isCustom) {
            s.name = name;
            s.url = url;
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
}
