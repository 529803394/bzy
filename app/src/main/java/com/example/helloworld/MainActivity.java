package com.example.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final int REQ_CHAT = 1001;
    static final int TAB_HOME = 10001;
    static final int TAB_LIBRARY = 10002;
    static final int TAB_DISCOVER = 10003;
    static final int TAB_ME = 10004;

    private LinearLayout contentArea; // 主内容区
    private int currentTab = TAB_HOME;

    // 主题模式常量
    public static final int THEME_FOLLOW_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    // 读取当前主题模式（持久化）
    static int getThemeMode(Activity ctx) {
        return ctx.getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                .getInt("theme_mode", THEME_FOLLOW_SYSTEM);
    }

    // 是否应该用深色主题（考虑跟随系统）
    static boolean isDarkMode(Activity ctx) {
        int mode = getThemeMode(ctx);
        if (mode == THEME_LIGHT) return false;
        if (mode == THEME_DARK) return true;
        // 跟随系统：检测系统 uiMode
        int uiMode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // 获取主题名
    static String getThemeName(int mode) {
        if (mode == THEME_LIGHT) return "浅色";
        if (mode == THEME_DARK) return "深色";
        return "跟随系统";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        // 根据主题模式设置状态栏颜色
        boolean dark = isDarkMode(this);
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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? Color.parseColor("#121212")
                : Color.parseColor("#F7F7F7"));

        // 主内容区
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        contentArea.setLayoutParams(clp);
        root.addView(contentArea);

        // 底部Tab栏
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(dark ? Color.parseColor("#1e1e1e")
                : Color.WHITE);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tbp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dip2px(56));
        tbp.gravity = Gravity.BOTTOM;
        tabBar.setLayoutParams(tbp);

        addTab(tabBar, "首页", TAB_HOME);
        addTab(tabBar, "乐库", TAB_LIBRARY);
        addTab(tabBar, "发现", TAB_DISCOVER);
        addTab(tabBar, "我", TAB_ME);

        root.addView(tabBar);

        // 顶部分割线
        View topDiv = new View(this);
        topDiv.setBackgroundColor(dark ? Color.parseColor("#2a2a2a")
                : Color.parseColor("#E5E5E5"));
        FrameLayout.LayoutParams tdlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
        tdlp.gravity = Gravity.BOTTOM;
        tdlp.bottomMargin = dip2px(56);
        topDiv.setLayoutParams(tdlp);
        root.addView(topDiv);

        setContentView(root);
        switchTab(TAB_HOME);
    }

    private void addTab(LinearLayout bar, String text, int id) {
        LinearLayout tab = new LinearLayout(this);
        tab.setId(id);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        tab.setLayoutParams(lp);

        // 图标（用简单的圆点表示）
        TextView icon = new TextView(this);
        icon.setTextSize(18);
        icon.setText(getTabIcon(id));
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.parseColor("#999999"));
        tab.addView(icon);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(Color.parseColor("#999999"));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelp.topMargin = dip2px(2);
        label.setLayoutParams(labelp);
        tab.addView(label);

        tab.setOnClickListener(v -> switchTab(id));
        bar.addView(tab);
    }

    private String getTabIcon(int id) {
        if (id == TAB_HOME) return "💬";
        if (id == TAB_LIBRARY) return "🎵";
        if (id == TAB_DISCOVER) return "✨";
        return "👤";
    }

    private void switchTab(int id) {
        currentTab = id;
        contentArea.removeAllViews();
        boolean dark = isDarkMode(this);

        // 更新 tab 样式：激活色根据浅/深主题调整
        LinearLayout tabBar = (LinearLayout) ((FrameLayout) contentArea.getParent()).getChildAt(1);
        if (tabBar != null) {
            for (int i = 0; i < tabBar.getChildCount(); i++) {
                LinearLayout tab = (LinearLayout) tabBar.getChildAt(i);
                TextView icon = (TextView) tab.getChildAt(0);
                TextView label = (TextView) tab.getChildAt(1);
                int active = dark ? Color.parseColor("#4dd0e1")
                        : Color.parseColor("#07C160");
                int inactive = dark ? Color.parseColor("#8a8a8a")
                        : Color.parseColor("#999999");
                int color = (tab.getId() == id) ? active : inactive;
                icon.setTextColor(color);
                label.setTextColor(color);
            }
        }

        // 标题栏
        TextView title = new TextView(this);
        title.setText(getTitleText(id));
        title.setTextSize(17);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(dark ? Color.WHITE : Color.BLACK);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(48));
        title.setLayoutParams(tlp);
        title.setBackgroundColor(dark ? Color.parseColor("#1e1e1e")
                : Color.parseColor("#F7F7F7"));
        contentArea.addView(title);

        // 标题下方分割线
        View div = new View(this);
        div.setBackgroundColor(dark ? Color.parseColor("#2a2a2a")
                : Color.parseColor("#E5E5E5"));
        contentArea.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));

        // 内容页面
        if (id == TAB_HOME) renderHome();
        else if (id == TAB_LIBRARY) renderLibrary();
        else if (id == TAB_DISCOVER) renderDiscover();
        else if (id == TAB_ME) renderMe();
    }

    private String getTitleText(int id) {
        if (id == TAB_HOME) return "微信";
        if (id == TAB_LIBRARY) return "乐库";
        if (id == TAB_DISCOVER) return "发现";
        return "我";
    }

    // -------- 首页：聊天列表 --------
    private void renderHome() {
        final boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int cardDiv = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");

        List<SoundStore.Sound> list = SoundStore.getHomeList(this);
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无聊天，去乐库添加白噪音吧");
            empty.setTextSize(14);
            empty.setTextColor(textSub);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
            elp.gravity = Gravity.CENTER;
            empty.setLayoutParams(elp);
            contentArea.addView(empty);
            return;
        }

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1));
        sv.setFillViewport(true);

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setBackgroundColor(cardBg);
        sv.addView(items);

        for (final SoundStore.Sound s : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(cardBg);
            row.setPadding(dip2px(14), dip2px(12), dip2px(14), dip2px(12));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rlp);

            // 头像（用渐变色圆表示白噪音的"头像"）
            int[] colors = s.getChatBgColors();
            TextView avatar = new TextView(this);
            avatar.setText(s.name.substring(0, 1));
            avatar.setTextSize(20);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable avatarBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            avatarBg.setCornerRadius(dip2px(24));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                avatar.setBackground(avatarBg);
            } else {
                avatar.setBackgroundDrawable(avatarBg);
            }
            LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dip2px(48), dip2px(48));
            avatar.setLayoutParams(avp);
            row.addView(avatar);

            // 右侧信息
            LinearLayout rightWrap = new LinearLayout(this);
            rightWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rwp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            rwp.leftMargin = dip2px(12);
            rightWrap.setLayoutParams(rwp);

            // 第一行：名称 + 时间
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameLabel = new TextView(this);
            String prefix = s.isPinned ? "📌 " : "";
            nameLabel.setText(prefix + s.name);
            nameLabel.setTextSize(16);
            nameLabel.setTextColor(textMain);
            nameLabel.getPaint().setFakeBoldText(true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            nameLabel.setLayoutParams(nlp);
            row1.addView(nameLabel);

            TextView timeLabel = new TextView(this);
            timeLabel.setText(SoundStore.formatTime(s.lastTime));
            timeLabel.setTextSize(11);
            timeLabel.setTextColor(textSub);
            timeLabel.setGravity(Gravity.RIGHT);
            timeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            row1.addView(timeLabel);
            rightWrap.addView(row1);

            // 第二行：最后一条消息预览
            TextView msgLabel = new TextView(this);
            String preview = s.lastMessage;
            if (preview == null || preview.isEmpty()) preview = "点击进入聊天";
            if (preview.length() > 20) preview = preview.substring(0, 20) + "...";
            msgLabel.setText(preview);
            msgLabel.setTextSize(13);
            msgLabel.setTextColor(textSub);
            msgLabel.setPadding(0, dip2px(4), 0, 0);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            msgLabel.setLayoutParams(mlp);
            rightWrap.addView(msgLabel);

            row.addView(rightWrap);

            // 点击事件：打开聊天播放页
            row.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("sound_id", s.id);
                startActivityForResult(i, REQ_CHAT);
            });

            // 长按：弹出操作菜单（置顶/删除）
            row.setOnLongClickListener(v -> {
                showSoundMenu(s);
                return true;
            });

            items.addView(row);

            // 分割线
            View line = new View(this);
            line.setBackgroundColor(cardDiv);
            items.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        }

        contentArea.addView(sv);
    }

    // 聊天项操作弹窗
    private void showSoundMenu(final SoundStore.Sound s) {
        final boolean dark = isDarkMode(this);
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#55000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        plp.leftMargin = 0;
        plp.rightMargin = 0;
        panel.setLayoutParams(plp);

        // 标题
        TextView title = new TextView(this);
        title.setText(s.name);
        title.setTextSize(12);
        title.setTextColor(textSub);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dip2px(14), 0, 0);
        panel.addView(title);

        String pinLabel = s.isPinned ? "取消置顶" : "置顶";
        panel.addView(makeMenuBtn(pinLabel, v -> {
            SoundStore.togglePin(MainActivity.this, s.id);
            ((ViewGroup) container.getParent()).removeView(container);
            refresh();
        }));
        panel.addView(makeMenuBtn("删除", v -> {
            SoundStore.markDeleted(MainActivity.this, s.id, true);
            ((ViewGroup) container.getParent()).removeView(container);
            refresh();
            Toast.makeText(MainActivity.this, "已移到乐库", Toast.LENGTH_SHORT).show();
        }));
        panel.addView(makeMenuBtn("取消", v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        }));

        panel.setPadding(0, 0, 0, dip2px(16));

        container.setOnClickListener(v -> {
            ((ViewGroup) v.getParent()).removeView(v);
        });
        container.addView(panel);

        ViewGroup root = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root.addView(container);
    }

    private Button makeMenuBtn(String text, View.OnClickListener l) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setTextColor(textMain);
        b.setBackgroundColor(panelBg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(54));
        bp.topMargin = dip2px(4);
        b.setLayoutParams(bp);
        b.setOnClickListener(l);
        return b;
    }

    // -------- 乐库页面 --------
    private void renderLibrary() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int cardDiv = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");
        int tipBg = dark ? Color.parseColor("#151515") : Color.parseColor("#F7F7F7");

        List<SoundStore.Sound> list = SoundStore.getLibraryList(this);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setBackgroundColor(cardBg);
        sv.addView(items);

        // 说明
        TextView tip = new TextView(this);
        tip.setText("点击白噪音将恢复到首页并进入聊天");
        tip.setTextSize(12);
        tip.setTextColor(textSub);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dip2px(12), 0, dip2px(12));
        tip.setBackgroundColor(tipBg);
        items.addView(tip, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        for (final SoundStore.Sound s : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dip2px(14), dip2px(14), dip2px(14), dip2px(14));
            row.setBackgroundColor(cardBg);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rlp);

            int[] colors = s.getChatBgColors();
            TextView avatar = new TextView(this);
            avatar.setText(s.name.substring(0, 1));
            avatar.setTextSize(20);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            bg.setCornerRadius(dip2px(24));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                avatar.setBackground(bg);
            } else {
                avatar.setBackgroundDrawable(bg);
            }
            LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dip2px(48), dip2px(48));
            avatar.setLayoutParams(avp);
            row.addView(avatar);

            LinearLayout rightWrap = new LinearLayout(this);
            rightWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rwp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            rwp.leftMargin = dip2px(12);
            rightWrap.setLayoutParams(rwp);

            TextView nameLabel = new TextView(this);
            String tag = s.isCustom ? "(自定义)" : "";
            String status = s.isDeleted ? "（已从首页移除）" : "";
            nameLabel.setText(s.name + tag + status);
            nameLabel.setTextSize(16);
            nameLabel.setTextColor(textMain);
            nameLabel.getPaint().setFakeBoldText(true);
            rightWrap.addView(nameLabel);

            TextView desc = new TextView(this);
            desc.setText("点击恢复到首页并进入聊天");
            desc.setTextSize(12);
            desc.setTextColor(textSub);
            desc.setPadding(0, dip2px(4), 0, 0);
            rightWrap.addView(desc);

            row.addView(rightWrap);

            row.setOnClickListener(v -> {
                if (s.isDeleted) {
                    SoundStore.markDeleted(MainActivity.this, s.id, false);
                }
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("sound_id", s.id);
                startActivityForResult(i, REQ_CHAT);
            });

            items.addView(row);

            View line = new View(this);
            line.setBackgroundColor(cardDiv);
            items.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        }

        contentArea.addView(sv);
    }

    // -------- 发现页面：建设中 --------
    private void renderDiscover() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int bg = dark ? Color.parseColor("#121212") : Color.parseColor("#F7F7F7");

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        holder.setBackgroundColor(bg);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp2 = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        clp2.gravity = Gravity.CENTER;
        center.setLayoutParams(clp2);

        TextView icon = new TextView(this);
        icon.setText("🚧");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        center.addView(icon);

        TextView t = new TextView(this);
        t.setText("发现页");
        t.setTextSize(20);
        t.setTextColor(textMain);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dip2px(12), 0, dip2px(8));
        t.getPaint().setFakeBoldText(true);
        center.addView(t);

        TextView sub = new TextView(this);
        sub.setText("正在建设中");
        sub.setTextSize(14);
        sub.setTextColor(textSub);
        sub.setGravity(Gravity.CENTER);
        center.addView(sub);

        holder.addView(center);
        contentArea.addView(holder);
    }

    // -------- 我页面（包含设置、添加、管理） --------
    private void renderMe() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int pageBg = dark ? Color.parseColor("#121212") : Color.parseColor("#EDEDED");
        int sepColor = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        sv.setBackgroundColor(pageBg);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        sv.addView(container);

        // 用户信息卡片
        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setBackgroundColor(cardBg);
        profile.setPadding(dip2px(20), dip2px(28), dip2px(20), dip2px(28));
        container.addView(profile, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView profileIcon = new TextView(this);
        profileIcon.setText("白");
        profileIcon.setTextSize(28);
        profileIcon.setTextColor(Color.WHITE);
        profileIcon.setGravity(Gravity.CENTER);
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#07C160"), Color.parseColor("#10AEFF")});
        pbg.setCornerRadius(dip2px(30));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            profileIcon.setBackground(pbg);
        } else {
            profileIcon.setBackgroundDrawable(pbg);
        }
        LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(dip2px(60), dip2px(60));
        profileIcon.setLayoutParams(pvp);
        profile.addView(profileIcon);

        LinearLayout ptext = new LinearLayout(this);
        ptext.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ptlp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        ptlp.leftMargin = dip2px(16);
        ptext.setLayoutParams(ptlp);

        TextView uname = new TextView(this);
        uname.setText("白噪音用户");
        uname.setTextSize(19);
        uname.setTextColor(textMain);
        uname.getPaint().setFakeBoldText(true);
        ptext.addView(uname);

        TextView uacc = new TextView(this);
        uacc.setText("微信号: whitenoise");
        uacc.setTextSize(12);
        uacc.setTextColor(textSub);
        uacc.setPadding(0, dip2px(6), 0, 0);
        ptext.addView(uacc);
        profile.addView(ptext);

        // 间距
        TextView spacer = new TextView(this);
        spacer.setHeight(dip2px(12));
        container.addView(spacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(12)));

        // 功能列表：设置、添加、管理
        container.addView(makeMenuRow("⚙️", "设置", "主题/后台播放", v -> showSettingsDialog()));
        container.addView(lineSep());
        container.addView(makeMenuRow("➕", "添加白噪音", "添加自定义音频URL", v -> showAddDialog()));
        container.addView(lineSep());
        container.addView(makeMenuRow("📋", "管理自定义", "修改 / 删除自定义白噪音", v -> showManageDialog()));

        contentArea.addView(sv);
    }

    private LinearLayout makeMenuRow(String icon, String title, String subtitle, View.OnClickListener l) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int arrowColor = dark ? Color.parseColor("#666666") : Color.parseColor("#CCCCCC");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(cardBg);
        row.setPadding(dip2px(20), dip2px(16), dip2px(20), dip2px(16));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rlp);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(22);
        row.addView(ic);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dip2px(14);
        text.setLayoutParams(tlp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(textMain);
        text.addView(t);

        if (subtitle != null) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextSize(12);
            s.setTextColor(textSub);
            s.setPadding(0, dip2px(4), 0, 0);
            text.addView(s);
        }
        row.addView(text);

        TextView arrow = new TextView(this);
        arrow.setText(">");
        arrow.setTextSize(18);
        arrow.setTextColor(arrowColor);
        row.addView(arrow);

        row.setOnClickListener(l);
        return row;
    }

    private View lineSep() {
        boolean dark = isDarkMode(this);
        int sepColor = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");
        View v = new View(this);
        v.setBackgroundColor(sepColor);
        v.setMinimumHeight(dip2px(0.5f));
        v.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        return v;
    }

    // -------- 设置弹窗（含主题模式选择） --------
    private void showSettingsDialog() {
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int btnBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F3F3F3");
        final int btnActive = dark ? Color.parseColor("#4dd0e1") : Color.parseColor("#07C160");

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#AA000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.width = dip2px(310);
        panel.setLayoutParams(plp);
        panel.setPadding(0, dip2px(20), 0, dip2px(12));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(19);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dip2px(10));
        panel.addView(title);

        // -- 主题模式 --
        TextView themeLabel = new TextView(this);
        themeLabel.setText("主题模式");
        themeLabel.setTextSize(14);
        themeLabel.setTextColor(textMain);
        themeLabel.setPadding(dip2px(16), dip2px(12), dip2px(16), dip2px(8));
        panel.addView(themeLabel);

        final int currentMode = getThemeMode(this);
        final int[] modes = new int[]{THEME_FOLLOW_SYSTEM, THEME_LIGHT, THEME_DARK};
        final String[] labels = new String[]{"跟随系统", "浅色", "深色"};
        LinearLayout themeBtns = new LinearLayout(this);
        themeBtns.setOrientation(LinearLayout.HORIZONTAL);
        themeBtns.setPadding(dip2px(16), 0, dip2px(16), dip2px(4));
        themeBtns.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < modes.length; i++) {
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setTextSize(13);
            final boolean active = currentMode == modes[i];
            b.setTextColor(active ? Color.WHITE : textMain);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dip2px(8));
            g.setColor(active ? btnActive : btnBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                b.setBackground(g);
            } else {
                b.setBackgroundDrawable(g);
            }
            final int mode = modes[i];
            b.setOnClickListener(v -> {
                getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                    .edit().putInt("theme_mode", mode).apply();
                ((ViewGroup) container.getParent()).removeView(container);
                // 刷新当前 activity：重新创建
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    MainActivity.this.recreate();
                } else {
                    refresh();
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, dip2px(38), 1);
            if (i > 0) blp.leftMargin = dip2px(8);
            b.setLayoutParams(blp);
            themeBtns.addView(b);
        }
        panel.addView(themeBtns);

        // 显示当前
        TextView themeHint = new TextView(this);
        themeHint.setText("当前: " + getThemeName(currentMode));
        themeHint.setTextSize(11);
        themeHint.setTextColor(textSub);
        themeHint.setGravity(Gravity.CENTER);
        themeHint.setPadding(0, dip2px(6), 0, 0);
        panel.addView(themeHint);

        // 分割线
        View divider = new View(this);
        divider.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E5E5E5"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
        dlp.topMargin = dip2px(10);
        divider.setLayoutParams(dlp);
        panel.addView(divider);

        // -- 后台播放开关 --
        LinearLayout bgRow = new LinearLayout(this);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        bgRow.setGravity(Gravity.CENTER_VERTICAL);
        bgRow.setPadding(dip2px(16), dip2px(12), dip2px(16), dip2px(12));
        LinearLayout.LayoutParams bgp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bgRow.setLayoutParams(bgp);

        LinearLayout bgText = new LinearLayout(this);
        bgText.setOrientation(LinearLayout.VERTICAL);
        bgText.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView bgt1 = new TextView(this);
        bgt1.setText("后台播放");
        bgt1.setTextSize(15);
        bgt1.setTextColor(textMain);
        bgText.addView(bgt1);

        TextView bgt2 = new TextView(this);
        bgt2.setText("返回后可继续播放（助眠场景）");
        bgt2.setTextSize(11);
        bgt2.setTextColor(textSub);
        bgt2.setPadding(0, dip2px(4), 0, 0);
        bgText.addView(bgt2);
        bgRow.addView(bgText);

        final SwitchCompatImpl bgSwitch = new SwitchCompatImpl(this);
        bgSwitch.setChecked(getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
            .getBoolean("bg_play", false));
        bgSwitch.setOnCheckedChanged(new Runnable() {
            @Override public void run() {
                getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                    .edit().putBoolean("bg_play", bgSwitch.isChecked()).apply();
            }
        });
        bgRow.addView(bgSwitch.getView());
        panel.addView(bgRow);

        // 版本信息
        TextView ver = new TextView(this);
        ver.setText("版本: 2.0.0");
        ver.setTextSize(12);
        ver.setTextColor(textSub);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dip2px(16), 0, dip2px(12));
        panel.addView(ver);

        // 关闭按钮
        Button close = new Button(this);
        close.setText("关闭");
        close.setTextSize(15);
        close.setTextColor(dark ? Color.parseColor("#4dd0e1") : Color.parseColor("#07C160"));
        close.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dip2px(4);
        close.setLayoutParams(cp);
        close.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });
        panel.addView(close);

        container.setOnClickListener(v -> {
            ((ViewGroup) v.getParent()).removeView(v);
        });
        container.addView(panel);

        ViewGroup root = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root.addView(container);
    }

    // -------- 添加白噪音弹窗 --------
    private void showAddDialog() {
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#666666");
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F5F5F5");

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#AA000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.width = dip2px(320);
        panel.setLayoutParams(plp);
        panel.setPadding(dip2px(18), dip2px(18), dip2px(18), dip2px(12));

        TextView title = new TextView(this);
        title.setText("添加白噪音");
        title.setTextSize(18);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dip2px(14));
        panel.addView(title);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("名称");
        nameInput.setTextSize(15);
        nameInput.setTextColor(textMain);
        nameInput.setHintTextColor(textSub);
        nameInput.setBackgroundColor(inputBg);
        nameInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        panel.addView(nameInput);

        final EditText urlInput = new EditText(this);
        urlInput.setHint("音频URL (https://...)");
        urlInput.setTextSize(15);
        urlInput.setTextColor(textMain);
        urlInput.setHintTextColor(textSub);
        urlInput.setBackgroundColor(inputBg);
        urlInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dip2px(10);
        urlInput.setLayoutParams(ulp);
        panel.addView(urlInput);

        final EditText bgImgInput = new EditText(this);
        bgImgInput.setHint("背景图片URL（可选，https://...）");
        bgImgInput.setTextSize(15);
        bgImgInput.setTextColor(textMain);
        bgImgInput.setHintTextColor(textSub);
        bgImgInput.setBackgroundColor(inputBg);
        bgImgInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams bgiLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bgiLp.topMargin = dip2px(10);
        bgImgInput.setLayoutParams(bgiLp);
        panel.addView(bgImgInput);

        Button confirm = new Button(this);
        confirm.setText("添加");
        confirm.setTextSize(15);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackgroundColor(Color.parseColor("#07C160"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(44));
        cp.topMargin = dip2px(16);
        confirm.setLayoutParams(cp);
        confirm.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            String bgImg = bgImgInput.getText().toString().trim();
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(MainActivity.this, "请填写名称和音频URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(MainActivity.this, "URL必须是http或https开头", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bgImg.isEmpty() && !bgImg.startsWith("http://") && !bgImg.startsWith("https://")) {
                Toast.makeText(MainActivity.this, "图片URL必须是http或https开头", Toast.LENGTH_SHORT).show();
                return;
            }
            SoundStore.addCustom(MainActivity.this, name, url, bgImg.isEmpty() ? null : bgImg);
            ((ViewGroup) container.getParent()).removeView(container);
            Toast.makeText(MainActivity.this, "已添加: " + name, Toast.LENGTH_SHORT).show();
            refresh();
        });
        panel.addView(confirm);

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setTextColor(Color.parseColor("#666666"));
        cancel.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(44));
        cap.topMargin = dip2px(6);
        cancel.setLayoutParams(cap);
        cancel.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });
        panel.addView(cancel);

        container.addView(panel);
        ViewGroup root = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root.addView(container);
    }

    // -------- 管理自定义弹窗 --------
    private void showManageDialog() {
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int cardBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F8F8F8");

        List<SoundStore.Sound> customs = new ArrayList<>();
        for (SoundStore.Sound s : SoundStore.getAll(this)) {
            if (s.isCustom) customs.add(s);
        }

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#AA000000"));

        ScrollView sv = new ScrollView(this);
        FrameLayout.LayoutParams svp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        svp.gravity = Gravity.CENTER;
        svp.width = dip2px(320);
        sv.setLayoutParams(svp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(14), dip2px(18), dip2px(14), dip2px(12));
        sv.addView(panel);

        TextView title = new TextView(this);
        title.setText("管理自定义白噪音");
        title.setTextSize(18);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dip2px(14));
        panel.addView(title);

        if (customs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无自定义白噪音");
            empty.setTextSize(14);
            empty.setTextColor(textSub);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dip2px(20), 0, dip2px(20));
            panel.addView(empty);
        } else {
            for (final SoundStore.Sound s : customs) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackgroundColor(cardBg);
                row.setPadding(dip2px(12), dip2px(12), dip2px(12), dip2px(12));
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dip2px(8);
                row.setLayoutParams(rlp);

                TextView n = new TextView(this);
                n.setText(s.name);
                n.setTextSize(15);
                n.setTextColor(textMain);
                row.addView(n);

                TextView url = new TextView(this);
                url.setText(s.url);
                url.setTextSize(11);
                url.setTextColor(textSub);
                url.setPadding(0, dip2px(4), 0, dip2px(2));
                row.addView(url);

                if (s.bgImageUrl != null && !s.bgImageUrl.isEmpty()) {
                    TextView bgTag = new TextView(this);
                    bgTag.setText("🖼 背景图已设置");
                    bgTag.setTextSize(11);
                    bgTag.setTextColor(Color.parseColor("#4dd0e1"));
                    bgTag.setPadding(0, dip2px(2), 0, dip2px(8));
                    row.addView(bgTag);
                } else {
                    TextView bgTag = new TextView(this);
                    bgTag.setText("无自定义背景");
                    bgTag.setTextSize(11);
                    bgTag.setTextColor(dark ? Color.parseColor("#555555") : Color.parseColor("#aaaaaa"));
                    bgTag.setPadding(0, dip2px(2), 0, dip2px(8));
                    row.addView(bgTag);
                }

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                btns.setGravity(Gravity.RIGHT);
                LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                btns.setLayoutParams(btp);

                Button edit = new Button(this);
                edit.setText("修改");
                edit.setTextSize(12);
                edit.setTextColor(Color.WHITE);
                edit.setBackgroundColor(Color.parseColor("#10AEFF"));
                edit.setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4));
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                ep.rightMargin = dip2px(8);
                edit.setLayoutParams(ep);
                edit.setOnClickListener(v -> {
                    ((ViewGroup) container.getParent()).removeView(container);
                    showEditDialog(s.id);
                });
                btns.addView(edit);

                Button del = new Button(this);
                del.setText("删除");
                del.setTextSize(12);
                del.setTextColor(Color.WHITE);
                del.setBackgroundColor(Color.parseColor("#FA5151"));
                del.setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4));
                del.setOnClickListener(v -> {
                    SoundStore.deleteCustom(MainActivity.this, s.id);
                    ((ViewGroup) container.getParent()).removeView(container);
                    Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    refresh();
                });
                btns.addView(del);

                row.addView(btns);
                panel.addView(row);
            }
        }

        Button close = new Button(this);
        close.setText("关闭");
        close.setTextSize(14);
        close.setTextColor(Color.parseColor("#666666"));
        close.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(40));
        clp2.topMargin = dip2px(8);
        close.setLayoutParams(clp2);
        close.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });
        panel.addView(close);

        container.addView(sv);
        ViewGroup root2 = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root2.addView(container);
    }

    private void showEditDialog(final String itemId) {
        final SoundStore.Sound s = SoundStore.findById(this, itemId);
        if (s == null) return;

        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#666666");
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F5F5F5");

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#AA000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.width = dip2px(320);
        panel.setLayoutParams(plp);
        panel.setPadding(dip2px(18), dip2px(18), dip2px(18), dip2px(12));

        TextView title = new TextView(this);
        title.setText("修改白噪音");
        title.setTextSize(18);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dip2px(14));
        panel.addView(title);

        final EditText nameInput = new EditText(this);
        nameInput.setText(s.name);
        nameInput.setTextSize(15);
        nameInput.setTextColor(textMain);
        nameInput.setHintTextColor(textSub);
        nameInput.setBackgroundColor(inputBg);
        nameInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        panel.addView(nameInput);

        final EditText urlInput = new EditText(this);
        urlInput.setText(s.url);
        urlInput.setHint("音频URL (https://...)");
        urlInput.setTextSize(15);
        urlInput.setTextColor(textMain);
        urlInput.setHintTextColor(textSub);
        urlInput.setBackgroundColor(inputBg);
        urlInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dip2px(10);
        urlInput.setLayoutParams(ulp);
        panel.addView(urlInput);

        final EditText bgImgInput = new EditText(this);
        bgImgInput.setHint("背景图片URL（可选，https://...）");
        bgImgInput.setTextSize(15);
        bgImgInput.setTextColor(textMain);
        bgImgInput.setHintTextColor(textSub);
        bgImgInput.setBackgroundColor(inputBg);
        bgImgInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        if (s.bgImageUrl != null && !s.bgImageUrl.isEmpty()) {
            bgImgInput.setText(s.bgImageUrl);
        }
        LinearLayout.LayoutParams bgiLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bgiLp.topMargin = dip2px(10);
        bgImgInput.setLayoutParams(bgiLp);
        panel.addView(bgImgInput);

        Button confirm = new Button(this);
        confirm.setText("保存");
        confirm.setTextSize(15);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackgroundColor(Color.parseColor("#07C160"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(44));
        cp.topMargin = dip2px(16);
        confirm.setLayoutParams(cp);
        confirm.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            String bgImg = bgImgInput.getText().toString().trim();
            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(MainActivity.this, "请填写名称和音频URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bgImg.isEmpty() && !bgImg.startsWith("http://") && !bgImg.startsWith("https://")) {
                Toast.makeText(MainActivity.this, "图片URL必须是http或https开头", Toast.LENGTH_SHORT).show();
                return;
            }
            SoundStore.updateCustom(MainActivity.this, itemId, name, url, bgImg.isEmpty() ? null : bgImg);
            ((ViewGroup) container.getParent()).removeView(container);
            Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            refresh();
        });
        panel.addView(confirm);

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setTextColor(Color.parseColor("#666666"));
        cancel.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(44));
        cap.topMargin = dip2px(6);
        cancel.setLayoutParams(cap);
        cancel.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });
        panel.addView(cancel);

        container.addView(panel);
        ViewGroup root3 = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root3.addView(container);
    }

    // 从聊天页返回时刷新
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHAT) {
            refresh();
        }
    }

    private void refresh() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int titleBg = dark ? Color.parseColor("#1e1e1e") : Color.parseColor("#F7F7F7");
        int divColor = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E5E5E5");

        int saved = currentTab;
        contentArea.removeAllViews();
        TextView title = new TextView(this);
        title.setText(getTitleText(saved));
        title.setTextSize(17);
        title.getPaint().setFakeBoldText(true);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(48));
        title.setLayoutParams(tlp);
        title.setBackgroundColor(titleBg);
        contentArea.addView(title);

        View div = new View(this);
        div.setBackgroundColor(divColor);
        contentArea.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));

        if (saved == TAB_HOME) renderHome();
        else if (saved == TAB_LIBRARY) renderLibrary();
        else if (saved == TAB_DISCOVER) renderDiscover();
        else if (saved == TAB_ME) renderMe();
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
