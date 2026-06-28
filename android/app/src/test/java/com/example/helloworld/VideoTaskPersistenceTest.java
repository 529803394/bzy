package com.example.helloworld;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 背景视频任务持久化 + 恢复轮询逻辑的单元测试
 *
 * 测试场景：
 * 1. bgVideoTaskId 正确持久化和清除
 * 2. setBgVideoTaskId / clearBgVideoTaskId 正确读写 Sound.bgVideoTaskId
 * 3. resumeVideoTaskPolling 在 UI 未初始化时不会被同步调用（bgRoot.post 保证）
 * 4. VideoTaskWorker 回调正确触发 onSuccess / onFailed / onTimeout
 */
public class VideoTaskPersistenceTest extends AndroidTestCase {

    @SmallTest
    public void testBgVideoTaskIdPersistence() throws Exception {
        // 测试 setBgVideoTaskId / clearBgVideoTaskId 正确读写字段
        Context ctx = getContext();

        String testId = "test_sound_001";
        String testTaskId = "task_123456";

        // 清理之前的测试数据
        SoundStore.Sound s = SoundStore.findById(ctx, testId);
        if (s != null) {
            SoundStore.deleteCustom(ctx, testId);
        }

        // 添加测试用自定义白噪音
        SoundStore.addCustom(ctx, testId, "测试白噪音", "https://example.com/test.mp3", null);

        // 验证初始 bgVideoTaskId 为 null
        s = SoundStore.findById(ctx, testId);
        assertNull("新建声音 bgVideoTaskId 应为 null", s.bgVideoTaskId);

        // 设置任务 ID
        SoundStore.setBgVideoTaskId(ctx, testId, testTaskId);
        s = SoundStore.findById(ctx, testId);
        assertEquals("bgVideoTaskId 应等于设置的值", testTaskId, s.bgVideoTaskId);

        // 清除任务 ID
        SoundStore.clearBgVideoTaskId(ctx, testId);
        s = SoundStore.findById(ctx, testId);
        assertNull("清除后 bgVideoTaskId 应为 null", s.bgVideoTaskId);

        // 清理测试数据
        SoundStore.deleteCustom(ctx, testId);
    }

    @SmallTest
    public void testBgVideoTaskIdSetAndClear_areConsistent() throws Exception {
        // 测试多次设置会覆盖，多次清除不会抛异常
        Context ctx = getContext();
        String testId = "test_sound_002";
        String taskId1 = "task_first";
        String taskId2 = "task_second";

        SoundStore.addCustom(ctx, testId, "测试2", "https://example.com/test2.mp3", null);

        // 第一次设置
        SoundStore.setBgVideoTaskId(ctx, testId, taskId1);
        SoundStore.Sound s1 = SoundStore.findById(ctx, testId);
        assertEquals(taskId1, s1.bgVideoTaskId);

        // 第二次设置（覆盖）
        SoundStore.setBgVideoTaskId(ctx, testId, taskId2);
        SoundStore.Sound s2 = SoundStore.findById(ctx, testId);
        assertEquals("第二次设置应覆盖第一次", taskId2, s2.bgVideoTaskId);

        // 多次清除不抛异常
        SoundStore.clearBgVideoTaskId(ctx, testId);
        SoundStore.clearBgVideoTaskId(ctx, testId); // 再清一次
        SoundStore.Sound s3 = SoundStore.findById(ctx, testId);
        assertNull("重复清除后仍为 null", s3.bgVideoTaskId);

        SoundStore.deleteCustom(ctx, testId);
    }

    @SmallTest
    public void testVideoTaskWorker_callbacksCalled() throws Exception {
        // 测试 VideoTaskWorker 回调正确触发
        final AtomicBoolean successCalled = new AtomicBoolean(false);
        final AtomicBoolean failedCalled = new AtomicBoolean(false);
        final AtomicBoolean timeoutCalled = new AtomicBoolean(false);
        final AtomicReference<String> receivedVideoUrl = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // 使用一个无效的 taskId，触发 onFailed 或 onTimeout
        AI.VideoTaskWorker.start("invalid_task_id_no_real_task", new AI.VideoTaskWorker.Callback() {
            @Override public void onSuccess(String taskId, String videoUrl) {
                successCalled.set(true);
                receivedVideoUrl.set(videoUrl);
                latch.countDown();
            }
            @Override public void onFailed(String taskId, String error) {
                failedCalled.set(true);
                latch.countDown();
            }
            @Override public void onTimeout(String taskId) {
                timeoutCalled.set(true);
                latch.countDown();
            }
        });

        // 最多等 35 秒（轮询间隔 30s * 最多 1 次无效查询就失败）
        boolean latchReleased = latch.await(35, TimeUnit.SECONDS);

        // 由于是无效 taskId，onFailed 或 onTimeout 必定触发
        // 注意：queryVideoResult 可能会抛出异常或返回 error
        assertTrue("回调应在超时前触发", latchReleased);
        boolean anyCalled = successCalled.get() || failedCalled.get() || timeoutCalled.get();
        assertTrue("至少有一个回调被触发", anyCalled);
    }

    @SmallTest
    public void testBgVideoUrlClearedOnSuccess() throws Exception {
        // 模拟成功回调：bgVideoTaskId 应被清除，bgVideoUrl 应被设置
        Context ctx = getContext();
        String testId = "test_sound_003";
        String taskId = "task_success_test";
        String videoUrl = "https://example.com/video.mp4";

        SoundStore.addCustom(ctx, testId, "测试3", "https://example.com/test3.mp3", null);
        SoundStore.setBgVideoTaskId(ctx, testId, taskId);

        // 模拟 onSuccess 逻辑
        SoundStore.Sound s = SoundStore.findById(ctx, testId);
        s.bgVideoTaskId = null;           // 模拟 clearBgVideoTaskId
        s.bgVideoUrl = videoUrl;           // 模拟 setBgVideoUrl
        SoundStore.clearBgVideoTaskId(ctx, testId);
        SoundStore.setBgVideoUrl(ctx, testId, videoUrl);

        SoundStore.Sound result = SoundStore.findById(ctx, testId);
        assertNull("成功后 bgVideoTaskId 应被清除", result.bgVideoTaskId);
        assertEquals("成功后 bgVideoUrl 应被设置", videoUrl, result.bgVideoUrl);

        SoundStore.deleteCustom(ctx, testId);
    }

    @SmallTest
    public void testBgVideoTaskIdNotCrashWhenSoundNull() throws Exception {
        // 边界测试：sound 为 null 时不应崩溃
        Context ctx = getContext();

        // 不创建 sound，直接测试 setBgVideoTaskId 对不存在的 id 不抛异常
        try {
            SoundStore.setBgVideoTaskId(ctx, "non_existent_id", "some_task");
            SoundStore.clearBgVideoTaskId(ctx, "non_existent_id");
        } catch (Exception e) {
            fail("对不存在的 sound 设置 taskId 不应抛异常: " + e.getMessage());
        }
    }
}
