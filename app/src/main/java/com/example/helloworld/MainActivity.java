package com.example.helloworld;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String DOWNLOAD_URL = "https://ai.install.ren/bzy.apk";
    private static final String VERSION = "v2.2-bgplay";

    private static final String[] DEFAULT_NAMES = {"雨声", "海浪", "森林", "风声", "篝火"};
    private static final int[] DEFAULT_RES = {
        R.raw.rain, R.raw.ocean, R.raw.forest, R.raw.wind, R.raw.campfire
    };

    private List<SoundItem> soundList = new ArrayList<>();
    private int currentIndex = 0;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private boolean bgPlayEnabled = false;

    private FlowLightView flowLightView;
    private TextView sceneTitle;
    private Button playPauseBtn;
    private LinearLayout soundContainer;

    private SharedPreferences prefs;
    private static final String PREFS_NAME = "whitenoise_prefs";
    private static final String KEY_SOUNDS = "custom_sounds";
    private static final String KEY_BG_PLAY = "bg_play_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bgPlayEnabled = prefs.getBoolean(KEY_BG_PLAY, false);
        loadSounds();

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        flowLightView = new FlowLightView(this);
        flowLightView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(flowLightView);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(40, 60, 40, 60);
        scrollView.addView(content);

        // Title
        sceneTitle = new TextView(this);
        sceneTitle.setText(getCurrentSound().name);
        sceneTitle.setTextSize(32);
        sceneTitle.setTextColor(Color.WHITE);
        sceneTitle.setGravity(Gravity.CENTER);
        sceneTitle.setShadowLayer(6, 2, 2, Color.BLACK);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dip2px(30));
        sceneTitle.setLayoutParams(titleParams);
        content.addView(sceneTitle);

        // Sound list (horizontal scroll)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        hsv.setHorizontalScrollBarEnabled(false);

        soundContainer = new LinearLayout(this);
        soundContainer.setOrientation(LinearLayout.HORIZONTAL);
        soundContainer.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dip2px(8);
        soundContainer.setPadding(padH, 0, padH, 0);
        hsv.addView(soundContainer);
        content.addView(hsv);

        rebuildSoundButtons();

        // Play/Pause button
        playPauseBtn = new Button(this);
        playPauseBtn.setText("▶ 开始播放");
        playPauseBtn.setTextSize(18);
        playPauseBtn.setTextColor(Color.WHITE);
        GradientDrawable playBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#6200EE"), Color.parseColor("#9C27B0")});
        playBg.setCornerRadius(dip2px(28));
        playPauseBtn.setBackground(playBg);
        playPauseBtn.setShadowLayer(8, 0, 4, Color.parseColor("#40000000"));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
            dip2px(220), dip2px(56));
        playParams.setMargins(0, dip2px(24), 0, dip2px(20));
        playPauseBtn.setLayoutParams(playParams);
        playPauseBtn.setOnClickListener(v -> togglePlayPause());
        content.addView(playPauseBtn);

        // Add + Manage buttons row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.setMargins(0, dip2px(16), 0, dip2px(16));
        btnRow.setLayoutParams(btnRowParams);

        Button addSoundBtn = new Button(this);
        addSoundBtn.setText("+ 添加");
        addSoundBtn.setTextSize(14);
        addSoundBtn.setTextColor(Color.WHITE);
        GradientDrawable addBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#3700B3"), Color.parseColor("#6200EE")});
        addBg.setCornerRadius(dip2px(24));
        addSoundBtn.setBackground(addBg);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
            0, dip2px(48), 1);
        addParams.setMargins(0, 0, dip2px(8), 0);
        addSoundBtn.setLayoutParams(addParams);
        addSoundBtn.setOnClickListener(v -> showAddSoundDialog());
        btnRow.addView(addSoundBtn);

        Button manageBtn = new Button(this);
        manageBtn.setText("⚙ 管理");
        manageBtn.setTextSize(14);
        manageBtn.setTextColor(Color.WHITE);
        GradientDrawable manageBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#424242"), Color.parseColor("#616161")});
        manageBg.setCornerRadius(dip2px(24));
        manageBtn.setBackground(manageBg);
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
            0, dip2px(48), 1);
        manageParams.setMargins(dip2px(8), 0, 0, 0);
        manageBtn.setLayoutParams(manageParams);
        manageBtn.setOnClickListener(v -> showManageCustomDialog());
        btnRow.addView(manageBtn);

        content.addView(btnRow);

        // Settings button
        Button settingsBtn = new Button(this);
        settingsBtn.setText("⚙ 设置");
        settingsBtn.setTextSize(14);
        settingsBtn.setTextColor(Color.WHITE);
        GradientDrawable settingsBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#303F9F"), Color.parseColor("#5C6BC0")});
        settingsBg.setCornerRadius(dip2px(24));
        settingsBtn.setBackground(settingsBg);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dip2px(48));
        settingsParams.setMargins(0, dip2px(8), 0, 0);
        settingsBtn.setLayoutParams(settingsParams);
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        content.addView(settingsBtn);

        // Share poster button
        Button shareBtn = new Button(this);
        shareBtn.setText("📤 分享海报");
        shareBtn.setTextSize(15);
        shareBtn.setTextColor(Color.WHITE);
        GradientDrawable shareBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#03DAC5"), Color.parseColor("#018786")});
        shareBg.setCornerRadius(dip2px(24));
        shareBtn.setBackground(shareBg);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
            dip2px(180), dip2px(48));
        shareParams.setMargins(0, dip2px(16), 0, 0);
        shareBtn.setLayoutParams(shareParams);
        shareBtn.setOnClickListener(v -> sharePoster());
        content.addView(shareBtn);

        // Status text
        TextView statusText = new TextView(this);
        statusText.setText("5种场景白噪音 · 循环播放");
        statusText.setTextSize(11);
        statusText.setTextColor(Color.parseColor("#AAAAAA"));
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dip2px(16), 0, 0);
        statusText.setLayoutParams(statusParams);
        content.addView(statusText);

        root.addView(scrollView);
        setContentView(root);

        flowLightView.setScene(0);
        flowLightView.start();
    }

    private void loadSounds() {
        soundList.clear();
        nextCustomId = 0;
        for (int i = 0; i < DEFAULT_NAMES.length; i++) {
            soundList.add(new SoundItem(DEFAULT_NAMES[i], DEFAULT_RES[i], false));
        }
        try {
            String json = prefs.getString(KEY_SOUNDS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                SoundItem item = new SoundItem(nextCustomId++,
                    obj.getString("name"), obj.getString("url"));
                soundList.add(item);
            }
        } catch (Exception ignored) {}
    }

    private void saveSounds() {
        try {
            JSONArray arr = new JSONArray();
            for (SoundItem item : soundList) {
                if (item.isCustom) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", item.name);
                    obj.put("url", item.url);
                    arr.put(obj);
                }
            }
            prefs.edit().putString(KEY_SOUNDS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private SoundItem getCurrentSound() {
        if (currentIndex >= 0 && currentIndex < soundList.size()) {
            return soundList.get(currentIndex);
        }
        return soundList.get(0);
    }

    private void rebuildSoundButtons() {
        soundContainer.removeAllViews();
        for (int i = 0; i < soundList.size(); i++) {
            final int idx = i;
            SoundItem item = soundList.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dip2px(20), dip2px(14), dip2px(20), dip2px(14));

            GradientDrawable bg;
            if (i == currentIndex) {
                bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor("#6200EE"), Color.parseColor("#9C27B0")});
            } else {
                bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor("#2D2D2D"), Color.parseColor("#1A1A1A")});
            }
            bg.setCornerRadius(dip2px(20));
            if (i == currentIndex) {
                bg.setStroke(dip2px(2), Color.parseColor("#BB86FC"));
            }
            card.setBackground(bg);

            TextView nameLabel = new TextView(this);
            nameLabel.setText(item.name + (item.isCustom ? " ✎" : ""));
            nameLabel.setTextSize(15);
            nameLabel.setTextColor(i == currentIndex ? Color.WHITE : Color.parseColor("#CCCCCC"));
            nameLabel.setGravity(Gravity.CENTER);

            card.addView(nameLabel);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dip2px(6), 0, dip2px(6), 0);
            card.setLayoutParams(params);
            card.setOnClickListener(v -> selectSound(idx));
            soundContainer.addView(card);
        }
    }

    private void selectSound(int index) {
        currentIndex = index;
        sceneTitle.setText(getCurrentSound().name);
        flowLightView.setScene(index % 5);
        rebuildSoundButtons();
        if (isPlaying) {
            stopSound();
            playSound();
        }
    }

    private void togglePlayPause() {
        if (isPlaying) {
            stopSound();
            stopBgService();
            playPauseBtn.setText("▶ 开始播放");
            isPlaying = false;
        } else {
            playSound();
            playPauseBtn.setText("⏸ 暂停播放");
            isPlaying = true;
        }
    }

    private void playSound() {
        try {
            stopSound();
            SoundItem item = getCurrentSound();

            if (bgPlayEnabled) {
                // 后台模式：使用前台Service播放
                Intent serviceIntent = new Intent(this, WhiteNoiseService.class);
                serviceIntent.setAction(WhiteNoiseService.ACTION_PLAY);
                serviceIntent.putExtra(WhiteNoiseService.EXTRA_SOUND_NAME, item.name);
                if (item.isCustom) {
                    serviceIntent.putExtra(WhiteNoiseService.EXTRA_IS_CUSTOM, true);
                    serviceIntent.putExtra(WhiteNoiseService.EXTRA_URL, item.url);
                } else {
                    serviceIntent.putExtra(WhiteNoiseService.EXTRA_IS_CUSTOM, false);
                    serviceIntent.putExtra(WhiteNoiseService.EXTRA_RES_ID, item.resId);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                // 前台模式：使用Activity内的MediaPlayer
                if (item.isCustom && item.url != null && item.url.startsWith("http")) {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(item.url);
                    mediaPlayer.setLooping(true);
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setOnPreparedListener(mp -> mp.start());
                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        Toast.makeText(this, "自定义音频播放失败，请检查URL", Toast.LENGTH_SHORT).show();
                        isPlaying = false;
                        playPauseBtn.setText("▶ 开始播放");
                        return true;
                    });
                } else {
                    mediaPlayer = MediaPlayer.create(this, item.resId);
                    if (mediaPlayer != null) {
                        mediaPlayer.setLooping(true);
                        mediaPlayer.start();
                    } else {
                        Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
                        isPlaying = false;
                        playPauseBtn.setText("▶ 开始播放");
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "播放错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isPlaying = false;
            playPauseBtn.setText("▶ 开始播放");
        }
    }

    private void stopSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void stopBgService() {
        Intent serviceIntent = new Intent(this, WhiteNoiseService.class);
        serviceIntent.setAction(WhiteNoiseService.ACTION_STOP);
        try {
            startService(serviceIntent);
        } catch (Exception ignored) {}
    }

    private void showSettingsDialog() {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dip2px(24), dip2px(20), dip2px(24), dip2px(20));

        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setColor(Color.parseColor("#DD1A1A2E"));
        dialogBg.setCornerRadius(dip2px(16));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            dialog.setBackground(dialogBg);
        } else {
            dialog.setBackgroundDrawable(dialogBg);
        }

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dip2px(16));
        dialog.addView(title);

        // 后台播放设置行
        LinearLayout bgRow = new LinearLayout(this);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        bgRow.setGravity(Gravity.CENTER_VERTICAL);
        bgRow.setPadding(dip2px(16), dip2px(14), dip2px(16), dip2px(14));

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Color.parseColor("#2A2A3E"));
        rowBg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            bgRow.setBackground(rowBg);
        } else {
            bgRow.setBackgroundDrawable(rowBg);
        }

        LinearLayout.LayoutParams bgRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bgRowParams.setMargins(0, dip2px(8), 0, dip2px(8));
        bgRow.setLayoutParams(bgRowParams);

        // 文字
        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView bgTitle = new TextView(this);
        bgTitle.setText("后台播放");
        bgTitle.setTextSize(16);
        bgTitle.setTextColor(Color.WHITE);
        textWrap.addView(bgTitle);

        TextView bgDesc = new TextView(this);
        bgDesc.setText("关闭App后仍可继续播放，适合助眠等场景");
        bgDesc.setTextSize(12);
        bgDesc.setTextColor(Color.parseColor("#AAAAAA"));
        bgDesc.setPadding(0, dip2px(4), 0, 0);
        textWrap.addView(bgDesc);

        bgRow.addView(textWrap);

        // 开关
        Switch bgSwitch = new Switch(this);
        bgSwitch.setChecked(bgPlayEnabled);
        bgSwitch.setTextColor(Color.WHITE);
        bgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            bgPlayEnabled = isChecked;
            prefs.edit().putBoolean(KEY_BG_PLAY, isChecked).apply();
            Toast.makeText(this, isChecked ? "已开启后台播放" : "已关闭后台播放",
                Toast.LENGTH_SHORT).show();
            // 如果当前正在播放且切换到后台模式，需要重启播放以切换到Service
            if (isPlaying) {
                Toast.makeText(this, "请先停止再播放以应用新设置", Toast.LENGTH_SHORT).show();
            }
        });
        bgRow.addView(bgSwitch);

        dialog.addView(bgRow);

        // 关闭按钮
        Button closeBtn = new Button(this);
        closeBtn.setText("关闭");
        closeBtn.setTextSize(14);
        closeBtn.setTextColor(Color.WHITE);
        GradientDrawable closeBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#6200EE"), Color.parseColor("#9C27B0")});
        closeBg.setCornerRadius(dip2px(20));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            closeBtn.setBackground(closeBg);
        } else {
            closeBtn.setBackgroundDrawable(closeBg);
        }

        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.setMargins(0, dip2px(20), 0, 0);
        closeParams.gravity = Gravity.CENTER;
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setPadding(dip2px(30), dip2px(8), dip2px(30), dip2px(8));

        dialog.addView(closeBtn);

        // 容器
        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setBackgroundColor(Color.parseColor("#AA000000"));
        FrameLayout.LayoutParams wrapParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        wrapper.setLayoutParams(wrapParams);

        LinearLayout dialogWrapper = new LinearLayout(this);
        dialogWrapper.setOrientation(LinearLayout.VERTICAL);
        dialogWrapper.setLayoutParams(new LinearLayout.LayoutParams(
            dip2px(320), LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogWrapper.addView(dialog);

        wrapper.addView(dialogWrapper);
        container.addView(wrapper);
        container.setVisibility(View.VISIBLE);

        closeBtn.setOnClickListener(v -> container.setVisibility(View.GONE));

        ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        root.addView(container);
    }

    private void showAddSoundDialog() {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dip2px(24), dip2px(16), dip2px(24), dip2px(16));
        dialog.setBackgroundColor(Color.parseColor("#DD1A1A2E"));

        TextView title = new TextView(this);
        title.setText("添加自定义白噪音");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dip2px(16));
        dialog.addView(title);

        EditText nameInput = new EditText(this);
        nameInput.setHint("名称（例如：咖啡馆）");
        nameInput.setHintTextColor(Color.parseColor("#888888"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setBackgroundColor(Color.parseColor("#333333"));
        nameInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.addView(nameInput);

        EditText urlInput = new EditText(this);
        urlInput.setHint("音频URL (https://...)");
        urlInput.setHintTextColor(Color.parseColor("#888888"));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setBackgroundColor(Color.parseColor("#333333"));
        urlInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        urlInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.addView(urlInput);

        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setBackgroundColor(Color.parseColor("#AA000000"));
        FrameLayout.LayoutParams wrapParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        wrapper.setLayoutParams(wrapParams);
        wrapper.addView(dialog);

        Button confirmBtn = new Button(this);
        confirmBtn.setText("确认添加");
        confirmBtn.setTextColor(Color.WHITE);
        confirmBtn.setBackgroundColor(Color.parseColor("#6200EE"));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmParams.setMargins(0, dip2px(20), 0, dip2px(8));
        confirmBtn.setLayoutParams(confirmParams);
        confirmBtn.setPadding(dip2px(24), dip2px(10), dip2px(24), dip2px(10));
        dialog.addView(confirmBtn);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextColor(Color.parseColor("#AAAAAA"));
        cancelBtn.setBackgroundColor(Color.parseColor("#444444"));
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        cancelBtn.setPadding(dip2px(24), dip2px(8), dip2px(24), dip2px(8));
        dialog.addView(cancelBtn);

        container.addView(wrapper);
        container.setVisibility(View.VISIBLE);

        final FrameLayout finalContainer = container;
        confirmBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "请填写名称和URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "URL必须以http://或https://开头", Toast.LENGTH_SHORT).show();
                return;
            }
            soundList.add(new SoundItem(nextCustomId++, name, url));
            saveSounds();
            rebuildSoundButtons();
            Toast.makeText(this, "已添加: " + name, Toast.LENGTH_SHORT).show();
            finalContainer.setVisibility(View.GONE);
        });

        cancelBtn.setOnClickListener(v -> finalContainer.setVisibility(View.GONE));

        ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        root.addView(container);
    }

    private void sharePoster() {
        Toast.makeText(this, "正在生成海报...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=" +
                    java.net.URLEncoder.encode(DOWNLOAD_URL, "UTF-8");
                java.net.URL url = new java.net.URL(qrApiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoInput(true);
                conn.connect();
                java.io.InputStream is = conn.getInputStream();
                Bitmap qrBitmap = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();

                Bitmap poster = generatePoster(qrBitmap);
                File cacheDir = new File(getCacheDir(), "images");
                cacheDir.mkdirs();
                File file = new File(cacheDir, "poster.png");
                FileOutputStream out = new FileOutputStream(file);
                poster.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();

                Uri uri = Uri.parse("content://com.example.helloworld.fileprovider/poster.png");

                runOnUiThread(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/png");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                        "白噪音App - 让身心融入平静之中\n下载地址: " + DOWNLOAD_URL);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "分享海报"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Bitmap generatePoster(Bitmap qrBitmap) {
        int w = 1080, h = 1920;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int y = 0; y < h; y++) {
            float ratio = y / (float) h;
            int r = (int) (13 + ratio * 40);
            int g = (int) (27 + ratio * 30);
            int b = (int) (126 - ratio * 50);
            paint.setColor(Color.rgb(r, g, b));
            canvas.drawLine(0, y, w, y, paint);
        }

        for (int i = 0; i < 8; i++) {
            Path path = new Path();
            for (int x = 0; x <= w; x += 30) {
                float wave = (float) Math.sin(x * 0.008 + i * 0.8) * 80;
                float y = i * (h / 8f) + h / 16f + wave;
                if (x == 0) path.moveTo(0, y);
                else path.lineTo(x, y);
            }
            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
            int[] colors = {Color.parseColor("#1A237E"), Color.parseColor("#03DAC5"),
                Color.parseColor("#6200EE"), Color.parseColor("#3700B3")};
            paint.setColor(colors[i % colors.length]);
            paint.setAlpha(40);
            canvas.drawPath(path, paint);
        }

        paint.setAlpha(255);
        paint.setColor(Color.WHITE);
        paint.setTextSize(90);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(8, 4, 4, Color.BLACK);
        canvas.drawText("白噪音", w / 2, 350, paint);

        paint.setTextSize(48);
        canvas.drawText("让身心融入平静之中", w / 2, 450, paint);

        paint.setTextSize(56);
        paint.setColor(Color.parseColor("#03DAC5"));
        paint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText("当前场景: " + getCurrentSound().name, w / 2, 620, paint);

        paint.setColor(Color.parseColor("#6200EE"));
        paint.setStrokeWidth(6);
        paint.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        canvas.drawLine(200, 700, w - 200, 700, paint);

        int qrSize = 420;
        int qrX = (w - qrSize) / 2;
        int qrY = 850;
        if (qrBitmap != null) {
            canvas.drawBitmap(qrBitmap, null,
                new android.graphics.Rect(qrX, qrY, qrX + qrSize, qrY + qrSize), paint);
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(36);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText("扫码下载白噪音App", w / 2, qrY + qrSize + 80, paint);
        paint.setTextSize(28);
        paint.setColor(Color.parseColor("#BBBBBB"));
        canvas.drawText(DOWNLOAD_URL, w / 2, qrY + qrSize + 140, paint);

        paint.setColor(Color.parseColor("#666666"));
        paint.setTextSize(26);
        canvas.drawText("白噪音 - 助眠 · 冥想 · 专注", w / 2, h - 120, paint);

        return bitmap;
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!bgPlayEnabled) {
            if (mediaPlayer != null && isPlaying) {
                try { mediaPlayer.pause(); } catch (Exception ignored) {}
            }
            flowLightView.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        flowLightView.start();
        if (!bgPlayEnabled && isPlaying && mediaPlayer != null) {
            try { mediaPlayer.start(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSound();
        flowLightView.stop();
    }

    static class SoundItem {
        String name;
        String url;
        int resId;
        boolean isCustom;
        int id;

        SoundItem(String n, int res, boolean custom) {
            name = n; resId = res; url = null; isCustom = custom; id = -1;
        }

        SoundItem(int uniqueId, String n, String u) {
            id = uniqueId; name = n; url = u; resId = 0; isCustom = true;
        }
    }

    private int nextCustomId = 0;
    private int getNextId() { return nextCustomId++; }

    private void showManageCustomDialog() {
        List<SoundItem> customItems = new ArrayList<>();
        for (SoundItem item : soundList) {
            if (item.isCustom) customItems.add(item);
        }

        if (customItems.isEmpty()) {
            Toast.makeText(this, "暂无自定义白噪音", Toast.LENGTH_SHORT).show();
            return;
        }

        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#AA000000"));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(20));
        panel.setBackgroundColor(Color.parseColor("#DD1A1A2E"));

        TextView title = new TextView(this);
        title.setText("管理自定义白噪音");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dip2px(16));
        panel.addView(title);

        for (SoundItem item : customItems) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dip2px(8), dip2px(8), dip2px(8), dip2px(8));
            row.setBackgroundColor(Color.parseColor("#222222"));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, dip2px(6), 0, dip2px(6));
            row.setLayoutParams(rowParams);

            TextView nameLabel = new TextView(this);
            nameLabel.setText(item.name);
            nameLabel.setTextSize(16);
            nameLabel.setTextColor(Color.WHITE);
            nameLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
            row.addView(nameLabel);

            TextView urlLabel = new TextView(this);
            String shortUrl = item.url;
            if (shortUrl.length() > 25) shortUrl = shortUrl.substring(0, 25) + "...";
            urlLabel.setText(shortUrl);
            urlLabel.setTextSize(11);
            urlLabel.setTextColor(Color.parseColor("#888888"));
            urlLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 3));
            row.addView(urlLabel);

            Button editBtn = new Button(this);
            editBtn.setText("修改");
            editBtn.setTextSize(12);
            editBtn.setTextColor(Color.WHITE);
            editBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            editBtn.setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4));
            editBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            final int editId = item.id;
            editBtn.setOnClickListener(v -> {
                container.setVisibility(View.GONE);
                showEditCustomDialog(editId);
            });
            row.addView(editBtn);

            Button deleteBtn = new Button(this);
            deleteBtn.setText("删除");
            deleteBtn.setTextSize(12);
            deleteBtn.setTextColor(Color.WHITE);
            deleteBtn.setBackgroundColor(Color.parseColor("#F44336"));
            deleteBtn.setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4));
            deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            final int delId = item.id;
            deleteBtn.setOnClickListener(v -> {
                for (int i = soundList.size() - 1; i >= 0; i--) {
                    if (soundList.get(i).isCustom && soundList.get(i).id == delId) {
                        soundList.remove(i);
                    }
                }
                saveSounds();
                rebuildSoundButtons();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                container.setVisibility(View.GONE);
            });
            row.addView(deleteBtn);

            panel.addView(row);
        }

        Button closeBtn = new Button(this);
        closeBtn.setText("关闭");
        closeBtn.setTextColor(Color.parseColor("#AAAAAA"));
        closeBtn.setBackgroundColor(Color.parseColor("#444444"));
        closeBtn.setTextSize(16);
        closeBtn.setPadding(dip2px(24), dip2px(10), dip2px(24), dip2px(10));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        closeParams.setMargins(0, dip2px(16), 0, 0);
        closeParams.gravity = Gravity.CENTER;
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setOnClickListener(v -> container.setVisibility(View.GONE));
        panel.addView(closeBtn);

        scroll.addView(panel);
        container.addView(scroll);
        container.setVisibility(View.VISIBLE);

        ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        root.addView(container);
    }

    private void showEditCustomDialog(int itemId) {
        SoundItem target = null;
        for (SoundItem item : soundList) {
            if (item.isCustom && item.id == itemId) {
                target = item;
                break;
            }
        }
        if (target == null) return;

        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dip2px(24), dip2px(16), dip2px(24), dip2px(16));
        dialog.setBackgroundColor(Color.parseColor("#DD1A1A2E"));

        TextView titleView = new TextView(this);
        titleView.setText("修改自定义白噪音");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.WHITE);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, dip2px(16));
        dialog.addView(titleView);

        EditText nameInput = new EditText(this);
        nameInput.setText(target.name);
        nameInput.setHintTextColor(Color.parseColor("#888888"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setBackgroundColor(Color.parseColor("#333333"));
        nameInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.addView(nameInput);

        EditText urlInput = new EditText(this);
        urlInput.setText(target.url);
        urlInput.setHintTextColor(Color.parseColor("#888888"));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setBackgroundColor(Color.parseColor("#333333"));
        urlInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        urlInput.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.addView(urlInput);

        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setBackgroundColor(Color.parseColor("#AA000000"));
        wrapper.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        wrapper.addView(dialog);

        Button confirmBtn = new Button(this);
        confirmBtn.setText("保存修改");
        confirmBtn.setTextColor(Color.WHITE);
        confirmBtn.setBackgroundColor(Color.parseColor("#6200EE"));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmParams.setMargins(0, dip2px(20), 0, dip2px(8));
        confirmBtn.setLayoutParams(confirmParams);
        confirmBtn.setPadding(dip2px(24), dip2px(10), dip2px(24), dip2px(10));
        dialog.addView(confirmBtn);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextColor(Color.parseColor("#AAAAAA"));
        cancelBtn.setBackgroundColor(Color.parseColor("#444444"));
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        cancelBtn.setPadding(dip2px(24), dip2px(8), dip2px(24), dip2px(8));
        dialog.addView(cancelBtn);

        container.addView(wrapper);
        container.setVisibility(View.VISIBLE);

        final FrameLayout finalContainer = container;
        final int fid = itemId;
        confirmBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "名称和URL不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "URL必须以http://或https://开头", Toast.LENGTH_SHORT).show();
                return;
            }
            for (SoundItem item : soundList) {
                if (item.isCustom && item.id == fid) {
                    item.name = name;
                    item.url = url;
                    break;
                }
            }
            saveSounds();
            rebuildSoundButtons();
            Toast.makeText(this, "已保存: " + name, Toast.LENGTH_SHORT).show();
            finalContainer.setVisibility(View.GONE);
        });

        cancelBtn.setOnClickListener(v -> finalContainer.setVisibility(View.GONE));

        ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        root.addView(container);
    }
}
