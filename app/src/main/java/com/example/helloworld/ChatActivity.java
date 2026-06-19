package com.example.helloworld;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.AsyncTask;
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

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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

        soundId = getIntent().getStringExtra("sound_id");
        sound = SoundStore.findById(this, soundId);
        if (sound == null) { finish(); return; }

        // 主题模式
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#666666");
        final int cardBg = dark ? Color.parseColor("#222222") : Color.parseColor("#FAFAFA");
        final int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");
        final int primary = Color.parseColor("#07C160");

        messages = SoundStore.loadMessages(this, soundId);

        // 状态栏：深色主题用深色，浅色主题用浅色 + 深色文字
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(dark ? Color.parseColor("#1a1a1a")
                    : Color.parseColor("#F7F7F7"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        }

        // 背景根（可放背景图片 + 渐变叠加层）
        bgRoot = new FrameLayout(this);
        bgRoot.setBackgroundColor(dark ? Color.parseColor("#121212") : Color.parseColor("#F7F7F7"));

        // 背景图片层（如果设置了自定义背景图片）
        final ImageView bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        bgImage.setLayoutParams(imgLp);
        bgImage.setVisibility(View.INVISIBLE);
        bgRoot.addView(bgImage);

        // 渐变叠加层（放在图片上方、文字区域不透明度最大，边缘渐隐）
        final View gradOverlay = new View(this);
        gradOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        bgRoot.addView(gradOverlay); // main 会在它后面 addView，成为最顶层

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
        topBar.setBackgroundColor(cardBg);
        topBar.setPadding(dip2px(8), 0, dip2px(8), 0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(50));
        topBar.setLayoutParams(tlp);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextSize(18);
        backBtn.setTextColor(textMain);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            dip2px(60), LinearLayout.LayoutParams.MATCH_PARENT);
        backBtn.setLayoutParams(blp);
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(sound.name);
        title.setTextSize(17);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        title.setLayoutParams(titleLp);
        topBar.addView(title);

        // 右侧：清空 + 转发 按钮
        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        actionBar.setLayoutParams(ablp);

        Button clearBtn = new Button(this);
        clearBtn.setText("🧹");
        clearBtn.setTextSize(16);
        clearBtn.setTextColor(textMain);
        clearBtn.setBackgroundColor(Color.TRANSPARENT);
        clearBtn.setPadding(dip2px(8), 0, dip2px(8), 0);
        clearBtn.setOnClickListener(v -> {
            if (messages == null || messages.isEmpty()) {
                Toast.makeText(this, "聊天记录已经是空的了", Toast.LENGTH_SHORT).show();
                return;
            }
            // 二次确认
            final FrameLayout confirmWrap = new FrameLayout(this);
            confirmWrap.setBackgroundColor(Color.parseColor("#AA000000"));
            final FrameLayout.LayoutParams cwLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            confirmWrap.setLayoutParams(cwLp);

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundColor(dark ? Color.parseColor("#1e1e1e") : Color.parseColor("#FFFFFF"));
            panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(20));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                dip2px(280), FrameLayout.LayoutParams.WRAP_CONTENT);
            plp.gravity = Gravity.CENTER;
            panel.setLayoutParams(plp);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(dark ? Color.parseColor("#1e1e1e") : Color.parseColor("#FFFFFF"));
            pbg.setCornerRadius(dip2px(12));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
            else panel.setBackgroundDrawable(pbg);

            TextView ptitle = new TextView(this);
            ptitle.setText("清空聊天记录？");
            ptitle.setTextSize(16);
            ptitle.setTextColor(textMain);
            ptitle.getPaint().setFakeBoldText(true);
            panel.addView(ptitle);

            TextView pdesc = new TextView(this);
            pdesc.setText("此操作不可恢复，所有与「" + sound.name + "」的对话将被移除。");
            pdesc.setTextSize(13);
            pdesc.setTextColor(textSub);
            pdesc.setPadding(0, dip2px(8), 0, dip2px(16));
            panel.addView(pdesc);

            Button okBtn = new Button(this);
            okBtn.setText("确认清空");
            okBtn.setTextSize(14);
            okBtn.setTextColor(Color.WHITE);
            okBtn.setBackgroundColor(Color.parseColor("#ef4444"));
            okBtn.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
            LinearLayout.LayoutParams oklp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            okBtn.setLayoutParams(oklp);
            okBtn.setOnClickListener(v2 -> {
                messages.clear();
                SoundStore.saveMessages(this, soundId, messages);
                SoundStore.setLastMessage(this, soundId, "");
                // 重新渲染
                msgContainer.removeAllViews();
                // 欢迎消息
                msgContainer.postDelayed(() -> addMessage("欢迎回到「" + sound.name + "」，聊天记录已清空。", false), 200);
                ((ViewGroup) confirmWrap.getParent()).removeView(confirmWrap);
                Toast.makeText(this, "已清空「" + sound.name + "」的聊天记录", Toast.LENGTH_SHORT).show();
            });
            panel.addView(okBtn);

            Button cancelBtn = new Button(this);
            cancelBtn.setText("取消");
            cancelBtn.setTextSize(14);
            cancelBtn.setTextColor(textMain);
            cancelBtn.setBackgroundColor(Color.TRANSPARENT);
            cancelBtn.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
            LinearLayout.LayoutParams canlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            canlp.topMargin = dip2px(6);
            cancelBtn.setLayoutParams(canlp);
            cancelBtn.setOnClickListener(v2 -> ((ViewGroup) confirmWrap.getParent()).removeView(confirmWrap));
            panel.addView(cancelBtn);

            confirmWrap.addView(panel);
            bgRoot.addView(confirmWrap);
        });
        actionBar.addView(clearBtn);

        Button shareBtn = new Button(this);
        shareBtn.setText("📤");
        shareBtn.setTextSize(16);
        shareBtn.setTextColor(textMain);
        shareBtn.setBackgroundColor(Color.TRANSPARENT);
        shareBtn.setPadding(dip2px(8), 0, dip2px(8), 0);
        shareBtn.setOnClickListener(v -> doShare());
        actionBar.addView(shareBtn);

        topBar.addView(actionBar);

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
        playerBar.setBackgroundColor(cardBg);
        playerBar.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams pllp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playerBar.setLayoutParams(pllp);

        final Button playBtn = new Button(this);
        playBtn.setText("▶ 播放");
        playBtn.setTextSize(14);
        playBtn.setTextColor(Color.WHITE);
        playBtn.setBackgroundColor(primary);
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
        stateText.setTextColor(textSub);
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
        inputBar.setBackgroundColor(dark ? Color.parseColor("#1a1a1a")
                : Color.parseColor("#F0F0F0"));
        inputBar.setPadding(dip2px(8), dip2px(8), dip2px(8), dip2px(8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputBar.setLayoutParams(ilp);

        final EditText input = new EditText(this);
        input.setHint("说点什么...");
        input.setTextSize(14);
        input.setTextColor(textMain);
        input.setHintTextColor(textSub);
        input.setBackgroundColor(inputBg);
        input.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        input.setLayoutParams(inputLp);
        inputBar.addView(input);

        Button sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextSize(14);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setBackgroundColor(primary);
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

        // 初始化背景流动动画（渐变叠加在图片或纯色背景上）
        startBgAnimation(gradOverlay, bgImage);

        // 异步加载自定义背景图片
        if (sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty()) {
            new LoadBgImageTask(bgImage).execute(sound.bgImageUrl);
        }

        // 渲染已有消息（根据主题更新气泡）
        for (SoundStore.Message m : messages) {
            addMessageView(m.text, m.fromUser);
        }
        if (messages.isEmpty()) {
            // 欢迎消息
            msgContainer.postDelayed(() -> addMessage("欢迎来到「" + sound.name + "」聊天室，播放白噪音放松一下吧～", false), 300);
        }

        // 自动开始播放
        msgContainer.postDelayed(() -> {
            togglePlay();
            playBtn.setText(isPlaying ? "⏸ 暂停" : "▶ 播放");
        }, 400);
    }

    // 判断当前主题是否深色
    private static boolean isDarkMode(Activity ctx) {
        int mode = ctx.getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
            .getInt("theme_mode", 0);
        if (mode == 1) return false; // 浅色
        if (mode == 2) return true;  // 深色
        // 跟随系统
        int uiMode = ctx.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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
        final boolean dark = isDarkMode(this);
        final int botBubbleBg = dark ? Color.parseColor("#3a3a3a")
                : Color.WHITE;
        final int botText = dark ? Color.WHITE : Color.BLACK;
        final int avatarBg = dark ? Color.parseColor("#333333")
                : Color.parseColor("#E8E8E8");
        final int avatarText = dark ? Color.WHITE : Color.parseColor("#333333");

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
            avatar.setBackgroundColor(avatarBg);
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
        bubble.setTextColor(fromUser ? Color.WHITE : botText);
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
            bubbleBg.setColor(botBubbleBg);
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
            avatar.setTextColor(avatarText);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundColor(avatarBg);
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
    private GradientDrawable bgDrawable;

    private void startBgAnimation(final View gradOverlay, final ImageView bgImage) {
        final boolean dark = isDarkMode(this);
        final int[] darkColors = sound.getChatBgColors();        // 2 colors
        final int[] lightColors = sound.getChatBgColorsLight();  // 3 colors

        // 选3个"锚"颜色
        final int c0, c1, c2;
        if (dark) {
            c0 = darkColors[0];
            c1 = mixColor(darkColors[0], darkColors[1], 0.5f);
            c2 = darkColors[1];
        } else {
            c0 = lightColors[0];
            c1 = lightColors[1];
            c2 = lightColors[2];
        }

        // 判断是否有自定义背景图片
        final boolean hasBgImage = (sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty());

        if (hasBgImage) {
            // 图片模式：渐变两端压暗、中间透明 → 叠加在图片上方
            final int topDark = mixColor(c0, Color.BLACK, 0.5f);
            final int botDark = mixColor(c2, Color.BLACK, 0.5f);
            bgDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topDark, Color.TRANSPARENT, Color.TRANSPARENT, botDark});
        } else {
            // 无图片：全不透明渐变
            bgDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c0, c1, c2, c1});
        }
        bgDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);

        // gradOverlay 是 bgRoot 的子 View，背景叠加在图片上方
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            gradOverlay.setBackground(bgDrawable);
        } else {
            gradOverlay.setBackgroundDrawable(bgDrawable);
        }

        bgAnimStart = System.currentTimeMillis();
        bgHandler = new android.os.Handler();
        final GradientDrawable.Orientation[] orients = {
            GradientDrawable.Orientation.TL_BR,
            GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.TR_BL,
            GradientDrawable.Orientation.RIGHT_LEFT,
            GradientDrawable.Orientation.BR_TL,
            GradientDrawable.Orientation.BOTTOM_TOP,
            GradientDrawable.Orientation.BL_TR,
            GradientDrawable.Orientation.LEFT_RIGHT,
        };
        bgAnim = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                float t = ((now - bgAnimStart) % 6000) / 6000f;

                float phase = t < 0.5f ? t * 2 : (1 - t) * 2;
                float t2 = (t + 0.33f) % 1.0f;
                float phase2 = t2 < 0.5f ? t2 * 2 : (1 - t2) * 2;
                float t3 = (t + 0.66f) % 1.0f;
                float phase3 = t3 < 0.5f ? t3 * 2 : (1 - t3) * 2;

                int col1 = mixColor(c0, c1, phase);
                int col2 = mixColor(c1, c2, phase2);
                int col3 = mixColor(c0, c2, phase3);

                if (hasBgImage) {
                    int td = mixColor(col1, Color.BLACK, 0.5f);
                    int bd = mixColor(col3, Color.BLACK, 0.5f);
                    bgDrawable.setColors(new int[]{td, Color.TRANSPARENT, Color.TRANSPARENT, bd});
                } else {
                    bgDrawable.setColors(new int[]{col1, col2, col3, col2});
                }

                int orientIdx = (int)((now - bgAnimStart) / 4000) % orients.length;
                bgDrawable.setOrientation(orients[orientIdx]);

                bgHandler.postDelayed(this, 150);
            }
        };
        bgHandler.post(bgAnim);
    }

    // 异步加载背景图片
    private static class LoadBgImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView target;
        LoadBgImageTask(ImageView iv) { this.target = iv; }

        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                URL url = new URL(params[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null && target != null) {
                target.setImageBitmap(result);
                target.setVisibility(View.VISIBLE);
            }
        }
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

    private void doShare() {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(this, "还没有聊天内容可以转发", Toast.LENGTH_SHORT).show();
            return;
        }
        // 构建转发文本
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(sound.name).append("】").append(" 聊天记录\n");
        sb.append("--------------------------------\n");
        for (SoundStore.Message m : messages) {
            String who = m.fromUser ? "我" : "助手";
            sb.append("[").append(who).append("] ").append(m.text).append("\n");
        }
        String content = sb.toString();
        // 1) 复制到剪贴板
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData cd = ClipData.newPlainText("聊天记录", content);
                cm.setPrimaryClip(cd);
            }
        } catch (Exception ignored) {}
        // 2) 通过系统分享面板转发
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "「" + sound.name + "」聊天记录");
        share.putExtra(Intent.EXTRA_TEXT, content);
        try {
            startActivity(Intent.createChooser(share, "转发到"));
        } catch (Exception e) {
            Toast.makeText(this, "聊天记录已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
