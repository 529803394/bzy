package com.example.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 自动更新检测器
 * 使用方式：
 *   UpdateChecker.check(MainActivity.this, new UpdateChecker.UpdateCallback() {
 *       public void onResult(UpdateInfo info) {
 *           // info.isUpdateAvailable 为 true 表示有新版本
 *           // info.downloadUrl 为 APK 下载地址
 *       }
 *   });
 *
 *   UpdateInfo 包含：
 *     - isUpdateAvailable (boolean)
 *     - latestVersion     (String，如 "2.2.0")
 *     - downloadUrl       (String，APK 下载地址)
 *     - errorMessage      (String，检测失败时的原因)
 */
public class UpdateChecker {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    /**
     * 从 assets/up.txt 读取 Supabase REST API URL，
     * 请求 {url}?select=*&limit=1&order=id.desc
     * 期望返回 JSON 数组: [{"version":"2.5.0","apk_url":"https://..."}]
     * 固定 URL，无短码可被穷举的风险。
     */
    public static void check(final Activity ctx, final UpdateCallback callback) {
        new AsyncTask<Void, Void, UpdateInfo>() {
            private String apiUrl;

            @Override
            protected UpdateInfo doInBackground(Void... params) {
                UpdateInfo info = new UpdateInfo();

                // 1. 从 assets 读取 Supabase API URL
                String urlBase = null;
                try {
                    InputStream is = ctx.getAssets().open("up.txt");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    urlBase = reader.readLine();
                    reader.close();
                    is.close();
                    if (urlBase == null || urlBase.trim().isEmpty()) {
                        info.errorMessage = "up.txt 为空或格式错误";
                        return info;
                    }
                    urlBase = urlBase.trim();
                } catch (Exception e) {
                    info.errorMessage = "无法读取 up.txt: " + e.getMessage();
                    return info;
                }

                // 构造查询最新一条记录的 URL（按 id 倒序取第一条）
                apiUrl = urlBase + "?select=*&limit=1&order=id.desc";

                // 2. GET 请求 Supabase REST API
                String raw = null;
                try {
                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("apikey", getAnonKey(urlBase));
                    conn.setRequestProperty("Authorization", "Bearer " + getAnonKey(urlBase));
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("User-Agent", "WhiteNoise-Update/1.0");
                    conn.setRequestProperty("Prefer", "count=none");

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        raw = sb.toString();
                    } else {
                        info.errorMessage = "网络响应异常: " + code;
                        return info;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    info.errorMessage = "网络请求失败: " + e.getMessage();
                    return info;
                }

                if (raw == null || raw.isEmpty() || raw.equals("[]") || raw.equals("[null]")) {
                    info.errorMessage = "服务器返回内容为空";
                    return info;
                }

                // 3. 解析 JSON 数组 [{"version":"...","apk_url":"..."}]
                String versionStr = null;
                String apkUrl = null;
                try {
                    // 简单解析：找第一个 "version":"..." 和 "apk_url":"..."
                    versionStr = extractJsonString(raw, "version");
                    apkUrl = extractJsonString(raw, "apk_url");

                    // 兼容 "v": "2.5.0" 格式
                    if (versionStr == null) versionStr = extractJsonString(raw, "v");
                    if (apkUrl == null) apkUrl = extractJsonString(raw, "u");
                    if (apkUrl == null) apkUrl = extractJsonString(raw, "url");
                } catch (Exception e) {
                    info.errorMessage = "JSON 解析失败: " + e.getMessage();
                    return info;
                }

                if (versionStr == null || versionStr.isEmpty() || apkUrl == null || apkUrl.isEmpty()) {
                    info.errorMessage = "数据格式错误，version 或 apk_url 为空";
                    return info;
                }

                info.latestVersion = versionStr.trim();
                info.downloadUrl = apkUrl.trim();
                info.isUpdateAvailable = isNewVersion(info.latestVersion, getCurrentVersion(ctx));

                return info;
            }

            @Override
            protected void onPostExecute(UpdateInfo info) {
                callback.onResult(info);
            }
        }.execute();
    }

    /**
     * 从 URL 中提取 Supabase anon key（格式如 https://xxx.supabase.co/rest/v1/xxx?key=yyy）
     * 兼容纯 URL（从 header apikey）或 URL?key=yyy 格式
     */
    private static String getAnonKey(String urlBase) {
        // 尝试从 URL 末尾 ?key= 中读取
        int qIdx = urlBase.indexOf('?');
        if (qIdx >= 0) {
            String qs = urlBase.substring(qIdx + 1);
            for (String pair : qs.split("&")) {
                int eq = pair.indexOf('=');
                if (eq >= 0 && "key".equals(pair.substring(0, eq).trim())) {
                    return pair.substring(eq + 1).trim();
                }
            }
        }
        // 默认返回空字符串，由调用方从 HTTP header 注入
        return "";
    }

    /**
     * 简单 JSON 字符串提取（不需要第三方库）
     * 查找 "key":"value" 或 "key": "value" 格式
     */
    private static String extractJsonString(String json, String key) {
        String pattern1 = "\"" + key + "\":\"";
        String pattern2 = "\"" + key + "\": \"";
        int start;
        boolean hasSpace = false;
        int idx1 = json.indexOf(pattern1);
        int idx2 = json.indexOf(pattern2);
        if (idx1 >= 0 && (idx2 < 0 || idx1 <= idx2)) {
            start = idx1 + pattern1.length();
            hasSpace = false;
        } else if (idx2 >= 0) {
            start = idx2 + pattern2.length();
            hasSpace = true;
        } else {
            return null;
        }
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\' && end + 1 < json.length()) {
                end += 2; // skip escaped char
                continue;
            }
            if (c == '"') break;
            end++;
        }
        String val = json.substring(start, end);
        return val;
    }

    /**
     * 下载并安装 APK（自动请求安装未知来源权限）
     */
    public static void downloadAndInstall(final Activity ctx, String apkUrl) {
        // 弹窗确认
        new AlertDialog.Builder(ctx)
            .setTitle("发现新版本")
            .setMessage("是否下载并安装新版本？")
            .setPositiveButton("下载更新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startDownload(ctx, apkUrl);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private static void startDownload(Context ctx, String apkUrl) {
        try {
            // 尝试使用系统 DownloadManager
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "whitenoise_update.apk");
            request.allowScanningByMediaScanner();

            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(ctx, "正在下载...", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            // DownloadManager 不可用，fallback 到手动下载
        }

        // fallback：直接用浏览器打开下载
        Toast.makeText(ctx, "正在跳转下载...", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /**
     * 获取当前 App 版本名（如 "2.1.0"）
     */
    public static String getCurrentVersion(Activity ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /**
     * 比较版本号大小，newVersion > current 返回 true
     * 支持 "2.1.0" 这种格式
     */
    private static boolean isNewVersion(String newVersion, String currentVersion) {
        try {
            String[] nParts = newVersion.split("\\.");
            String[] cParts = currentVersion.split("\\.");
            int len = Math.max(nParts.length, cParts.length);
            for (int i = 0; i < len; i++) {
                int n = i < nParts.length ? Integer.parseInt(nParts[i].replaceAll("[^0-9]", "")) : 0;
                int c = i < cParts.length ? Integer.parseInt(cParts[i].replaceAll("[^0-9]", "")) : 0;
                if (n > c) return true;
                if (n < c) return false;
            }
        } catch (Exception e) {
            // 解析失败，保守返回 false
        }
        return false;
    }

    // ---------- 回调结构体 ----------
    public static class UpdateInfo {
        public boolean isUpdateAvailable = false;
        public String latestVersion = null;   // 如 "2.2.0"
        public String downloadUrl = null;     // APK 下载地址
        public String errorMessage = null;    // 检测失败原因
    }

    public interface UpdateCallback {
        void onResult(UpdateInfo info);
    }
}
