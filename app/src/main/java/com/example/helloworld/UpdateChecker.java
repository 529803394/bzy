package com.example.helloworld;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 自动更新检测器（基于阿里云 OSS）
 *
 * 工作流程：
 *   1. GET https://pic98.oss-cn-beijing.aliyuncs.com/bzy/upgrade.txt
 *      -> 读取纯文本版本号，如 "2.5.0"
 *   2. 与当前 App 版本号比较
 *   3. 若有更新，引导用户下载:
 *      https://pic98.oss-cn-beijing.aliyuncs.com/bzy/2.5.0.apk
 *
 * 使用方式：
 *   UpdateChecker.check(MainActivity.this, new UpdateChecker.UpdateCallback() {
 *       public void onResult(UpdateInfo info) { ... }
 *   });
 */
public class UpdateChecker {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 15000;

    /** OSS 基础路径（北京 region，结尾带 /）：
     *  https://pic98.oss-cn-beijing.aliyuncs.com/bzy/
     */
    public static final String OSS_BASE =
            "https://pic98.oss-cn-beijing.aliyuncs.com/bzy/";

    /** 版本描述文件（纯文本，内容就是版本号，如 "2.5.0"） */
    public static final String UPGRADE_URL = OSS_BASE + "upgrade.txt";

    /**
     * 检测是否有新版本
     */
    public static void check(final Activity ctx, final UpdateCallback callback) {
        new AsyncTask<Void, Void, UpdateInfo>() {
            @Override
            protected UpdateInfo doInBackground(Void... params) {
                UpdateInfo info = new UpdateInfo();

                // 1. 从 OSS 读取 upgrade.txt
                String latestVersion = null;
                try {
                    URL url = new URL(UPGRADE_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "BZY-UpdateChecker/1.0");

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        info.errorMessage = "升级服务器异常: HTTP " + code;
                        return info;
                    }

                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    conn.disconnect();

                    latestVersion = sb.toString().trim();
                    if (latestVersion.isEmpty()) {
                        info.errorMessage = "upgrade.txt 内容为空";
                        return info;
                    }
                } catch (Exception e) {
                    info.errorMessage = "检查更新失败: " + e.getMessage();
                    return info;
                }

                // 2. 解析与比较
                info.latestVersion = latestVersion;
                info.downloadUrl = OSS_BASE + "apk/" + latestVersion + ".apk";
                String current = getCurrentVersion(ctx);
                info.currentVersion = current;
                info.isUpdateAvailable = isNewVersion(latestVersion, current);

                return info;
            }

            @Override
            protected void onPostExecute(UpdateInfo info) {
                callback.onResult(info);
            }
        }.execute();
    }

    /**
     * 调起系统浏览器下载新版 APK（最简单、稳定、无需额外权限）
     */
    public static void openDownload(Activity ctx, String apkUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            Toast.makeText(ctx, "正在下载新版 APK...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 获取当前 App 版本号（如 "2.5.0"）
     */
    public static String getCurrentVersion(Activity ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /**
     * 比较版本号：newVersion > current 返回 true
     * 支持 "2.5.0" / "2.5" / "2" 格式
     */
    private static boolean isNewVersion(String newVersion, String current) {
        if (newVersion == null || current == null) return false;
        try {
            String[] n = newVersion.split("\\.");
            String[] c = current.split("\\.");
            int len = Math.max(n.length, c.length);
            for (int i = 0; i < len; i++) {
                int nv = i < n.length ? Integer.parseInt(n[i].replaceAll("[^0-9]", "")) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i].replaceAll("[^0-9]", "")) : 0;
                if (nv > cv) return true;
                if (nv < cv) return false;
            }
        } catch (Exception ignored) { }
        return false;
    }

    // ---------- 回调数据结构 ----------
    public static class UpdateInfo {
        public boolean isUpdateAvailable = false;
        public String latestVersion = null;    // 如 "2.5.0"
        public String currentVersion = null;   // 本地版本
        public String downloadUrl = null;      // APK 下载地址
        public String errorMessage = null;     // 错误原因
    }

    public interface UpdateCallback {
        void onResult(UpdateInfo info);
    }
}
