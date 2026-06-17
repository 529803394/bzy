package com.example.helloworld;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 聊天式播放界面：
// - 顶部：返回 + 白噪音名称
// - 中间：消息对话区（气泡样式）
// - 底部：播放控制 + 消息输入框
// - 背景：该白噪音主题色渐变（带动画感）
public class ChatActivity extends Activity {

    private String soundId;
    private SoundStore.Sound sound;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying;

    private LinearLayout msgContainer; // 消息列表容器
    private ScrollView msgScroller;

    // 消息数据
    private List<SoundStore.Message> messages;

    // 用于更新lastMessage到首页列表
    private String lastMsg;

    // 背景动画相关
    private FrameLayout bgRoot;
    private long bgAnimStart;
    private android.os.Handler bgHandler;
    private Runnable bgAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 状态栏深色/浅色自适应
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1a1a1a"));
        }

        soundId = getIntent().getStringExtra("sound_id");
        sound = SoundStore.findById(this, soundId);
        if (sound == null) { finish(); return; }

        messages = SoundStore.loadMessages(this, soundId);

        // 背景根
        bgRoot = new FrameLayout(this);
        bgRoot.setBackgroundColor(Color.BLACK);

        // 主垂直布局
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        main.setLayoutParams(mlp);

        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#222222"));
        topBar.setPadding(dip2px(8), 0, dip2px(8), 0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(50));
        topBar.setLayoutParams(tlp);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextSize(18);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            dip2px(60), LinearLayout.LayoutParams.MATCH_PARENT);
        backBtn.setLayoutParams(blp);
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(sound.name);
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        title.setLayoutParams(titleLp);
        topBar.addView(title);

        // 右侧占位保持标题居中
        TextView spacer = new TextView(this);
        spacer.setWidth(dip2px(60));
        topBar.addView(spacer);

        main.addView(topBar);

        // 消息滚动区
        msgScroller = new ScrollView(this);
        msgScroller.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        msgScroller.setPadding(dip2px(12), dip2px(12), dip2px(12), dip2px(4));

        msgContainer = new LinearLayout(this);
        msgContainer.setOrientation(LinearLayout.VERTICAL);
        msgScroller.addView(msgContainer);
        main.addView(msgScroller);

        // 底部播放控制条
        LinearLayout playerBar = new LinearLayout(this);
        playerBar.setOrientation(LinearLayout.HORIZONTAL);
        playerBar.setGravity(Gravity.CENTER_VERTICAL);
        playerBar.setBackgroundColor(Color.parseColor("#222222"));
        playerBar.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams pllp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playerBar.setLayoutParams(pllp);

        final Button playBtn = new Button(this);
        playBtn.setText("▶ 播放");
        playBtn.setTextSize(14);
        playBtn.setTextColor(Color.WHITE);
        playBtn.setBackgroundColor(Color.parseColor("#07C160"));
        playBtn.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playBtn.setLayoutParams(playLp);
        playBtn.setOnClickListener(v -> {
            togglePlay();
            playBtn.setText(isPlaying ? "⏸ 暂停" : "▶ 播放");
        });
        playerBar.addView(playBtn);

        // 播放状态文字
        TextView stateText = new TextView(this);
        stateText.setText("点击开始播放白噪音");
        stateText.setTextSize(12);
        stateText.setTextColor(Color.parseColor("#CCCCCC"));
        stateText.setGravity(Gravity.CENTER_VERTICAL);
        stateText.setPadding(dip2px(12), 0, 0, 0);
        stateText.setId(View.generateViewId());
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        stateText.setLayoutParams(stlp);
        stateText.setTag("stateText");
        playerBar.addView(stateText);

        main.addView(playerBar);

        // 消息输入栏
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setBackgroundColor(Color.parseColor("#1a1a1a"));
        inputBar.setPadding(dip2px(8), dip2px(8), dip2px(8), dip2px(8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputBar.setLayoutParams(ilp);

        final EditText input = new EditText(this);
        input.setHint("说点什么...");
        input.setTextSize(14);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#777777"));
        input.setBackgroundColor(Color.parseColor("#2a2a2a"));
        input.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        input.setLayoutParams(inputLp);
        inputBar.addView(input);

        Button sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextSize(14);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setBackgroundColor(Color.parseColor("#07C160"));
        sendBtn.setPadding(dip2px(14), dip2px(8), dip2px(14), dip2px(8));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sendLp.leftMargin = dip2px(8);
        sendBtn.setLayoutParams(sendLp);
        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            addMessage(text, true);
            input.setText("");
            // 自动回复
            TextView st = (TextView) playerBar.findViewWithTag("stateText");
            String reply = buildAutoReply(text, sound.name);
            msgContainer.postDelayed(() -> addMessage(reply, false), 600);
        });
        inputBar.addView(sendBtn);

        main.addView(inputBar);

        bgRoot.addView(main);
        setContentView(bgRoot);

        // 初始化背景动画
        startBgAnimation();

        // 渲染已有消息
        for (SoundStore.Message m : messages) {
            addMessageView(m.text, m.fromUser);
        }
        if (messages.isEmpty()) {
            // 欢迎消息（系统回复样式）
            msgContainer.postDelayed(() -> addMessage("欢迎来到「" + sound.name + "」聊天室，播放白噪音放松一下吧～", false), 300);
        }

        // 自动开始播放
        msgContainer.postDelayed(() -> {
            togglePlay();
            playBtn.setText(isPlaying ? "⏸ 暂停" : "▶ 播放");
        }, 400);
    }

    private String buildAutoReply(String userText, String soundName) {
        // 简单的关键词回复 + 白噪音提示
        if (userText.contains("睡觉") || userText.contains("失眠") || userText.contains("睡")) {
            return "让「" + soundName + "」陪你入睡吧～";
        }
        if (userText.contains("你好") || userText.contains("hi") || userText.contains("在吗")) {
            return "你好呀～正在播放「" + soundName + "」";
        }
        if (userText.contains("停") || userText.contains("暂停")) {
            return "点下面的按钮可以暂停哦";
        }
        if (userText.contains("谢谢") || userText.contains("感谢")) {
            return "不客气～保持好心情";
        }
        // 默认回复
        String[] defaults = {
            "嗯，我在听",
            "继续说吧，这里只有「" + soundName + "」和我",
            "（" + soundName + "中...）",
            "听起来不错呢",
            "深呼吸一下",
        };
        return defaults[(int) (Math.random() * defaults.length)];
    }

    private void addMessage(String text, boolean fromUser) {
        messages.add(new SoundStore.Message(text, fromUser));
        addMessageView(text, fromUser);
        if (fromUser) lastMsg = text;
        // 自动滚动到底部
        msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageView(String text, boolean fromUser) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(fromUser ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dip2px(6);
        row.setLayoutParams(rlp);

        if (!fromUser) {
            // 左侧头像
            TextView avatar = new TextView(this);
            avatar.setText("🔊");
            avatar.setTextSize(18);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                dip2px(34), dip2px(34));
            alp.rightMargin = dip2px(8);
            avatar.setLayoutParams(alp);
            row.addView(avatar);
        }

        // 气泡
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.width = Math.min(maxW, LinearLayout.LayoutParams.WRAP_CONTENT);
        bubble.setLayoutParams(blp);
        bubble.setMaxWidth(maxW);

        GradientDrawable bubbleBg = new GradientDrawable();
        if (fromUser) {
            bubbleBg.setColor(Color.parseColor("#07C160"));
        } else {
            bubbleBg.setColor(Color.parseColor("#3a3a3a"));
        }
        bubbleBg.setCornerRadius(dip2px(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            bubble.setBackground(bubbleBg);
        } else {
            bubble.setBackgroundDrawable(bubbleBg);
        }
        row.addView(bubble);

        if (fromUser) {
            // 右侧头像
            TextView avatar = new TextView(this);
            avatar.setText("我");
            avatar.setTextSize(12);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundColor(Color.parseColor("#444444"));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                dip2px(34), dip2px(34));
            alp.leftMargin = dip2px(8);
            avatar.setLayoutParams(alp);
            row.addView(avatar);
        }

        msgContainer.addView(row);
    }

    // -------- 播放控制 --------
    private void togglePlay() {
        if (isPlaying) {
            stopPlay();
        } else {
            startPlay();
        }
    }

    private void startPlay() {
        try {
            if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
            else mediaPlayer.reset();

            if (sound.resId > 0) {
                // 内置资源
                mediaPlayer = MediaPlayer.create(this, sound.resId);
                if (mediaPlayer == null) {
                    Toast.makeText(this, "播放失败: 资源无法加载", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // 自定义URL
                mediaPlayer.setDataSource(sound.url);
                mediaPlayer.prepare();
            }
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            isPlaying = true;
        } catch (IOException e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isPlaying = false;
        } catch (Exception e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isPlaying = false;
        }
    }

    private void stopPlay() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}
        isPlaying = false;
    }

    // -------- 背景动画（缓慢的颜色渐变流动）--------
    private void startBgAnimation() {
        final int[] colors = sound.getChatBgColors();
        final int colorA = colors[0];
        final int colorB = colors[1];
        bgAnimStart = System.currentTimeMillis();
        bgHandler = new android.os.Handler();
        bgAnim = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                float t = ((now - bgAnimStart) % 8000) / 8000f; // 8秒一个周期
                // 用三角波让过渡更自然
                float phase = t < 0.5f ? t * 2 : (1 - t) * 2;
                int mixed = mixColor(colorA, colorB, phase);
                // 背景：上下渐变
                GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{mixed, colorB, colorA, mixed});
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    bgRoot.setBackground(gd);
                } else {
                    bgRoot.setBackgroundDrawable(gd);
                }
                bgHandler.postDelayed(this, 200);
            }
        };
        bgHandler.post(bgAnim);
    }

    private static int mixColor(int a, int b, float t) {
        int ra = (a >> 16) & 0xff, ga = (a >> 8) & 0xff, ba = a & 0xff;
        int rb = (b >> 16) & 0xff, gb = (b >> 8) & 0xff, bb = b & 0xff;
        int r = (int) (ra * (1 - t) + rb * t);
        int g = (int) (ga * (1 - t) + gb * t);
        int bl = (int) (ba * (1 - t) + bb * t);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    protected void onDestroy() {
        // 检查是否开启了后台播放
        boolean bgPlay = getSharedPreferences("whitenoise_settings", Context.MODE_PRIVATE)
            .getBoolean("bg_play", false);
        if (!bgPlay) stopPlay();
        // 停止背景动画
        if (bgHandler != null && bgAnim != null) {
            bgHandler.removeCallbacks(bgAnim);
        }
        // 保存消息和lastMessage
        SoundStore.saveMessages(this, soundId, messages);
        if (lastMsg != null) {
            SoundStore.setLastMessage(this, soundId, lastMsg);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
