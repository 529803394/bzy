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
     * 从 assets/up.txt 读取版本信息 URL（GitHub raw），
     * 再 GET 该 URL 获取 "version:apk_url" 字符串。
     * 这样，用户只需 push 新的 version.txt 到 git 即完成版本更新。
     */
    public static void check(final Activity ctx, final UpdateCallback callback) {
        new AsyncTask<Void, Void, UpdateInfo>() {
            private String versionUrl;

            @Override
            protected UpdateInfo doInBackground(Void... params) {
                UpdateInfo info = new UpdateInfo();
                // 1. 从 assets 读取版本信息 URL
                try {
                    InputStream is = ctx.getAssets().open("up.txt");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    versionUrl = reader.readLine();
                    reader.close();
                    is.close();
                    if (versionUrl == null || versionUrl.trim().isEmpty()) {
                        info.errorMessage = "up.txt 为空或格式错误";
                        return info;
                    }
                    versionUrl = versionUrl.trim();
                } catch (Exception e) {
                    info.errorMessage = "无法读取 up.txt: " + e.getMessage();
                    return info;
                }

                // 2. 从该 URL 获取版本信息 "version:apk_url"
                String raw = null;
                try {
                    URL url = new URL(versionUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.setRequestMethod("GET");
                    // GitHub raw 需要 User-Agent
                    conn.setRequestProperty("User-Agent", "WhiteNoise-Update-Checker");
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

                if (raw == null || raw.isEmpty()) {
                    info.errorMessage = "服务器返回内容为空";
                    return info;
                }

                // 3. 按 ":" 分割解析 "version:apk_url"
                int colonIdx = raw.indexOf(':');
                if (colonIdx < 0) {
                    info.errorMessage = "数据格式错误，未找到冒号分隔符";
                    return info;
                }
                String versionStr = raw.substring(0, colonIdx).trim();
                String apkUrl = raw.substring(colonIdx + 1).trim();

                if (versionStr.isEmpty() || apkUrl.isEmpty()) {
                    info.errorMessage = "版本号或下载地址为空";
                    return info;
                }

                info.latestVersion = versionStr;
                info.downloadUrl = apkUrl;
                info.isUpdateAvailable = isNewVersion(versionStr, getCurrentVersion(ctx));

                return info;
            }

            @Override
            protected void onPostExecute(UpdateInfo info) {
                callback.onResult(info);
            }
        }.execute();
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
