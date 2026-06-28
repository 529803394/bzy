package com.example.helloworld;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台任务管理
 * - 每个声音(soundId)维护一个任务列表
 * - 任务持久化到 SharedPreferences
 * - 支持：添加、删除、更新状态、按 soundId 查询
 *
 * 任务类型：
 * - TYPE_VIDEO: 背景视频生成任务
 *
 * 任务状态：
 * - STATUS_PENDING: 等待中（刚提交）
 * - STATUS_PROCESSING: 处理中
 * - STATUS_SUCCESS: 成功
 * - STATUS_FAILED: 失败
 * - STATUS_TIMEOUT: 超时
 */
public class TaskStore {

    public static final String TYPE_VIDEO = "video";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_TIMEOUT = "timeout";

    public static class Task {
        public String taskId;
        public String type;
        public String status;
        public String result;   // 任务结果（视频URL等）
        public String error;    // 错误信息
        public String soundId;  // 关联的声音ID
        public long createTime;
        public long updateTime;
        public String title;    // 展示用标题

        public Task(String taskId, String type, String soundId) {
            this.taskId = taskId;
            this.type = type;
            this.soundId = soundId;
            this.status = STATUS_PENDING;
            this.result = null;
            this.error = null;
            this.createTime = System.currentTimeMillis();
            this.updateTime = System.currentTimeMillis();
            this.title = type.equals(TYPE_VIDEO) ? "背景视频生成" : "后台任务";
        }

        public String getStatusText() {
            switch (status) {
                case STATUS_PENDING: return "等待中";
                case STATUS_PROCESSING: return "处理中";
                case STATUS_SUCCESS: return "已完成";
                case STATUS_FAILED: return "失败";
                case STATUS_TIMEOUT: return "超时";
                default: return status;
            }
        }

        public boolean isFinished() {
            return STATUS_SUCCESS.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_TIMEOUT.equals(status);
        }
    }

    private static final String PREFS = "task_store";
    private static final String KEY_TASKS = "tasks";

    private static List<Task> allTasks = null;

    private static synchronized void loadAll(Context ctx) {
        if (allTasks != null) return;
        allTasks = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_TASKS, "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Task t = new Task(
                    obj.optString("taskId", ""),
                    obj.optString("type", ""),
                    obj.optString("soundId", "")
                );
                t.status = obj.optString("status", STATUS_PENDING);
                t.result = optNullable(obj, "result");
                t.error = optNullable(obj, "error");
                t.createTime = obj.optLong("createTime", 0);
                t.updateTime = obj.optLong("updateTime", 0);
                t.title = optNullable(obj, "title");
                if (t.title == null || t.title.isEmpty()) {
                    t.title = t.type.equals(TYPE_VIDEO) ? "背景视频生成" : "后台任务";
                }
                allTasks.add(t);
            }
        } catch (Exception ignored) {}
    }

    private static String optNullable(JSONObject obj, String key) {
        if (!obj.has(key)) return null;
        try {
            String v = obj.getString(key);
            if (v == null || v.isEmpty()) return null;
            return v;
        } catch (Exception e) { return null; }
    }

    private static synchronized void saveAll(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (Task t : allTasks) {
                JSONObject obj = new JSONObject();
                obj.put("taskId", t.taskId);
                obj.put("type", t.type);
                obj.put("status", t.status);
                obj.put("soundId", t.soundId);
                obj.put("result", t.result == null ? "" : t.result);
                obj.put("error", t.error == null ? "" : t.error);
                obj.put("createTime", t.createTime);
                obj.put("updateTime", t.updateTime);
                obj.put("title", t.title == null ? "" : t.title);
                arr.put(obj);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit().putString(KEY_TASKS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized List<Task> getTasksBySoundId(Context ctx, String soundId) {
        loadAll(ctx);
        List<Task> result = new ArrayList<>();
        for (Task t : allTasks) {
            if (t.soundId != null && t.soundId.equals(soundId)) {
                result.add(t);
            }
        }
        // 按创建时间倒序（最新在前）
        java.util.Collections.sort(result, new java.util.Comparator<Task>() {
            @Override public int compare(Task a, Task b) {
                return Long.compare(b.createTime, a.createTime);
            }
        });
        return result;
    }

    public static synchronized void addTask(Context ctx, Task task) {
        loadAll(ctx);
        // 去重：同 taskId 覆盖
        for (int i = 0; i < allTasks.size(); i++) {
            if (allTasks.get(i).taskId.equals(task.taskId)) {
                allTasks.set(i, task);
                saveAll(ctx);
                return;
            }
        }
        allTasks.add(task);
        saveAll(ctx);
    }

    public static synchronized void deleteTask(Context ctx, String taskId) {
        loadAll(ctx);
        for (int i = 0; i < allTasks.size(); i++) {
            if (allTasks.get(i).taskId.equals(taskId)) {
                allTasks.remove(i);
                saveAll(ctx);
                return;
            }
        }
    }

    public static synchronized Task findById(Context ctx, String taskId) {
        loadAll(ctx);
        for (Task t : allTasks) {
            if (t.taskId.equals(taskId)) return t;
        }
        return null;
    }

    public static synchronized void updateTaskStatus(Context ctx, String taskId, String status, String result, String error) {
        loadAll(ctx);
        for (Task t : allTasks) {
            if (t.taskId.equals(taskId)) {
                t.status = status;
                if (result != null) t.result = result;
                if (error != null) t.error = error;
                t.updateTime = System.currentTimeMillis();
                saveAll(ctx);
                return;
            }
        }
    }

    public static synchronized void clearAll(Context ctx) {
        allTasks = new ArrayList<>();
        saveAll(ctx);
    }
}
