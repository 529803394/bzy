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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
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
//
// AI 对话：使用 DeepSeek（真实 LLM，规则仅作网络失败兜底）
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
    private ImageView bgImage; // 背景图片（智能配图后可替换）
    private VideoView bgVideo; // 背景视频（智谱AI生成后可替换）
    private View gradOverlay;   // 渐变叠加层
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
        bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        bgImage.setLayoutParams(imgLp);
        bgImage.setVisibility(View.INVISIBLE);
        bgRoot.addView(bgImage);

        // 背景视频层（如果设置了背景视频）
        bgVideo = new VideoView(this);
        bgVideo.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        bgVideo.setVisibility(View.INVISIBLE);
        bgRoot.addView(bgVideo);

        // 渐变叠加层（放在图片/视频上方、文字区域不透明度最大，边缘渐隐）
        gradOverlay = new View(this);
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

        // 右侧：更多按钮（清除历史、分享、生成背景图 等二级入口）
        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        actionBar.setLayoutParams(ablp);

        Button moreBtn = new Button(this);
        moreBtn.setText("···");
        moreBtn.setTextSize(22);
        moreBtn.setTextColor(textMain);
        moreBtn.setBackgroundColor(Color.TRANSPARENT);
        moreBtn.setPadding(dip2px(12), 0, dip2px(12), 0);
        moreBtn.setOnClickListener(v -> showMoreMenu(moreBtn));
        actionBar.addView(moreBtn);

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

        // ===== 浮动播放控件（QQ 音乐样式：半透明胶囊 + 圆形播放按钮）=====
        // 放置在 main 内（消息滚动区和输入栏之间），半透明背景让它与背景图融为一体
        FrameLayout floatWrap = new FrameLayout(this);
        GradientDrawable floatBg = new GradientDrawable();
        floatBg.setCornerRadius(dip2px(28));
        floatBg.setColor(dark ? Color.parseColor("#601a1a1a") : Color.parseColor("#60FFFFFF"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            floatWrap.setBackground(floatBg);
        } else {
            floatWrap.setBackgroundDrawable(floatBg);
        }
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(56));
        flp.leftMargin = dip2px(14);
        flp.rightMargin = dip2px(14);
        flp.topMargin = dip2px(8);
        flp.bottomMargin = dip2px(8);
        floatWrap.setLayoutParams(flp);

        // 左侧：声音名 + 状态
        LinearLayout leftInfo = new LinearLayout(this);
        leftInfo.setOrientation(LinearLayout.VERTICAL);
        leftInfo.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dip2px(16);
        llp.rightMargin = dip2px(72); // 给右侧圆形按钮留空间
        llp.gravity = Gravity.CENTER_VERTICAL;
        leftInfo.setLayoutParams(llp);

        TextView nameTv = new TextView(this);
        nameTv.setText(sound.name);
        nameTv.setTextSize(14);
        nameTv.setTextColor(dark ? Color.parseColor("#FFFFFF") : Color.parseColor("#222222"));
        nameTv.getPaint().setFakeBoldText(true);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        leftInfo.addView(nameTv);

        final TextView stateTv = new TextView(this);
        stateTv.setText("点击开始播放");
        stateTv.setTextSize(11);
        stateTv.setTextColor(dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#888888"));
        stateTv.setSingleLine(true);
        stateTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = dip2px(2);
        stateTv.setLayoutParams(stlp);
        leftInfo.addView(stateTv);
        floatWrap.addView(leftInfo);

        // 右侧：圆形播放按钮
        final FrameLayout playCircle = new FrameLayout(this);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(dark ? Color.parseColor("#E6FFFFFF") : Color.parseColor("#E6000000"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            playCircle.setBackground(circleBg);
        } else {
            playCircle.setBackgroundDrawable(circleBg);
        }
        FrameLayout.LayoutParams pclp = new FrameLayout.LayoutParams(
            dip2px(44), dip2px(44));
        pclp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        pclp.rightMargin = dip2px(6);
        playCircle.setLayoutParams(pclp);

        final TextView playIcon = new TextView(this);
        playIcon.setText("▶");
        playIcon.setTextSize(18);
        playIcon.setTextColor(dark ? Color.parseColor("#222222") : Color.parseColor("#FFFFFF"));
        playIcon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pilp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pilp.gravity = Gravity.CENTER;
        playIcon.setLayoutParams(pilp);
        playCircle.addView(playIcon);
        floatWrap.addView(playCircle);

        // 点击任意位置切换播放
        View.OnClickListener playListener = new View.OnClickListener() {
            @Override public void onClick(View v) {
                togglePlay();
                if (isPlaying) {
                    playIcon.setText("⏸");
                    stateTv.setText("正在播放 · 放松一下");
                } else {
                    playIcon.setText("▶");
                    stateTv.setText("点击开始播放");
                }
            }
        };
        playCircle.setOnClickListener(playListener);
        floatWrap.setOnClickListener(playListener);
        main.addView(floatWrap);

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
            final String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            addMessage(text, true);
            input.setText("");

            // 思考气泡（用于显示 "正在思考..."，API 返回后自动替换为真实回复）
            final TextView thinkingBubble = new TextView(this);
            thinkingBubble.setText("🐻 正在思考...");
            thinkingBubble.setTextSize(13);
            thinkingBubble.setTextColor(Color.parseColor("#999999"));
            thinkingBubble.setPadding(dip2px(12), dip2px(6), dip2px(12), dip2px(6));
            GradientDrawable tbBg = new GradientDrawable();
            tbBg.setColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EEEEEE"));
            tbBg.setCornerRadius(dip2px(8));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) thinkingBubble.setBackground(tbBg);
            else thinkingBubble.setBackgroundDrawable(tbBg);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.LEFT);
            row.setPadding(0, dip2px(4), 0, 0);
            row.addView(thinkingBubble);
            msgContainer.addView(row);
            msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));

            // 后台线程调用 DeepSeek
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... p) {
                    try {
                        List<AI.ChatMessage> history = new ArrayList<>();
                        int start = Math.max(0, messages.size() - 8);
                        for (int i = start; i < messages.size() - 1; i++) {
                            SoundStore.Message m = messages.get(i);
                            history.add(new AI.ChatMessage(m.text, m.fromUser));
                        }
                        String reply = AI.chatWithSound(ChatActivity.this, sound.name, text, history);
                        return (reply != null && !reply.isEmpty()) ? reply : null;
                    } catch (Throwable e) {
                        return "⚠️ 网络异常，请稍后再试";
                    }
                }
                @Override
                protected void onPostExecute(String reply) {
                    // 移除思考气泡（先移除 LinearLayout row）
                    try {
                        ViewGroup parent = (ViewGroup) row.getParent();
                        if (parent != null) parent.removeView(row);
                    } catch (Exception ignored) {}

                    String r = (reply == null || reply.isEmpty()) ? "嗯，我在听你说～" : reply;
                    addMessage(r, false);
                    // 如果回复以 ⚠️ 开头，说明是错误，弹 toast
                    if (r.startsWith("⚠️")) {
                        Toast.makeText(ChatActivity.this, r, Toast.LENGTH_LONG).show();
                        // 把错误消息从列表里删掉（不算真正的对话）
                        if (!messages.isEmpty()) messages.remove(messages.size() - 1);
                    }
                }
            }.execute();
        });
        inputBar.addView(sendBtn);

        main.addView(inputBar);

        bgRoot.addView(main);
        setContentView(bgRoot);

        // 初始化背景流动动画（渐变叠加在图片或纯色背景上）
        startBgAnimation(gradOverlay, bgImage);

        // 根据设置决定显示背景图还是背景视频
        int bgDisplayMode = getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
            .getInt("bg_display_mode", 0); // 0=图片优先, 1=视频优先

        // 视频优先模式：有视频URL则显示视频，否则显示图片
        // 图片优先模式：有图片URL则显示图片，否则显示视频
        boolean hasVideo = sound.bgVideoUrl != null && !sound.bgVideoUrl.isEmpty();
        boolean hasImage = sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty();

        if (bgDisplayMode == 1 && hasVideo) {
            // 视频优先模式且有视频
            loadBackgroundVideo(sound.bgVideoUrl);
        } else if (hasImage) {
            // 图片优先模式或有图片但无视频
            new LoadBgImageTask(bgImage).execute(sound.bgImageUrl);
        } else if (hasVideo) {
            // 无图片但有视频
            loadBackgroundVideo(sound.bgVideoUrl);
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
            playIcon.setText("⏸");
            stateTv.setText("正在播放 · 放松一下");
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
            avatar.setText("🐻");
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
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                isPlaying = true;
                return;
            }

            // 自定义/网络：优先本地缓存，否则网络 URL
            String playSource = null;
            boolean isNetworkSource = false;
            if (sound.localPath != null && !sound.localPath.isEmpty()) {
                java.io.File localFile = new java.io.File(sound.localPath);
                if (localFile.exists() && localFile.length() > 0) {
                    playSource = sound.localPath;
                }
            }
            if (playSource == null) {
                if (sound.url != null && !sound.url.isEmpty()) {
                    playSource = sound.url;
                    isNetworkSource = true;
                } else {
                    Toast.makeText(this, "播放失败: 未找到音频 (sound.url=" + sound.url + ")", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            // 网络URL用Uri方式设置数据源，兼容更多Android版本
            if (isNetworkSource) {
                mediaPlayer.setDataSource(this, android.net.Uri.parse(playSource));
            } else {
                mediaPlayer.setDataSource(playSource);
            }
            mediaPlayer.setLooping(true);

            final String sourceForLog = playSource;
            if (isNetworkSource) {
                // 网络源异步准备，避免主线程阻塞
                mediaPlayer.setOnPreparedListener(mp -> {
                    try {
                        mp.start();
                        isPlaying = true;
                    } catch (Exception e) {
                        Toast.makeText(ChatActivity.this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Toast.makeText(ChatActivity.this, "播放失败: 网络音频加载错误 (what=" + what + ", extra=" + extra + ", url=" + sourceForLog + ")", Toast.LENGTH_LONG).show();
                    isPlaying = false;
                    try { mp.reset(); } catch (Exception ignored) {}
                    return true;
                });
                mediaPlayer.prepareAsync();
                Toast.makeText(this, "正在加载音频: " + playSource, Toast.LENGTH_SHORT).show();
            } else {
                // 本地文件同步准备
                mediaPlayer.prepare();
                mediaPlayer.start();
                isPlaying = true;
            }
        } catch (java.io.IOException e) {
            String msg = e.getMessage();
            Toast.makeText(this, "播放失败: " + (msg == null ? "音频源不可用" : msg), Toast.LENGTH_LONG).show();
            isPlaying = false;
            try { if (mediaPlayer != null) mediaPlayer.reset(); } catch (Exception ignored) {}
        } catch (Exception e) {
            String msg = e.getMessage();
            Toast.makeText(this, "播放失败: " + (msg == null ? "未知错误" : msg), Toast.LENGTH_LONG).show();
            isPlaying = false;
            try { if (mediaPlayer != null) mediaPlayer.reset(); } catch (Exception ignored) {}
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

    private void startBgAnimation(final View gradOverlay, final View bgMediaView) {
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

        // 判断是否有自定义背景媒体（图片或视频）
        final boolean hasBgMedia = (sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty())
            || (sound.bgVideoUrl != null && !sound.bgVideoUrl.isEmpty());

        if (hasBgMedia) {
            // 图片/视频模式：渐变两端压暗、中间透明 → 叠加在媒体上方
            final int topDark = mixColor(c0, Color.BLACK, 0.5f);
            final int botDark = mixColor(c2, Color.BLACK, 0.5f);
            bgDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topDark, Color.TRANSPARENT, Color.TRANSPARENT, botDark});
        } else {
            // 无媒体：全不透明渐变
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

                if (hasBgMedia) {
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

    // 加载背景视频（网络URL或本地路径）
    private void loadBackgroundVideo(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) return;
        // 优先本地缓存
        String localPath = sound.bgVideoLocalPath;
        if (localPath != null && !localPath.isEmpty()) {
            java.io.File f = new java.io.File(localPath);
            if (f.exists()) {
                playBackgroundVideo(localPath);
                return;
            }
        }
        // 网络URL
        playBackgroundVideo(videoUrl);
    }

    // 播放背景视频（循环播放）
    private void playBackgroundVideo(String path) {
        bgVideo.setVideoPath(path);
        bgVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // 静音
            bgVideo.setVisibility(View.VISIBLE);
            bgImage.setVisibility(View.INVISIBLE);
            bgVideo.start();
            // 重新启动动画（视频模式下叠加层仍需要）
            if (bgHandler != null) bgHandler.removeCallbacks(bgAnim);
            startBgAnimation(gradOverlay, bgVideo);
        });
        bgVideo.setOnErrorListener((mp, what, extra) -> {
            // 视频播放失败，fallback到图片
            bgVideo.setVisibility(View.INVISIBLE);
            if (sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty()) {
                new LoadBgImageTask(bgImage).execute(sound.bgImageUrl);
            }
            return true;
        });
    }

    // 异步加载背景图片
    private class LoadBgImageTask extends AsyncTask<String, Void, android.graphics.Bitmap> {
        private final ImageView target;
        LoadBgImageTask(ImageView iv) { this.target = iv; }

        @Override
        protected android.graphics.Bitmap doInBackground(String... params) {
            String src = params[0];
            if (src == null || src.isEmpty()) return null;
            java.io.InputStream is = null;
            try {
                if (src.startsWith("/") || src.startsWith("file://")) {
                    String path = src.startsWith("file://") ? src.substring(7) : src;
                    is = new java.io.FileInputStream(path);
                } else {
                    java.net.URL url = new java.net.URL(src);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.connect();
                    is = conn.getInputStream();
                }
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(android.graphics.Bitmap result) {
            if (result != null && target != null) {
                target.setImageBitmap(result);
                target.setVisibility(View.VISIBLE);
                // 重新启动背景动画，让渐变叠加层使用图片模式
                bgRoot.post(() -> {
                    if (bgHandler != null) bgHandler.removeCallbacks(bgAnim);
                    startBgAnimation(gradOverlay, bgImage);
                });
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
        // 生成海报图片（背景色 + 白噪音名称 + 下载二维码 + 宣传文字）
        Toast.makeText(this, "正在生成海报...", Toast.LENGTH_SHORT).show();
        final String soundNameForShare = sound.name;
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.net.Uri imgUri = null;
                try {
                    int w = 720;
                    int h = 1080;
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);

                    int[] themeColors = getThemeColors(sound.themeIndex);
                    android.graphics.LinearGradient grad = new android.graphics.LinearGradient(
                        0, 0, w, h, themeColors[0], themeColors[1], android.graphics.Shader.TileMode.CLAMP);
                    android.graphics.Paint bgPaint = new android.graphics.Paint();
                    bgPaint.setShader(grad);
                    canvas.drawRect(0, 0, w, h, bgPaint);

                    android.graphics.Paint titlePaint = new android.graphics.Paint();
                    titlePaint.setColor(Color.WHITE);
                    titlePaint.setTextSize(72);
                    titlePaint.setFakeBoldText(true);
                    titlePaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText("「" + soundNameForShare + "」", w / 2, 180, titlePaint);

                    android.graphics.Paint subPaint = new android.graphics.Paint();
                    subPaint.setColor(Color.parseColor("#D0D0D0"));
                    subPaint.setTextSize(32);
                    subPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText("戴上耳机，安静整个世界", w / 2, 250, subPaint);

                    String qrText = "https://pic98.oss-cn-beijing.aliyuncs.com/bzy/2.22.0.apk";
                    boolean[][] qr = QRCodeGenerator.generate(qrText);
                    int qrSize = qr.length;
                    int moduleSize = 440 / qrSize;
                    int qrX = (w - moduleSize * qrSize) / 2;
                    int qrY = 380;
                    android.graphics.Paint whitePaint = new android.graphics.Paint();
                    whitePaint.setColor(Color.WHITE);
                    int qrBoxSize = moduleSize * qrSize;
                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                        canvas.drawRoundRect(qrX - 40, qrY - 40, qrX + qrBoxSize + 40, qrY + qrBoxSize + 40, 40, 40, whitePaint);
                    } else {
                        canvas.drawRect(qrX - 40, qrY - 40, qrX + qrBoxSize + 40, qrY + qrBoxSize + 40, whitePaint);
                    }
                    android.graphics.Paint blackPaint = new android.graphics.Paint();
                    blackPaint.setColor(Color.BLACK);
                    for (int y = 0; y < qrSize; y++) {
                        for (int x = 0; x < qrSize; x++) {
                            if (qr[x][y]) {
                                canvas.drawRect(
                                    qrX + x * moduleSize, qrY + y * moduleSize,
                                    qrX + (x + 1) * moduleSize, qrY + (y + 1) * moduleSize,
                                    blackPaint);
                            }
                        }
                    }

                    android.graphics.Paint hintPaint = new android.graphics.Paint();
                    hintPaint.setColor(Color.BLACK);
                    hintPaint.setTextSize(28);
                    hintPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText("扫码下载 App", w / 2, qrY + qrBoxSize + 100, hintPaint);

                    android.graphics.Paint footerPaint = new android.graphics.Paint();
                    footerPaint.setColor(Color.parseColor("#E0E0E0"));
                    footerPaint.setTextSize(26);
                    footerPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText("— 让白噪音陪伴每个需要安静的瞬间 —", w / 2, h - 80, footerPaint);

                    // 保存到应用私有外部目录（免权限）
                    java.io.File imgFile = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                        "poster_" + System.currentTimeMillis() + ".png");
                    java.io.File parent = imgFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(imgFile);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                    bmp.recycle();

                    // 插入到 MediaStore 获取 content:// URI（无需 FileProvider）
                    try {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Images.Media.TITLE, "poster_" + System.currentTimeMillis());
                        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, imgFile.getName());
                        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_PICTURES);
                        }
                        values.put(android.provider.MediaStore.Images.Media.DATA, imgFile.getAbsolutePath());
                        imgUri = getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    } catch (Throwable ignored) {}

                    final String shareText = "我在用「" + soundNameForShare + "」白噪音，一起来听吧！下载："
                        + "https://pic98.oss-cn-beijing.aliyuncs.com/bzy/2.22.0.apk";

                    // 系统分享
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    if (imgUri != null) {
                        shareIntent.setType("image/png");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, imgUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享海报到"));
                    return;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                // 失败兜底：复制文字
                final String content = "我在用「" + soundNameForShare + "」白噪音，一起来听吧！下载：https://pic98.oss-cn-beijing.aliyuncs.com/bzy/2.22.0.apk";
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("分享", content));
                } catch (Throwable ignored) {}
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(ChatActivity.this, "已复制分享链接", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // 获取主题渐变色（与首页主题对应）
    private int[] getThemeColors(int idx) {
        int[][] palettes = {
            {Color.parseColor("#2E3A87"), Color.parseColor("#5B6EE1")},
            {Color.parseColor("#1A4D2E"), Color.parseColor("#4F7942")},
            {Color.parseColor("#0E4D64"), Color.parseColor("#188977")},
            {Color.parseColor("#3E2723"), Color.parseColor("#8D6E63")},
            {Color.parseColor("#1A237E"), Color.parseColor("#5C6BC0")},
            {Color.parseColor("#4A148C"), Color.parseColor("#8E24AA")},
            {Color.parseColor("#3E2723"), Color.parseColor("#795548")},
        };
        return palettes[idx % palettes.length];
    }

    // 显示当前声音详情（名称/类型/声音ID/网络URL/本地路径/背景URL/远程文件大小）
    private void showSoundDetailDialog() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;

        final FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#88000000"));
        dialogWrap.setOnClickListener(v -> bgRoot.removeView(dialogWrap));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(24), dip2px(20), dip2px(24), dip2px(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            dip2px(320), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
        else panel.setBackgroundDrawable(pbg);

        TextView titleTv = new TextView(this);
        titleTv.setText("音乐详情");
        titleTv.setTextSize(16);
        titleTv.setTextColor(textMain);
        titleTv.getPaint().setFakeBoldText(true);
        panel.addView(titleTv);

        // 类型标签
        String typeLabel;
        if (sound.isNetwork) typeLabel = "网络音乐";
        else if (sound.isCustom) typeLabel = "自定义白噪音";
        else typeLabel = "内置白噪音";
        addDetailRow(panel, "类型", typeLabel, textMain, textSub);

        addDetailRow(panel, "名称", sound.name, textMain, textSub);

        addDetailRow(panel, "声音ID", sound.id, textMain, textSub);

        if (sound.url != null && !sound.url.isEmpty()) {
            addDetailRow(panel, "网络地址", sound.url, textMain, textSub);
        }
        if (sound.localPath != null && !sound.localPath.isEmpty()) {
            addDetailRow(panel, "本地路径", sound.localPath, textMain, textSub);
            addDetailRow(panel, "文件大小", SoundStore.formatFileSize(sound.fileSize), textMain, textSub);
        } else if (sound.url != null && !sound.url.isEmpty()) {
            // 远程音乐：异步 HEAD 获取远程文件大小
            final TextView remoteSizeTv = addDetailRow(panel, "远程文件大小", "正在获取...", textMain, textSub);
            final String remoteUrl = sound.url;
            new Thread() {
                @Override public void run() {
                    final long remoteSize = SoundStore.getRemoteFileSize(remoteUrl);
                    runOnUiThread(() -> {
                        if (remoteSize > 0) {
                            remoteSizeTv.setText(SoundStore.formatFileSize(remoteSize));
                        } else {
                            remoteSizeTv.setText("获取失败（服务器未返回 Content-Length）");
                        }
                    });
                }
            }.start();
            addDetailRow(panel, "播放方式", "通过网络URL直接播放", textMain, textSub);
        }
        if (sound.bgImageUrl != null && !sound.bgImageUrl.isEmpty()) {
            addDetailRow(panel, "背景图网络地址", sound.bgImageUrl, textMain, textSub);
        }
        if (sound.bgImageLocalPath != null && !sound.bgImageLocalPath.isEmpty()) {
            addDetailRow(panel, "背景图本地路径", sound.bgImageLocalPath, textMain, textSub);
        }
        if (sound.resId > 0) {
            addDetailRow(panel, "资源ID", "内置资源 (" + sound.resId + ")", textMain, textSub);
        }

        Button closeBtn = new Button(this);
        closeBtn.setText("关闭");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#07C160"));
        closeBtn.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams btnlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnlp.topMargin = dip2px(16);
        closeBtn.setLayoutParams(btnlp);
        closeBtn.setOnClickListener(v -> bgRoot.removeView(dialogWrap));
        panel.addView(closeBtn);

        dialogWrap.addView(panel);
        bgRoot.addView(dialogWrap);
    }

    private TextView addDetailRow(LinearLayout panel, String label, String value, int textMain, int textSub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dip2px(10), 0, 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView labelTv = new TextView(this);
        labelTv.setText(label + "：");
        labelTv.setTextSize(13);
        labelTv.setTextColor(textSub);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(13);
        valueTv.setTextColor(textMain);
        valueTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelTv);
        row.addView(valueTv);
        panel.addView(row);
        return valueTv;
    }

    // 更多菜单（清除历史、分享、生成背景图）
    private void showMoreMenu(View anchor) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int lineColor = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");

        final FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#88000000"));
        dialogWrap.setOnClickListener(v -> bgRoot.removeView(dialogWrap));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            dip2px(240), FrameLayout.LayoutParams.WRAP_CONTENT);
        // 定位在顶部栏右下
        plp.gravity = Gravity.TOP | Gravity.END;
        plp.topMargin = dip2px(52);
        plp.rightMargin = dip2px(12);
        panel.setLayoutParams(plp);
        panel.setOnClickListener(v -> {}); // 自己消费点击，不让它穿透给 dialogWrap
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
        else panel.setBackgroundDrawable(pbg);

        String[] items = new String[] { "📋  详情", "🧹  清除历史", "📤  分享", "🖼  生成背景图", "🔧  调试日志" };
        for (int i = 0; i < items.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(items[i]);
            tv.setTextSize(15);
            tv.setTextColor(textMain);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(dip2px(16), dip2px(14), dip2px(16), dip2px(14));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(tlp);
            final int idx = i;
            tv.setOnClickListener(v -> {
                bgRoot.removeView(dialogWrap);
                if (idx == 0) showSoundDetailDialog();
                else if (idx == 1) showClearHistoryDialog();
                else if (idx == 2) doShare();
                else if (idx == 3) showArtDialog();
                else showDebugLogDialog();
            });
            panel.addView(tv);
            if (i < items.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(lineColor);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
                line.setLayoutParams(llp);
                panel.addView(line);
            }
        }
        dialogWrap.addView(panel);
        bgRoot.addView(dialogWrap);
    }

    // 清除聊天记录（从顶部"更多"里呼出）
    private void showClearHistoryDialog() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");

        if (messages == null || messages.isEmpty()) {
            Toast.makeText(this, "聊天记录已经是空的了", Toast.LENGTH_SHORT).show();
            return;
        }
        final FrameLayout confirmWrap = new FrameLayout(this);
        confirmWrap.setBackgroundColor(Color.parseColor("#AA000000"));
        FrameLayout.LayoutParams cwLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        confirmWrap.setLayoutParams(cwLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(dark ? Color.parseColor("#1e1e1e") : Color.WHITE);
        panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            dip2px(280), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(dark ? Color.parseColor("#1e1e1e") : Color.WHITE);
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
            msgContainer.removeAllViews();
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
    }

    // 调试日志弹窗（每秒刷新，显示最近10条HTTP请求）
    private void showDebugLogDialog() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int logBg = dark ? Color.parseColor("#121212") : Color.parseColor("#F5F5F5");
        int logText = dark ? Color.parseColor("#10B981") : Color.parseColor("#1A7F32");

        final FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#88000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(16), dip2px(16), dip2px(16), dip2px(16));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dip2px(340), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
        else panel.setBackgroundDrawable(pbg);

        // 标题栏
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleRow.setLayoutParams(trlp);

        TextView title = new TextView(this);
        title.setText("🔧 HTTP 调试日志");
        title.setTextSize(16);
        title.setTextColor(textMain);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(title);

        Button clearBtn = new Button(this);
        clearBtn.setText("清空");
        clearBtn.setTextSize(12);
        clearBtn.setTextColor(Color.parseColor("#FF6B6B"));
        clearBtn.setBackgroundColor(Color.TRANSPARENT);
        clearBtn.setOnClickListener(v -> HttpLogger.clear());
        titleRow.addView(clearBtn);
        panel.addView(titleRow);

        // 日志文本区
        final ScrollView logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(logBg);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(240));
        lsp.topMargin = dip2px(10);
        logScroll.setLayoutParams(lsp);

        final TextView logTextView = new TextView(this);
        logTextView.setTextSize(11);
        logTextView.setTextColor(logText);
        logTextView.setPadding(dip2px(8), dip2px(8), dip2px(8), dip2px(8));
        logTextView.setText("暂无日志...\n\n提示：点击任意HTTP请求后重新进入聊天，刷新页面可触发新的HTTP调用");
        logScroll.addView(logTextView);
        panel.addView(logScroll);

        // 刷新提示
        TextView hint = new TextView(this);
        hint.setText("每秒自动刷新");
        hint.setTextSize(11);
        hint.setTextColor(textSub);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dip2px(8), 0, 0);
        panel.addView(hint);

        // 关闭按钮
        Button closeBtn = new Button(this);
        closeBtn.setText("关闭");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(Color.parseColor("#10AEFF"));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp2.topMargin = dip2px(8);
        closeBtn.setLayoutParams(clp2);
        final android.os.Handler refreshHandler = new android.os.Handler();
        closeBtn.setOnClickListener(v -> {
            refreshHandler.removeCallbacksAndMessages(null);
            bgRoot.removeView(dialogWrap);
        });
        panel.addView(closeBtn);

        dialogWrap.addView(panel);
        bgRoot.addView(dialogWrap);

        // 每秒刷新日志
        refreshHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (dialogWrap.getParent() == null) {
                    refreshHandler.removeCallbacks(this);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
                for (HttpLogger.HttpLogEntry entry : HttpLogger.getLogs()) {
                    String time = sdf.format(new java.util.Date(entry.timestamp));
                    String code = entry.responseCode > 0 ? String.valueOf(entry.responseCode) : "ERR";
                    String err = entry.error != null ? " ⚠️" + entry.error : "";
                    String shortUrl = entry.url.length() > 60 ? entry.url.substring(0, 60) + "..." : entry.url;
                    sb.append("[").append(time).append("] ")
                        .append(entry.method).append(" ").append(code)
                        .append(" ").append(entry.durationMs).append("ms").append(err).append("\n")
                        .append(shortUrl).append("\n\n");
                }
                if (sb.length() == 0) {
                    logTextView.setText("暂无日志...\n\n提示：进行聊天交互或生成背景图后会记录HTTP请求");
                } else {
                    logTextView.setText(sb.toString());
                    // 滚动到底部
                    logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
                }
                refreshHandler.postDelayed(this, 1000);
            }
        }, 500); // 首次延迟0.5秒立即刷新
    }

    // 智能配图弹窗
    private void showArtDialog() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#B0B0B0") : Color.parseColor("#666666");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.parseColor("#FFFFFF");
        int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F0F0F0");

        final FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#AA000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dip2px(320), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
        else panel.setBackgroundDrawable(pbg);

        TextView ptitle = new TextView(this);
        ptitle.setText("智能配图");
        ptitle.setTextSize(18);
        ptitle.setTextColor(textMain);
        ptitle.getPaint().setFakeBoldText(true);
        panel.addView(ptitle);

        TextView pdesc = new TextView(this);
        pdesc.setText("输入描述让智谱AI为「" + sound.name + "」生成专属背景图。");
        pdesc.setTextSize(13);
        pdesc.setTextColor(textSub);
        pdesc.setPadding(0, dip2px(10), 0, dip2px(12));
        panel.addView(pdesc);

        // 输入框（描述）
        final EditText descInput = new EditText(this);
        descInput.setHint("描述想要的画面（如：雨夜书房、海浪黄昏、森林鸟鸣）");
        descInput.setText(sound.name);
        descInput.setTextSize(14);
        descInput.setTextColor(textMain);
        descInput.setHintTextColor(dark ? Color.parseColor("#606060") : Color.parseColor("#CCCCCC"));
        descInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        GradientDrawable inpBg = new GradientDrawable();
        inpBg.setColor(inputBg);
        inpBg.setCornerRadius(dip2px(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) descInput.setBackground(inpBg);
        else descInput.setBackgroundDrawable(inpBg);
        LinearLayout.LayoutParams inpLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inpLp.bottomMargin = dip2px(12);
        descInput.setLayoutParams(inpLp);
        panel.addView(descInput);

        // 图片预览区域
        final FrameLayout previewWrap = new FrameLayout(this);
        previewWrap.setBackgroundColor(inputBg);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(180));
        previewLp.bottomMargin = dip2px(12);
        previewWrap.setLayoutParams(previewLp);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(inputBg);
        previewBg.setCornerRadius(dip2px(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) previewWrap.setBackground(previewBg);
        else previewWrap.setBackgroundDrawable(previewBg);

        final ImageView previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        previewImage.setVisibility(View.GONE);
        previewWrap.addView(previewImage);

        final TextView previewPlaceholder = new TextView(this);
        previewPlaceholder.setText("图片预览区域");
        previewPlaceholder.setTextSize(13);
        previewPlaceholder.setTextColor(textSub);
        previewPlaceholder.setGravity(Gravity.CENTER);
        previewPlaceholder.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        previewWrap.addView(previewPlaceholder);

        panel.addView(previewWrap);

        // 状态文字
        final TextView statusText = new TextView(this);
        statusText.setText("");
        statusText.setTextSize(12);
        statusText.setTextColor(Color.parseColor("#5B8DEF"));
        statusText.setPadding(0, 0, 0, dip2px(8));
        panel.addView(statusText);

        // 按钮：一行一个，垂直排列
        // 生成按钮
        Button aiBtn = new Button(this);
        aiBtn.setText("✨ 智谱AI 生成");
        aiBtn.setTextSize(14);
        aiBtn.setTextColor(Color.WHITE);
        aiBtn.setBackgroundColor(Color.parseColor("#5B8DEF"));
        aiBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        aiLp.bottomMargin = dip2px(8);
        aiBtn.setLayoutParams(aiLp);
        aiBtn.setOnClickListener(v -> {
            final String desc = descInput.getText().toString().trim();
            if (desc.isEmpty()) {
                Toast.makeText(ChatActivity.this, "请先输入画面描述", Toast.LENGTH_SHORT).show();
                return;
            }
            statusText.setText("智谱AI正在生成图片，请稍候...");
            previewImage.setVisibility(View.GONE);
            previewPlaceholder.setText("生成中...");
            previewPlaceholder.setVisibility(View.VISIBLE);
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String imgUrl = AI.generateImage(desc);
                        final String foundUrl = (imgUrl != null && !imgUrl.isEmpty()) ? imgUrl : null;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (foundUrl != null) {
                                    statusText.setText("图片已生成，点击确认应用");
                                    previewPlaceholder.setVisibility(View.GONE);
                                    previewImage.setVisibility(View.VISIBLE);
                                    // 异步加载图片到预览
                                    new LoadBgImageTask(previewImage).execute(foundUrl);
                                    // 记录当前 URL 供确认使用
                                    previewImage.setTag(foundUrl);
                                } else {
                                    statusText.setText("生成失败，请稍后重试");
                                    previewPlaceholder.setText("生成失败");
                                }
                            }
                        });
                    } catch (Throwable e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                statusText.setText("请求异常: " + e.getMessage());
                                previewPlaceholder.setText("生成失败");
                            }
                        });
                    }
                }
            }).start();
        });
        panel.addView(aiBtn);

        // 确认按钮
        Button okBtn = new Button(this);
        okBtn.setText("确认应用");
        okBtn.setTextSize(14);
        okBtn.setTextColor(Color.WHITE);
        okBtn.setBackgroundColor(Color.parseColor("#07C160"));
        okBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        okLp.bottomMargin = dip2px(8);
        okBtn.setLayoutParams(okLp);
        okBtn.setOnClickListener(v -> {
            Object tag = previewImage.getTag();
            String url = (tag instanceof String) ? (String) tag : null;
            if (url == null || url.isEmpty()) {
                Toast.makeText(ChatActivity.this, "请先生成图片", Toast.LENGTH_SHORT).show();
                return;
            }
            sound.bgImageUrl = url;
            new LoadBgImageTask(bgImage).execute(url);
            new Thread(new Runnable() {
                @Override public void run() {
                    SoundStore.setBgImageUrl(ChatActivity.this, soundId, url);
                }
            }).start();
            Toast.makeText(ChatActivity.this, "背景图已更新", Toast.LENGTH_SHORT).show();
            bgRoot.removeView(dialogWrap);
        });
        panel.addView(okBtn);

        // 生成背景图视频按钮（仅在已有背景图时可点击）
        Button videoBtn = new Button(this);
        videoBtn.setText("🎬 生成背景图视频");
        videoBtn.setTextSize(14);
        videoBtn.setTextColor(Color.WHITE);
        videoBtn.setBackgroundColor(Color.parseColor("#FF6B6B"));
        videoBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        videoLp.bottomMargin = dip2px(8);
        videoBtn.setLayoutParams(videoLp);
        videoBtn.setOnClickListener(v -> {
            // 获取背景图URL（优先预览区，其次sound.bgImageUrl）
            Object tag = previewImage.getTag();
            String bgUrl = (tag instanceof String) ? (String) tag : sound.bgImageUrl;
            if (bgUrl == null || bgUrl.isEmpty()) {
                Toast.makeText(ChatActivity.this, "请先生成或确认背景图", Toast.LENGTH_SHORT).show();
                return;
            }
            statusText.setText("正在提交视频生成任务...");
            new Thread(new Runnable() {
                @Override public void run() {
                    // 提交视频生成任务
                    final AI.VideoResult submitResult = AI.submitVideoTask(bgUrl, "画面缓缓流动，柔和自然，舒缓治愈");
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (submitResult.error != null) {
                                statusText.setText("提交失败: " + submitResult.error);
                                return;
                            }
                            statusText.setText("任务已提交: " + submitResult.taskId + "\n后台生成中，请关注聊天窗口");
                            // 开始轮询
                            pollVideoTask(submitResult.taskId);
                        }
                    });
                }
            }).start();
        });
        panel.addView(videoBtn);

        // 取消按钮
        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(14);
        cancelBtn.setTextColor(Color.parseColor("#888888"));
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        cancelBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cancelBtn.setOnClickListener(v -> bgRoot.removeView(dialogWrap));
        panel.addView(cancelBtn);

        dialogWrap.addView(panel);
        bgRoot.addView(dialogWrap);
    }

    // 轮询视频生成任务，完成后在聊天窗口通知结果
    private void pollVideoTask(final String taskId) {
        final android.os.Handler handler = new android.os.Handler();
        final int maxRetries = 10;    // 最多轮询10次
        final int delayMs = 60000;    // 每1分钟查询一次
        final int[] retryCount = {0};

        Runnable pollRunnable = new Runnable() {
            @Override public void run() {
                if (retryCount[0] >= maxRetries) {
                    addMessage("视频生成超时（任务ID: " + taskId + "），请稍后重试。", false);
                    return;
                }
                retryCount[0]++;
                new Thread(new Runnable() {
                    @Override public void run() {
                        final AI.VideoResult result = AI.queryVideoResult(taskId);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (result.success) {
                                    addMessage("视频生成成功！任务ID: " + taskId + "\n" + result.videoUrl, false);
                                    // 展示视频播放对话框（如果用户还开着页面）
                                    showVideoResultDialog(result.videoUrl, null);
                                } else if (result.error != null) {
                                    addMessage("视频生成失败（任务ID: " + taskId + "）: " + result.error, false);
                                } else {
                                    // 继续轮询
                                    handler.postDelayed(this, delayMs);
                                }
                            }
                        });
                    }
                }).start();
            }
        };
        handler.post(pollRunnable);
    }

    // 展示视频生成结果对话框（可播放/下载）
    private void showVideoResultDialog(final String videoUrl, final FrameLayout parentDialogWrap) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;

        final FrameLayout videoWrap = new FrameLayout(this);
        videoWrap.setBackgroundColor(Color.parseColor("#AA000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dip2px(320), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(12));
        panel.setBackground(pbg);

        TextView title = new TextView(this);
        title.setText("背景图视频生成完成");
        title.setTextSize(16);
        title.setTextColor(textMain);
        title.getPaint().setFakeBoldText(true);
        panel.addView(title);

        TextView desc = new TextView(this);
        desc.setText("视频已生成，点击下方按钮播放或下载。");
        desc.setTextSize(13);
        desc.setTextColor(dark ? Color.parseColor("#B0B0B0") : Color.parseColor("#666666"));
        desc.setPadding(0, dip2px(10), 0, dip2px(12));
        panel.addView(desc);

        // 播放按钮
        Button playBtn = new Button(this);
        playBtn.setText("▶ 播放视频");
        playBtn.setTextSize(14);
        playBtn.setTextColor(Color.WHITE);
        playBtn.setBackgroundColor(Color.parseColor("#5B8DEF"));
        playBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.bottomMargin = dip2px(8);
        playBtn.setLayoutParams(playLp);
        playBtn.setOnClickListener(v -> {
            // 使用系统播放器打开视频URL
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(android.net.Uri.parse(videoUrl), "video/mp4");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(ChatActivity.this, "无法播放视频: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        panel.addView(playBtn);

        // 下载按钮
        Button downloadBtn = new Button(this);
        downloadBtn.setText("⬇ 下载视频");
        downloadBtn.setTextSize(14);
        downloadBtn.setTextColor(Color.WHITE);
        downloadBtn.setBackgroundColor(Color.parseColor("#07C160"));
        downloadBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlLp.bottomMargin = dip2px(8);
        downloadBtn.setLayoutParams(dlLp);
        downloadBtn.setOnClickListener(v -> {
            // 先保存视频URL到SoundStore（即使未下载也能播放网络URL）
            sound.bgVideoUrl = videoUrl;
            SoundStore.setBgVideoUrl(ChatActivity.this, soundId, videoUrl);
            // 下载到本地
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        java.net.URL url = new java.net.URL(videoUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        java.io.InputStream is = conn.getInputStream();
                        java.io.File dir = new java.io.File(getExternalFilesDir(null), "videos");
                        if (!dir.exists()) dir.mkdirs();
                        String filename = "bg_video_" + sound.id + "_" + System.currentTimeMillis() + ".mp4";
                        java.io.File outFile = new java.io.File(dir, filename);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        fos.close();
                        is.close();
                        conn.disconnect();
                        // 保存本地路径到SoundStore
                        sound.bgVideoLocalPath = outFile.getAbsolutePath();
                        SoundStore.setBgVideoLocalPath(ChatActivity.this, soundId, outFile.getAbsolutePath());
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(ChatActivity.this, "视频已保存: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                                // 如果当前设置是视频优先模式，立即加载视频背景
                                int bgMode = getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                                    .getInt("bg_display_mode", 0);
                                if (bgMode == 1) {
                                    loadBackgroundVideo(outFile.getAbsolutePath());
                                }
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(ChatActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
        });
        panel.addView(downloadBtn);

        // 关闭按钮
        Button closeBtn = new Button(this);
        closeBtn.setText("关闭");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(Color.parseColor("#888888"));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(dip2px(16), dip2px(10), dip2px(16), dip2px(10));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        closeBtn.setOnClickListener(v -> {
            bgRoot.removeView(videoWrap);
            if (parentDialogWrap != null && parentDialogWrap.getParent() != null) {
                bgRoot.removeView(parentDialogWrap);
            }
        });
        panel.addView(closeBtn);

        videoWrap.addView(panel);
        bgRoot.addView(videoWrap);
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
