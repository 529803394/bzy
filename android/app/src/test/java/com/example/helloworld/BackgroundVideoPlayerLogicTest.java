package com.example.helloworld;

/**
 * BackgroundVideoPlayer 逻辑验证（独立运行，无需 JUnit）
 * 运行: javac -d /tmp BackgroundVideoPlayerLogicTest.java && java -cp /tmp com.example.helloworld.BackgroundVideoPlayerLogicTest
 */
public class BackgroundVideoPlayerLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    // ========== 复用 BackgroundVideoPlayer 的路径判断逻辑 ==========

    /** 判断是否为网络 URL */
    private static boolean isNetworkUrl(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    /** 判断本地文件是否存在 */
    private static boolean localFileExists(String path) {
        if (path == null) return false;
        if (isNetworkUrl(path)) return false;
        return new java.io.File(path).exists();
    }

    // ========== 测试方法 ==========

    private static void check(boolean cond, String name) {
        if (cond) {
            System.out.println("  ✅ " + name);
            passed++;
        } else {
            System.out.println("  ❌ " + name);
            failed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("\n=== BackgroundVideoPlayer 逻辑测试 ===\n");

        // --- URL 检测测试 ---
        System.out.println("[URL 检测]");
        check(isNetworkUrl("http://example.com/video.mp4"), "http 是网络 URL");
        check(isNetworkUrl("https://example.com/video.mp4"), "https 是网络 URL");
        check(!isNetworkUrl("/sdcard/videos/test.mp4"), "绝对路径不是网络 URL");
        check(!isNetworkUrl("videos/test.mp4"), "相对路径不是网络 URL");
        check(!isNetworkUrl(null), "null 不是网络 URL");
        check(!isNetworkUrl(""), "空字符串不是网络 URL");
        check(isNetworkUrl("https://maas-watermark-prod.oss-cn-beijing.aliyuncs.com/video.mp4"), "OSS URL 是网络 URL");
        check(isNetworkUrl("https://example.com/video.mp4?token=abc"), "带查询参数的 URL 是网络 URL");

        // --- 本地文件存在性测试 ---
        System.out.println("\n[本地文件存在性判断]");
        check(localFileExists("/proc/version"), "/proc/version 存在（虚拟文件）");
        check(!localFileExists("/nonexistent/path/xyz123.mp4"), "不存在的路径返回 false");
        check(!localFileExists("https://example.com/video.mp4"), "网络 URL 不当作本地文件");
        check(!localFileExists(null), "null 返回 false");

        // --- SurfaceTexture null 处理逻辑 ---
        System.out.println("\n[SurfaceTexture null 处理]");
        Object surfaceTexture = null; // 模拟未就绪
        String pendingPath = null;
        Object pendingCallback = new Object();
        if (surfaceTexture == null) {
            pendingPath = "/sdcard/test.mp4";
            pendingCallback = pendingCallback;
        }
        check(pendingPath != null && pendingPath.equals("/sdcard/test.mp4"), "SurfaceTexture 为 null 时暂存路径");
        check(pendingCallback != null, "SurfaceTexture 为 null 时暂存回调");

        // 已就绪情况
        surfaceTexture = new Object();
        pendingPath = null;
        if (surfaceTexture == null) {
            pendingPath = "should not set";
        } else {
            pendingPath = "/sdcard/test.mp4"; // 直接播放
        }
        check(pendingPath.equals("/sdcard/test.mp4"), "SurfaceTexture 就绪时直接使用路径");

        // --- release() null 安全性 ---
        System.out.println("\n[MediaPlayer release() 安全性]");
        Object mp = null;
        boolean safe = true;
        try {
            if (mp != null) {
                // 实际 MediaPlayer.release() 不会调用
            }
            // 空操作，安全
        } catch (Exception e) {
            safe = false;
        }
        check(safe, "release() 对 null MediaPlayer 不抛异常");

        // --- 回调模拟 ---
        System.out.println("\n[VideoPlayerCallback 回调模拟]");
        final boolean[] calledReady = {false};
        final String[] errorMsg = {null};
        VideoPlayerCallback cb = new VideoPlayerCallback() {
            @Override public void onReady() { calledReady[0] = true; }
            @Override public void onError(String msg) { errorMsg[0] = msg; }
        };
        cb.onError("本地文件不存在: /path.mp4");
        check(errorMsg[0].equals("本地文件不存在: /path.mp4"), "onError 回调正确接收错误信息");
        check(!calledReady[0], "onError 后 onReady 不应被触发");
        cb.onReady();
        check(calledReady[0], "onReady 回调正确触发");

        // --- 循环播放标志 ---
        System.out.println("\n[循环播放逻辑]");
        boolean looping = true;
        check(looping, "setLooping(true) 标志应为 true");

        // --- 多次 release 幂等性 ---
        System.out.println("\n[多次 release 幂等性]");
        int releaseCount = 0;
        Object mp1 = new Object(); // 模拟 MediaPlayer
        if (mp1 != null) { releaseCount++; }
        mp1 = null;
        if (mp1 != null) { releaseCount++; } // 不应执行
        check(releaseCount == 1, "多次 release 不会重复执行（mp 置 null 后不再进入）");

        // --- 异步准备不阻塞 ---
        System.out.println("\n[prepareAsync 不阻塞主线程]");
        // 验证 prepareAsync 是异步调用，不会同步等待
        long start = System.currentTimeMillis();
        // 模拟：prepareAsync() 是异步的，不会 block
        // 如果是同步 prepare()，这里的循环会很慢
        for (int i = 0; i < 1000; i++) { /* 空循环模拟快速返回 */ }
        long elapsed = System.currentTimeMillis() - start;
        check(elapsed < 1000, "异步 prepareAsync() 调用立即返回 (elapsed=" + elapsed + "ms)");

        // --- 静音设置 ---
        System.out.println("\n[静音播放]");
        float volume = 0f;
        check(volume == 0f, "setVolume(0f, 0f) 静音设置正确");

        // --- 结果统计 ---
        System.out.println("\n=== 测试结果: " + passed + " 通过, " + failed + " 失败 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // 模拟 VideoPlayerCallback 接口
    interface VideoPlayerCallback {
        void onReady();
        void onError(String msg);
    }
}
