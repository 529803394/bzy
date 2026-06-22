package com.example.helloworld;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final int REQ_CHAT = 1001;
    public static final int REQ_IMPORT_FILE = 1002;
    static final int TAB_HOME = 10001;
    static final int TAB_LIBRARY = 10002;
    static final int TAB_DISCOVER = 10003;
    static final int TAB_ME = 10004;

    private LinearLayout contentArea;
    private int currentTab = TAB_HOME;
    private boolean libSelectMode = false;
    private java.util.Set<String> libSelected = new java.util.HashSet<>();
    // 乐库操作栏按钮（多选删除时需即时刷新状态）
    private TextView libLeftBtn = null;
    private TextView libRightBtn = null;
    private java.util.List<SoundStore.Sound> libLocalList = new java.util.ArrayList<>();

    // 主题模式常量
    public static final int THEME_FOLLOW_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    static int getThemeMode(Activity ctx) {
        return ctx.getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                .getInt("theme_mode", THEME_FOLLOW_SYSTEM);
    }

    static boolean isDarkMode(Activity ctx) {
        int mode = getThemeMode(ctx);
        if (mode == THEME_LIGHT) return false;
        if (mode == THEME_DARK) return true;
        int uiMode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    static String getThemeName(int mode) {
        if (mode == THEME_LIGHT) return "浅色";
        if (mode == THEME_DARK) return "深色";
        return "跟随系统";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
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

        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        clp.bottomMargin = dip2px(56); // 避免被底部 tabBar 遮挡
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

        View div = new View(this);
        div.setBackgroundColor(dark ? Color.parseColor("#2a2a2a")
                : Color.parseColor("#E5E5E5"));
        contentArea.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));

        if (id == TAB_HOME) renderHome();
        else if (id == TAB_LIBRARY) renderLibrary();
        else if (id == TAB_DISCOVER) renderDiscover();
        else if (id == TAB_ME) renderMe();
    }

    private String getTitleText(int id) {
        if (id == TAB_HOME) return "小白";
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

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1));
        sv.setFillViewport(true);

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setBackgroundColor(cardBg);
        items.setPadding(0, 0, 0, dip2px(20)); // 底部留白避免贴底
        sv.addView(items);

        // ===== 猜你喜欢 入口卡片 =====
        LinearLayout recRow = new LinearLayout(this);
        recRow.setOrientation(LinearLayout.HORIZONTAL);
        recRow.setGravity(Gravity.CENTER_VERTICAL);
        recRow.setBackgroundColor(dark ? Color.parseColor("#2a2a3a") : Color.parseColor("#FFF7ED"));
        recRow.setPadding(dip2px(14), dip2px(16), dip2px(14), dip2px(16));
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        recLp.bottomMargin = dip2px(2);
        recRow.setLayoutParams(recLp);

        TextView recIcon = new TextView(this);
        recIcon.setText("✨");
        recIcon.setTextSize(22);
        recIcon.setGravity(Gravity.CENTER);
        GradientDrawable recIconBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#f59e0b"), Color.parseColor("#ef4444")});
        recIconBg.setCornerRadius(dip2px(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            recIcon.setBackground(recIconBg);
        } else {
            recIcon.setBackgroundDrawable(recIconBg);
        }
        LinearLayout.LayoutParams recIconLp = new LinearLayout.LayoutParams(dip2px(48), dip2px(48));
        recIcon.setLayoutParams(recIconLp);
        recRow.addView(recIcon);

        LinearLayout recRight = new LinearLayout(this);
        recRight.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams recRlp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        recRlp.leftMargin = dip2px(12);
        recRight.setLayoutParams(recRlp);

        TextView recTitle = new TextView(this);
        recTitle.setText("猜你喜欢 · 为你推荐一首白噪音");
        recTitle.setTextSize(16);
        recTitle.setTextColor(textMain);
        recTitle.getPaint().setFakeBoldText(true);
        recRight.addView(recTitle);

        TextView recDesc = new TextView(this);
        recDesc.setText("根据当前时间、季节和心情推荐");
        recDesc.setTextSize(12);
        recDesc.setTextColor(textSub);
        recDesc.setPadding(0, dip2px(4), 0, 0);
        recRight.addView(recDesc);
        recRow.addView(recRight);

        TextView recArrow = new TextView(this);
        recArrow.setText("→");
        recArrow.setTextSize(18);
        recArrow.setTextColor(Color.parseColor("#f59e0b"));
        recArrow.setGravity(Gravity.CENTER);
        recRow.addView(recArrow);

        recRow.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, RecommendActivity.class);
            startActivityForResult(i, REQ_CHAT);
        });
        items.addView(recRow);

        // 分隔线
        View recDiv = new View(this);
        recDiv.setBackgroundColor(cardDiv);
        items.addView(recDiv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));

        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无聊天，去乐库添加白噪音吧");
            empty.setTextSize(14);
            empty.setTextColor(textSub);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dip2px(40), 0, 0);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.gravity = Gravity.CENTER;
            empty.setLayoutParams(elp);
            items.addView(empty);
            contentArea.addView(sv);
            return;
        }

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

            LinearLayout rightWrap = new LinearLayout(this);
            rightWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rwp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            rwp.leftMargin = dip2px(12);
            rightWrap.setLayoutParams(rwp);

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

            row.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("sound_id", s.id);
                startActivityForResult(i, REQ_CHAT);
            });

            row.setOnLongClickListener(v -> {
                showSoundMenu(s);
                return true;
            });

            items.addView(row);

            View line = new View(this);
            line.setBackgroundColor(cardDiv);
            items.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        }

        contentArea.addView(sv);
    }

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
        panel.setLayoutParams(plp);

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
        panel.addView(makeMenuBtn("重命名", v -> {
            ((ViewGroup) container.getParent()).removeView(container);
            showRenameDialog(s);
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

    // 重命名对话框
    private void showRenameDialog(final SoundStore.Sound s) {
        final boolean dark = isDarkMode(this);
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        final int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F5F5F5");

        final FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        container.setBackgroundColor(Color.parseColor("#55000000"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        plp.leftMargin = dip2px(40);
        plp.rightMargin = dip2px(40);
        panel.setLayoutParams(plp);
        panel.setPadding(dip2px(20), dip2px(20), dip2px(20), dip2px(12));

        TextView title = new TextView(this);
        title.setText("重命名");
        title.setTextSize(17);
        title.setTextColor(textMain);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, dip2px(14));
        panel.addView(title);

        final EditText input = new EditText(this);
        input.setText(s.name);
        input.setSelection(s.name.length());
        input.setTextSize(16);
        input.setTextColor(textMain);
        input.setBackgroundColor(inputBg);
        input.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        input.setSingleLine(true);
        input.setHint("输入新名字");
        input.setHintTextColor(textSub);
        panel.addView(input);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(0, dip2px(14), 0, 0);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(15);
        cancelBtn.setTextColor(textSub);
        cancelBtn.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cblp.rightMargin = dip2px(8);
        cancelBtn.setLayoutParams(cblp);
        cancelBtn.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });
        btnRow.addView(cancelBtn);

        Button okBtn = new Button(this);
        okBtn.setText("确定");
        okBtn.setTextSize(15);
        okBtn.setTextColor(Color.parseColor("#07C160"));
        okBtn.setBackgroundColor(Color.TRANSPARENT);
        okBtn.setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(MainActivity.this, "名字不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            SoundStore.rename(MainActivity.this, s.id, newName);
            ((ViewGroup) container.getParent()).removeView(container);
            refresh();
            Toast.makeText(MainActivity.this, "已重命名", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(okBtn);
        panel.addView(btnRow);

        container.setOnClickListener(v -> {
            ((ViewGroup) v.getParent()).removeView(v);
        });
        container.addView(panel);

        ViewGroup root = (ViewGroup) getWindow().getDecorView()
            .findViewById(android.R.id.content);
        root.addView(container);
    }

    // 网络音乐列表（从 bzy/list.csv 加载）
    private java.util.List<SoundStore.Sound> networkList = new java.util.ArrayList<>();
    // 当前下载中的 id，用于按钮状态
    private java.util.Set<String> downloadingIds = new java.util.HashSet<>();

    // -------- 乐库页面（支持搜索 + 多选删除 + 网络音乐）--------
    private void renderLibrary() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int cardDiv = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED");
        int barBg = dark ? Color.parseColor("#1a1a1a") : Color.parseColor("#F0F0F0");
        int tagBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F0F0F0");
        int tagText = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int highlightColor = dark ? Color.parseColor("#ffd54f") : Color.parseColor("#f59e0b");
        int greenColor = Color.parseColor("#07C160");

        final List<SoundStore.Sound> localList = SoundStore.getLibraryList(this);
        libLocalList = localList;
        // 每次 render 先清空网络列表，重新加载
        networkList.clear();

        // ===== 顶部操作栏 =====
        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setBackgroundColor(barBg);
        actionBar.setPadding(dip2px(14), 0, dip2px(14), 0);
        LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(44));
        actionBar.setLayoutParams(ablp);

        TextView leftBtn = new TextView(this);
        leftBtn.setTextSize(14);
        leftBtn.setTextColor(dark ? Color.parseColor("#4dd0e1") : Color.parseColor("#07C160"));
        leftBtn.setGravity(Gravity.CENTER_VERTICAL);
        leftBtn.setPadding(0, dip2px(10), dip2px(14), dip2px(10));
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView rightBtn = new TextView(this);
        rightBtn.setTextSize(14);
        rightBtn.setTextColor(Color.parseColor("#999999"));
        rightBtn.setGravity(Gravity.CENTER);
        rightBtn.setPadding(dip2px(12), dip2px(6), dip2px(12), dip2px(6));
        rightBtn.setEnabled(false);
        libLeftBtn = leftBtn;
        libRightBtn = rightBtn;
        LinearLayout.LayoutParams rbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rbp.leftMargin = dip2px(8);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(dip2px(6));
        btnBg.setColor(Color.parseColor("#22000000"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            rightBtn.setBackground(btnBg);
        } else {
            rightBtn.setBackgroundDrawable(btnBg);
        }

        View spacer = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 0, 1);
        spacer.setLayoutParams(sp);

        actionBar.addView(leftBtn, lbp);
        actionBar.addView(spacer);
        actionBar.addView(rightBtn, rbp);
        contentArea.addView(actionBar);

        // ===== 搜索框 =====
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundColor(cardBg);
        searchBar.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        searchBar.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView searchIcon = new TextView(this);
        searchIcon.setText("🔍");
        searchIcon.setTextSize(16);
        searchIcon.setGravity(Gravity.CENTER);
        searchIcon.setPadding(0, 0, dip2px(8), 0);
        searchIcon.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final EditText searchInput = new EditText(this);
        searchInput.setHint("搜索白噪音名称（雨声、海浪...）");
        searchInput.setTextSize(15);
        searchInput.setTextColor(textMain);
        searchInput.setHintTextColor(textSub);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        final TextView clearBtn = new TextView(this);
        clearBtn.setText("清除");
        clearBtn.setTextSize(13);
        clearBtn.setTextColor(dark ? Color.parseColor("#4dd0e1") : greenColor);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(dip2px(10), 0, 0, 0);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        searchBar.addView(searchIcon);
        searchBar.addView(searchInput);
        searchBar.addView(clearBtn);

        View searchDiv = new View(this);
        searchDiv.setBackgroundColor(cardDiv);
        searchDiv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));

        contentArea.addView(searchBar);
        contentArea.addView(searchDiv);

        // ===== 列表区 =====
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        final LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setBackgroundColor(cardBg);
        items.setPadding(0, 0, 0, dip2px(20)); // 底部留白避免贴底
        sv.addView(items);

        // ===== 加载指示器 =====
        final LinearLayout loadingWrap = new LinearLayout(this);
        loadingWrap.setOrientation(LinearLayout.HORIZONTAL);
        loadingWrap.setGravity(Gravity.CENTER);
        loadingWrap.setPadding(0, dip2px(40), 0, dip2px(20));
        TextView loadingTv = new TextView(this);
        loadingTv.setText("正在加载网络音乐列表...");
        loadingTv.setTextSize(14);
        loadingTv.setTextColor(textSub);
        loadingWrap.addView(loadingTv);
        items.addView(loadingWrap);

        // ===== 网络音乐分区标题（初始隐藏，等加载完显示）=====
        final LinearLayout netSectionHeader = new LinearLayout(this);
        netSectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        netSectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        netSectionHeader.setBackgroundColor(tagBg);
        netSectionHeader.setPadding(dip2px(14), dip2px(8), dip2px(14), dip2px(8));
        netSectionHeader.setVisibility(View.GONE);
        netSectionHeader.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView netLabel = new TextView(this);
        netLabel.setText("🌐 网络音乐");
        netLabel.setTextSize(13);
        netLabel.setTextColor(tagText);
        netLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        final TextView netCount = new TextView(this);
        netCount.setTextSize(12);
        netCount.setTextColor(tagText);
        netSectionHeader.addView(netLabel);
        netSectionHeader.addView(netCount);

        // ===== 构建列表行（合并本地 + 网络），需在 Thread 之前声明以便引用 =====
        final Runnable buildRows = new Runnable() {
            @Override public void run() {
                items.removeAllViews();
                // 如果还没加载完，先放加载指示器
                if (loadingWrap.getParent() == null) {
                    items.addView(loadingWrap);
                    if (netSectionHeader.getParent() == null && !networkList.isEmpty()) {
                        items.addView(netSectionHeader);
                    }
                } else {
                    // 移除旧的 section header 再重新插入
                    if (netSectionHeader.getParent() != null) {
                        ((ViewGroup) netSectionHeader.getParent()).removeView(netSectionHeader);
                    }
                    if (!networkList.isEmpty()) {
                        netCount.setText("(" + networkList.size() + ")");
                        netSectionHeader.setVisibility(View.VISIBLE);
                        // 找本地列表结尾位置
                        int insertPos = Math.min(localList.size(), items.getChildCount());
                        items.addView(netSectionHeader, insertPos);
                    }
                }

                String keywordRaw = searchInput.getText() == null ? "" : searchInput.getText().toString();
                String keyword = keywordRaw.trim().toLowerCase(java.util.Locale.getDefault());

                java.util.List<SoundStore.Sound> localFiltered = new java.util.ArrayList<>();
                java.util.List<SoundStore.Sound> netFiltered = new java.util.ArrayList<>();

                for (SoundStore.Sound ls : localList) {
                    if (keyword.isEmpty()) localFiltered.add(ls);
                    else {
                        String n = (ls.name == null ? "" : ls.name).toLowerCase(java.util.Locale.getDefault());
                        String tag = ls.isCustom ? "custom" : "built";
                        if (n.contains(keyword) || tag.contains(keyword)) localFiltered.add(ls);
                    }
                }
                for (SoundStore.Sound ns : networkList) {
                    if (keyword.isEmpty()) netFiltered.add(ns);
                    else {
                        String n = (ns.name == null ? "" : ns.name).toLowerCase(java.util.Locale.getDefault());
                        if (n.contains(keyword)) netFiltered.add(ns);
                    }
                }

                if (localFiltered.isEmpty() && netFiltered.isEmpty()) {
                    TextView empty = new TextView(MainActivity.this);
                    if (keyword.isEmpty()) {
                        empty.setText("乐库暂无白噪音，回到首页「猜你喜欢」试试吧");
                    } else {
                        empty.setText("没有找到和「" + keywordRaw.trim() + "」匹配的白噪音\n试试其他关键字吧");
                    }
                    empty.setTextSize(14);
                    empty.setTextColor(textSub);
                    empty.setGravity(Gravity.CENTER);
                    empty.setLineSpacing(0, 1.2f);
                    empty.setPadding(0, dip2px(60), 0, 0);
                    empty.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    items.addView(empty);
                    return;
                }

                // 本地分区标题（搜索时显示）
                if (!keyword.isEmpty() && !localFiltered.isEmpty()) {
                    addSectionHeader(items, "本地白噪音", localFiltered.size(), tagBg, tagText);
                }
                for (SoundStore.Sound ls : localFiltered) {
                    addLocalRow(items, ls, textMain, textSub, cardBg, cardDiv, dark, highlightColor, greenColor, keyword, keywordRaw);
                }

                // 网络分区标题（搜索时显示）
                if (!keyword.isEmpty() && !netFiltered.isEmpty()) {
                    addSectionHeader(items, "🌐 网络音乐", netFiltered.size(), tagBg, tagText);
                }
                for (SoundStore.Sound ns : netFiltered) {
                    addNetworkRow(items, ns, textMain, textSub, cardBg, cardDiv, dark, highlightColor, greenColor, keyword, keywordRaw);
                }
            }
        };

        // ===== 异步加载 CSV =====
        final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread() {
            @Override public void run() {
                try {
                    java.net.URL url = new java.net.URL("https://pic98.oss-cn-beijing.aliyuncs.com/bzy/list.csv");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("Accept-Charset", "UTF-8");
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            // 支持两列 name,url 或三列 name,url,bgImageUrl
                            int firstComma = line.indexOf(',');
                            if (firstComma > 0) {
                                String name = line.substring(0, firstComma).trim();
                                String rest = line.substring(firstComma + 1).trim();
                                int secondComma = rest.indexOf(',');
                                String musicUrl;
                                String bgUrl = null;
                                if (secondComma > 0) {
                                    musicUrl = rest.substring(0, secondComma).trim();
                                    bgUrl = rest.substring(secondComma + 1).trim();
                                    if (bgUrl.isEmpty()) bgUrl = null;
                                } else {
                                    musicUrl = rest;
                                }
                                if (!name.isEmpty() && !musicUrl.isEmpty()) {
                                    SoundStore.Sound s = SoundStore.Sound.fromNetwork(musicUrl, name, bgUrl);
                                    SoundStore.checkNetworkCache(MainActivity.this, s);
                                    networkList.add(s);
                                }
                            }
                        }
                        br.close();
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                uiHandler.post(() -> {
                    loadingWrap.setVisibility(View.GONE);
                    if (!networkList.isEmpty()) {
                        netCount.setText("(" + networkList.size() + ")");
                        netSectionHeader.setVisibility(View.VISIBLE);
                        items.addView(netSectionHeader, 1); // 插在本地列表之后
                    }
                    buildRows.run();
                });
            }
        }.start();

        // ===== 搜索框文本变化监听 =====
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                clearBtn.setVisibility((s != null && s.toString().trim().length() > 0) ? View.VISIBLE : View.GONE);
                libSelected.clear();
                updateLibraryActionBar(leftBtn, rightBtn, localList);
                buildRows.run();
            }
        };
        searchInput.addTextChangedListener(tw);

        clearBtn.setOnClickListener(v -> {
            searchInput.setText("");
            try { searchInput.requestFocus(); } catch (Throwable ignored) {}
        });

        // ===== 初始化按钮 =====
        updateLibraryActionBar(leftBtn, rightBtn, localList);

        leftBtn.setOnClickListener(v -> {
            libSelectMode = !libSelectMode;
            if (!libSelectMode) libSelected.clear();
            updateLibraryActionBar(leftBtn, rightBtn, localList);
            buildRows.run();
        });

        rightBtn.setOnClickListener(v -> {
            if (libSelected.isEmpty()) return;
            final int count = libSelected.size();
            new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("确认删除")
                .setMessage("确定要删除选中的 " + count + " 个白噪音吗？\n（内置白噪音会从首页移除，自定义白噪音将永久删除）")
                .setPositiveButton("删除", (dialog, which) -> {
                    SoundStore.bulkDelete(MainActivity.this, libSelected);
                    Toast.makeText(MainActivity.this, "已删除 " + count + " 个", Toast.LENGTH_SHORT).show();
                    libSelected.clear();
                    libSelectMode = false;
                    items.removeAllViews();
                    contentArea.removeAllViews();
                    renderLibrary();
                })
                .setNegativeButton("取消", null)
                .show();
        });

        contentArea.addView(sv);
    }

    // 添加分区标题行
    private void addSectionHeader(LinearLayout items, String title, int count, int bgColor, int textColor) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(bgColor);
        header.setPadding(dip2px(14), dip2px(8), dip2px(14), dip2px(8));
        header.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tv = new TextView(this);
        tv.setText(title + " (" + count + ")");
        tv.setTextSize(13);
        tv.setTextColor(textColor);
        header.addView(tv);
        items.addView(header);

        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#EDEDED"));
        line.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        items.addView(line);
    }

    // 添加本地白噪音行
    private void addLocalRow(LinearLayout items, SoundStore.Sound s,
            int textMain, int textSub, int cardBg, int cardDiv, boolean dark,
            int highlightColor, int greenColor,
            String keyword, String keywordRaw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dip2px(14), dip2px(14), dip2px(14), dip2px(14));
        row.setBackgroundColor(cardBg);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (libSelectMode) {
            TextView box = makeCheckBox(s, dark, greenColor);
            row.addView(box);
            final TextView fb = box;
            final SoundStore.Sound fs = s;
            View.OnClickListener toggleListener = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (libSelected.contains(fs.id)) libSelected.remove(fs.id);
                    else libSelected.add(fs.id);
                    refreshCheckBox(fb, fs, dark, greenColor);
                    // 即时刷新按钮状态（显示数量 / 高亮 / 可用性）
                    if (libLeftBtn != null && libRightBtn != null) {
                        updateLibraryActionBar(libLeftBtn, libRightBtn, libLocalList);
                    }
                }
            };
            box.setOnClickListener(toggleListener);
            row.setOnClickListener(toggleListener);
        }

        int[] colors = s.getChatBgColors();
        TextView avatar = new TextView(this);
        avatar.setText(s.name.substring(0, 1));
        avatar.setTextSize(20);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(dip2px(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) avatar.setBackground(bg);
        else avatar.setBackgroundDrawable(bg);
        LinearLayout.LayoutParams avp = new LinearLayout.LayoutParams(dip2px(48), dip2px(48));
        if (libSelectMode) avp.rightMargin = dip2px(12);
        else avp.leftMargin = 0;
        avatar.setLayoutParams(avp);
        row.addView(avatar);

        LinearLayout rightWrap = new LinearLayout(this);
        rightWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rwp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rwp.leftMargin = libSelectMode ? 0 : dip2px(12);
        rightWrap.setLayoutParams(rwp);

        TextView nameLabel = new TextView(this);
        String tag = s.isCustom ? "（自定义）" : "";
        String status = s.isDeleted ? "（已从首页移除）" : "";
        String fullName = s.name + tag + status;
        if (!keyword.isEmpty()) {
            nameLabel.setText(highlightKeyword(fullName, keywordRaw.trim(), highlightColor, textMain));
        } else {
            nameLabel.setText(fullName);
        }
        nameLabel.setTextSize(16);
        nameLabel.setTextColor(textMain);
        nameLabel.getPaint().setFakeBoldText(true);
        rightWrap.addView(nameLabel);

        TextView desc = new TextView(this);
        desc.setText(libSelectMode ? "点击选择/取消" : "点击恢复到首页并进入聊天");
        desc.setTextSize(12);
        desc.setTextColor(textSub);
        desc.setPadding(0, dip2px(4), 0, 0);
        rightWrap.addView(desc);
        row.addView(rightWrap);

        if (!libSelectMode) {
            // 长按显示详情菜单（所有类型都支持）
            row.setOnLongClickListener(v -> {
                showLibraryItemMenu(v, s);
                return true;
            });
            row.setOnClickListener(v -> {
                if (s.isDeleted) SoundStore.markDeleted(MainActivity.this, s.id, false);
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("sound_id", s.id);
                startActivityForResult(i, REQ_CHAT);
            });
        }

        items.addView(row);
        View line = new View(this);
        line.setBackgroundColor(cardDiv);
        line.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        items.addView(line);
    }

    // 添加网络音乐行
    private void addNetworkRow(LinearLayout items, SoundStore.Sound s,
            int textMain, int textSub, int cardBg, int cardDiv, boolean dark,
            int highlightColor, int greenColor,
            String keyword, String keywordRaw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dip2px(14), dip2px(12), dip2px(14), dip2px(12));
        row.setBackgroundColor(cardBg);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 头像（圆角渐变）
        int[] colors = s.getChatBgColors();
        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams awp = new LinearLayout.LayoutParams(dip2px(48), dip2px(48));
        avatarWrap.setLayoutParams(awp);

        TextView avatar = new TextView(this);
        avatar.setText(s.name.substring(0, 1));
        avatar.setTextSize(20);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(dip2px(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) avatar.setBackground(bg);
        else avatar.setBackgroundDrawable(bg);
        avatar.setLayoutParams(new FrameLayout.LayoutParams(dip2px(48), dip2px(48)));
        avatarWrap.addView(avatar);

        // 右下角小角标：未下载=🌐，已下载=✓
        TextView badge = new TextView(this);
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(s.localPath != null ? greenColor : Color.parseColor("#888888"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) badge.setBackground(badgeBg);
        else badge.setBackgroundDrawable(badgeBg);
        badge.setText(s.localPath != null ? "✓" : "🌐");
        badge.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams bbp = new FrameLayout.LayoutParams(dip2px(18), dip2px(18));
        bbp.gravity = Gravity.BOTTOM | Gravity.END;
        badge.setLayoutParams(bbp);
        avatarWrap.addView(badge);
        row.addView(avatarWrap);

        // 右侧文字
        LinearLayout rightWrap = new LinearLayout(this);
        rightWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rwp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        rwp.leftMargin = dip2px(12);
        rightWrap.setLayoutParams(rwp);

        TextView nameLabel = new TextView(this);
        if (!keyword.isEmpty()) {
            nameLabel.setText(highlightKeyword(s.name, keywordRaw.trim(), highlightColor, textMain));
        } else {
            nameLabel.setText(s.name);
        }
        nameLabel.setTextSize(16);
        nameLabel.setTextColor(textMain);
        nameLabel.getPaint().setFakeBoldText(true);
        rightWrap.addView(nameLabel);

        TextView desc = new TextView(this);
        if (s.localPath != null) {
            desc.setText("已下载 · " + SoundStore.formatFileSize(s.fileSize) + " · 长按查看详情");
        } else {
            desc.setText("未下载 · 长按下载或查看详情");
        }
        desc.setTextSize(12);
        desc.setTextColor(textSub);
        desc.setPadding(0, dip2px(4), 0, 0);
        rightWrap.addView(desc);
        row.addView(rightWrap);

        // 长按菜单（统一入口，所有类型都支持）
        row.setOnLongClickListener(v -> {
            showLibraryItemMenu(v, s);
            return true;
        });

        // 点击事件：直接进入聊天（优先用本地缓存，否则 URL 播放），不需要提前下载
        row.setOnClickListener(v -> {
            // 确保网络音乐先注册到 SoundStore（或更新已有记录
            SoundStore.Sound existing = SoundStore.findById(MainActivity.this, s.id);
            if (existing == null) {
                SoundStore.addCustom(MainActivity.this, s.id, s.name, s.url, s.bgImageUrl, s.localPath);
            } else {
                if (s.url != null) existing.url = s.url;
                if (s.bgImageUrl != null) existing.bgImageUrl = s.bgImageUrl;
                if (s.localPath != null) existing.localPath = s.localPath;
                SoundStore.save(MainActivity.this);
            }
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("sound_id", s.id);
            startActivityForResult(i, REQ_CHAT);
        });

        items.addView(row);
        View line = new View(this);
        line.setBackgroundColor(cardDiv);
        line.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        items.addView(line);
    }

    // 乐库条目长按菜单（所有类型：内置、自定义、网络音乐）
    private void showLibraryItemMenu(View anchor, final SoundStore.Sound s) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;

        FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#88000000"));
        dialogWrap.setOnClickListener(v -> ((ViewGroup) dialogWrap.getParent()).removeView(dialogWrap));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dip2px(260), FrameLayout.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER;
        panel.setLayoutParams(plp);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(panelBg);
        pbg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) panel.setBackground(pbg);
        else panel.setBackgroundDrawable(pbg);

        // 标题
        TextView titleTv = new TextView(this);
        titleTv.setText(s.name);
        titleTv.setTextSize(16);
        titleTv.setTextColor(textMain);
        titleTv.getPaint().setFakeBoldText(true);
        titleTv.setPadding(dip2px(18), dip2px(16), dip2px(18), dip2px(8));
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(titleTv);

        // 分隔线
        View titleDiv = new View(this);
        titleDiv.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED"));
        titleDiv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
        panel.addView(titleDiv);

        // 按类型生成菜单项：详情始终在最前
        java.util.List<String> menuItems = new java.util.ArrayList<>();
        menuItems.add("📋  详情");
        if (s.isNetwork && s.localPath == null) {
            menuItems.add("⬇  下载到本地");
        }
        if (s.localPath != null) {
            menuItems.add("🗑  删除缓存");
        }
        for (int i = 0; i < menuItems.size(); i++) {
            final String label = menuItems.get(i);
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(15);
            tv.setTextColor(label.startsWith("🗑") ? Color.parseColor("#ef4444") : textMain);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(dip2px(18), dip2px(13), dip2px(18), dip2px(13));
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            tv.setOnClickListener(v -> {
                ((ViewGroup) dialogWrap.getParent()).removeView(dialogWrap);
                if (label.startsWith("📋")) showLibraryDetailDialog(s);
                else if (label.startsWith("⬇")) downloadNetSound(s);
                else if (label.startsWith("🗑")) deleteNetCache(s);
            });
            panel.addView(tv);

            if (i < menuItems.size() - 1) {
                View div = new View(this);
                div.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED"));
                div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f)));
                panel.addView(div);
            }
        }

        dialogWrap.addView(panel);
        ((ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content)).addView(dialogWrap);
    }

    // 下载网络音乐
    private void downloadNetSound(final SoundStore.Sound s) {
        if (downloadingIds.contains(s.id)) {
            Toast.makeText(this, "正在下载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        downloadingIds.add(s.id);
        Toast.makeText(this, "开始下载: " + s.name, Toast.LENGTH_SHORT).show();

        final String soundId = s.id;
        final String soundUrl = s.url;
        final String soundName = s.name;
        final String soundBgUrl = s.bgImageUrl;
        new android.os.AsyncTask<Void, Integer, java.io.File>() {
            @Override protected java.io.File doInBackground(Void... params) {
                try {
                    java.io.File cacheDir = SoundStore.getNetworkCacheDir(MainActivity.this);
                    String fileName = SoundStore.getCacheFileName(soundUrl);
                    java.io.File outFile = new java.io.File(cacheDir, fileName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                    java.net.URL url = new java.net.URL(soundUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    int total = conn.getContentLength();
                    java.io.InputStream is = conn.getInputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    conn.disconnect();
                    outFile.setReadable(true, false);
                    s.localPath = outFile.getAbsolutePath();
                    s.fileSize = outFile.length();
                    return outFile;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override protected void onPostExecute(java.io.File result) {
                downloadingIds.remove(soundId);
                if (result != null) {
                    String localPath = result.getAbsolutePath();
                    SoundStore.Sound existing = SoundStore.findById(MainActivity.this, soundId);
                    if (existing == null) {
                        SoundStore.addCustom(MainActivity.this, soundId, soundName, soundUrl, soundBgUrl, localPath);
                    } else {
                        existing.localPath = localPath;
                        existing.fileSize = result.length();
                        if (soundBgUrl != null && existing.bgImageUrl == null) {
                            existing.bgImageUrl = soundBgUrl;
                        }
                        SoundStore.save(MainActivity.this);
                    }
                    Toast.makeText(MainActivity.this, "下载完成: " + soundName, Toast.LENGTH_SHORT).show();
                    refreshLibraryList();
                } else {
                    Toast.makeText(MainActivity.this, "下载失败，请检查网络: " + soundName, Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    // 删除网络音乐缓存
    private void deleteNetCache(SoundStore.Sound s) {
        if (s.localPath == null) return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除缓存")
            .setMessage("确定删除「" + s.name + "」的本地缓存吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                java.io.File f = new java.io.File(s.localPath);
                if (f.exists()) f.delete();
                Toast.makeText(this, "已删除缓存: " + s.name, Toast.LENGTH_SHORT).show();
                refreshLibraryList();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // 显示乐库条目详情（名称/网络URL/本地路径/背景URL/背景本地路径）
    private void showLibraryDetailDialog(SoundStore.Sound s) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.parseColor("#202020");
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;

        FrameLayout dialogWrap = new FrameLayout(this);
        dialogWrap.setBackgroundColor(Color.parseColor("#88000000"));
        dialogWrap.setOnClickListener(v -> ((ViewGroup) dialogWrap.getParent()).removeView(dialogWrap));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(panelBg);
        panel.setPadding(dip2px(24), dip2px(20), dip2px(24), dip2px(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dip2px(320), FrameLayout.LayoutParams.WRAP_CONTENT);
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
        if (s.isNetwork) typeLabel = "网络音乐";
        else if (s.isCustom) typeLabel = "自定义白噪音";
        else typeLabel = "内置白噪音";
        addDetailRow(panel, "类型", typeLabel, textMain, textSub);

        addDetailRow(panel, "名称", s.name, textMain, textSub);

        addDetailRow(panel, "声音ID", s.id, textMain, textSub);

        if (s.url != null && !s.url.isEmpty()) {
            addDetailRow(panel, "网络地址", s.url, textMain, textSub);
        }
        if (s.localPath != null && !s.localPath.isEmpty()) {
            addDetailRow(panel, "本地路径", s.localPath, textMain, textSub);
            addDetailRow(panel, "文件大小", SoundStore.formatFileSize(s.fileSize), textMain, textSub);
        } else if (s.url != null && !s.url.isEmpty()) {
            // 远程音乐：先显示占位，后台异步用 HEAD 获取远程文件大小
            final TextView remoteSizeTv = addDetailRow(panel, "远程文件大小", "正在获取...", textMain, textSub);
            final String remoteUrl = s.url;
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
        if (s.bgImageUrl != null && !s.bgImageUrl.isEmpty()) {
            addDetailRow(panel, "背景图网络地址", s.bgImageUrl, textMain, textSub);
        }
        if (s.bgImageLocalPath != null && !s.bgImageLocalPath.isEmpty()) {
            addDetailRow(panel, "背景图本地路径", s.bgImageLocalPath, textMain, textSub);
        }
        if (s.resId > 0) {
            addDetailRow(panel, "资源ID", "内置资源 (" + s.resId + ")", textMain, textSub);
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
        closeBtn.setOnClickListener(v -> ((ViewGroup) dialogWrap.getParent()).removeView(dialogWrap));
        panel.addView(closeBtn);

        dialogWrap.addView(panel);
        ((ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content)).addView(dialogWrap);
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

    // 刷新乐库列表（下载/删除后调用）
    private void refreshLibraryList() {
        // 重新检查缓存并刷新
        for (SoundStore.Sound s : networkList) {
            SoundStore.checkNetworkCache(this, s);
        }
        contentArea.removeAllViews();
        renderLibrary();
    }

    // 生成勾选框
    private TextView makeCheckBox(SoundStore.Sound s, boolean dark, int greenColor) {
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setShape(GradientDrawable.RECTANGLE);
        boxBg.setCornerRadius(dip2px(6));
        boxBg.setColor(Color.TRANSPARENT);
        boxBg.setStroke(dip2px(2), dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#CCCCCC"));
        TextView box = new TextView(this);
        box.setText(libSelected.contains(s.id) ? "✓" : "");
        box.setTextColor(greenColor);
        box.setTextSize(18);
        box.setGravity(Gravity.CENTER);
        box.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dip2px(24), dip2px(24));
        bp.rightMargin = dip2px(12);
        box.setLayoutParams(bp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) box.setBackground(boxBg);
        else box.setBackgroundDrawable(boxBg);
        return box;
    }

    private void refreshCheckBox(TextView box, SoundStore.Sound s, boolean dark, int greenColor) {
        boolean on = libSelected.contains(s.id);
        box.setText(on ? "✓" : "");
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setShape(GradientDrawable.RECTANGLE);
        bg2.setCornerRadius(dip2px(6));
        if (on) {
            bg2.setColor(greenColor);
            bg2.setStroke(dip2px(2), greenColor);
            box.setTextColor(Color.WHITE);
        } else {
            bg2.setColor(Color.TRANSPARENT);
            bg2.setStroke(dip2px(2), dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#CCCCCC"));
            box.setTextColor(greenColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) box.setBackground(bg2);
        else box.setBackgroundDrawable(bg2);
    }

    private void updateLibraryActionBar(TextView leftBtn, TextView rightBtn, List<SoundStore.Sound> list) {
        boolean dark = isDarkMode(this);
        rightBtn.setVisibility(View.VISIBLE);
        if (libSelectMode) {
            leftBtn.setText("取消选择");
            int n = libSelected.size();
            if (n > 0) {
                rightBtn.setText("删除（" + n + "）");
                rightBtn.setTextColor(Color.parseColor("#FFFFFF"));
                GradientDrawable bg2 = new GradientDrawable();
                bg2.setCornerRadius(dip2px(6));
                bg2.setColor(Color.parseColor("#E53935"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) rightBtn.setBackground(bg2);
                else rightBtn.setBackgroundDrawable(bg2);
                rightBtn.setEnabled(true);
            } else {
                rightBtn.setText("删除");
                rightBtn.setTextColor(Color.parseColor("#999999"));
                GradientDrawable bg2 = new GradientDrawable();
                bg2.setCornerRadius(dip2px(6));
                bg2.setColor(Color.parseColor("#22000000"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) rightBtn.setBackground(bg2);
                else rightBtn.setBackgroundDrawable(bg2);
                rightBtn.setEnabled(false);
            }
        } else {
            leftBtn.setText("选择");
            rightBtn.setText("");
            rightBtn.setVisibility(View.GONE);
            rightBtn.setEnabled(false);
        }
    }

    // -------- 发现页面：泡泡白噪音 WebView --------
    private void renderDiscover() {
        boolean dark = isDarkMode(this);
        int bg = dark ? Color.parseColor("#121212") : Color.parseColor("#F7F7F7");

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        holder.setBackgroundColor(bg);

        WebView webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 页面全部在 WebView 内打开，不调用外部浏览器
        webView.setWebViewClient(new WebViewClient() {
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return false;
            }
        });

        webView.loadUrl("https://www.ppbzy.com");

        holder.addView(webView);
        contentArea.addView(holder);
    }

    // -------- 我页面 --------
    private void renderMe() {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#999999");
        int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        int pageBg = dark ? Color.parseColor("#121212") : Color.parseColor("#EDEDED");

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        sv.setBackgroundColor(pageBg);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, dip2px(20)); // 底部留白避免贴底
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
        uname.setText("小白");
        uname.setTextSize(19);
        uname.setTextColor(textMain);
        uname.getPaint().setFakeBoldText(true);
        ptext.addView(uname);

        TextView uacc = new TextView(this);
        uacc.setText("微信号: 小白号");
        uacc.setTextSize(12);
        uacc.setTextColor(textSub);
        uacc.setPadding(0, dip2px(6), 0, 0);
        ptext.addView(uacc);
        profile.addView(ptext);

        TextView spacer = new TextView(this);
        spacer.setHeight(dip2px(12));
        container.addView(spacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(12)));

        // 功能列表
        container.addView(makeMenuRow("⚙️", "设置", "主题/后台播放", v -> showSettingsDialog()));
        container.addView(lineSep());
        container.addView(makeMenuRow("🔄", "检查更新", "当前版本: " + UpdateChecker.getCurrentVersion(this), v -> doCheckUpdate()));
        container.addView(makeMenuRow("➕", "添加白噪音", "添加自定义音频URL", v -> showAddDialog()));
        container.addView(lineSep());
        container.addView(makeMenuRow("📥", "导入白噪音", "从备份文件恢复", v -> doImportSounds()));
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

    // -------- 设置弹窗 --------
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

        // 主题模式
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

        TextView themeHint = new TextView(this);
        themeHint.setText("当前: " + getThemeName(currentMode));
        themeHint.setTextSize(11);
        themeHint.setTextColor(textSub);
        themeHint.setGravity(Gravity.CENTER);
        themeHint.setPadding(0, dip2px(6), 0, 0);
        panel.addView(themeHint);

        View divider = new View(this);
        divider.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E5E5E5"));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
        dlp.topMargin = dip2px(10);
        divider.setLayoutParams(dlp);
        panel.addView(divider);

        // 后台播放开关
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

        View divider2 = new View(this);
        divider2.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E5E5E5"));
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
        dlp2.topMargin = dip2px(10);
        divider2.setLayoutParams(dlp2);
        panel.addView(divider2);

        // 背景显示模式
        TextView bgModeLabel = new TextView(this);
        bgModeLabel.setText("聊天背景显示模式");
        bgModeLabel.setTextSize(14);
        bgModeLabel.setTextColor(textMain);
        bgModeLabel.setPadding(dip2px(16), dip2px(12), dip2px(16), dip2px(8));
        panel.addView(bgModeLabel);

        final int currentBgMode = getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
            .getInt("bg_display_mode", 0); // 0=图片优先, 1=视频优先
        final int[] bgModes = new int[]{0, 1};
        final String[] bgModeLabels = new String[]{"图片优先", "视频优先"};
        LinearLayout bgModeBtns = new LinearLayout(this);
        bgModeBtns.setOrientation(LinearLayout.HORIZONTAL);
        bgModeBtns.setPadding(dip2px(16), 0, dip2px(16), dip2px(4));
        bgModeBtns.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < bgModes.length; i++) {
            Button b = new Button(this);
            b.setText(bgModeLabels[i]);
            b.setTextSize(13);
            final boolean active = currentBgMode == bgModes[i];
            b.setTextColor(active ? Color.WHITE : textMain);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dip2px(8));
            g.setColor(active ? btnActive : btnBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                b.setBackground(g);
            } else {
                b.setBackgroundDrawable(g);
            }
            final int mode = bgModes[i];
            b.setOnClickListener(v -> {
                getSharedPreferences("whitenoise_settings", MODE_PRIVATE)
                    .edit().putInt("bg_display_mode", mode).apply();
                Toast.makeText(MainActivity.this, "已切换为" + bgModeLabels[mode] + "模式", Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, dip2px(38), 1);
            if (i > 0) blp.leftMargin = dip2px(8);
            b.setLayoutParams(blp);
            bgModeBtns.addView(b);
        }
        panel.addView(bgModeBtns);

        TextView bgModeHint = new TextView(this);
        bgModeHint.setText("视频优先时，若无视频则显示图片");
        bgModeHint.setTextSize(11);
        bgModeHint.setTextColor(textSub);
        bgModeHint.setGravity(Gravity.CENTER);
        bgModeHint.setPadding(0, dip2px(6), 0, 0);
        panel.addView(bgModeHint);

        // 分割线
        View divider3 = new View(this);
        divider3.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E5E5E5"));
        LinearLayout.LayoutParams dlp3 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dip2px(0.5f));
        dlp3.topMargin = dip2px(12);
        divider3.setLayoutParams(dlp3);
        panel.addView(divider3);

        Button close = new Button(this);
        close.setText("关闭");
        close.setTextSize(15);
        close.setTextColor(dark ? Color.parseColor("#4dd0e1") : Color.parseColor("#07C160"));
        close.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dip2px(8);
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

    // -------- 添加白噪音弹窗（AI生成音频与背景图）--------
    private void showAddDialog() {
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#8a8a8a") : Color.parseColor("#666666");
        final int panelBg = dark ? Color.parseColor("#1e1e1e") : Color.WHITE;
        final int inputBg = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#F5F5F5");
        final int btnGreen = Color.parseColor("#07C160");
        final int btnGray = dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#E0E0E0");

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
        nameInput.setHint("名称（如：雨声、海浪声、咖啡馆）");
        nameInput.setTextSize(15);
        nameInput.setTextColor(textMain);
        nameInput.setHintTextColor(textSub);
        nameInput.setBackgroundColor(inputBg);
        nameInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        panel.addView(nameInput);

        // URL 行 + AI 搜索按钮
        LinearLayout urlRow = new LinearLayout(this);
        urlRow.setOrientation(LinearLayout.HORIZONTAL);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams urp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        urp.topMargin = dip2px(10);
        urlRow.setLayoutParams(urp);

        final EditText urlInput = new EditText(MainActivity.this);
        urlInput.setHint("音频URL 或 本地文件路径（可留空）");
        urlInput.setTextSize(14);
        urlInput.setTextColor(textMain);
        urlInput.setHintTextColor(textSub);
        urlInput.setBackgroundColor(inputBg);
        urlInput.setPadding(dip2px(12), dip2px(10), dip2px(8), dip2px(10));
        LinearLayout.LayoutParams uip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        urlInput.setLayoutParams(uip);

        final TextView aiSearchBtn = new TextView(MainActivity.this);
        aiSearchBtn.setText("✨ AI生成");
        aiSearchBtn.setTextSize(13);
        aiSearchBtn.setTextColor(Color.WHITE);
        aiSearchBtn.setGravity(Gravity.CENTER);
        aiSearchBtn.setPadding(dip2px(10), dip2px(8), dip2px(10), dip2px(8));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dip2px(6));
        searchBg.setColor(Color.parseColor("#4A90D9"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) aiSearchBtn.setBackground(searchBg);
        else aiSearchBtn.setBackgroundDrawable(searchBg);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dip2px(36));
        sbp.leftMargin = dip2px(8);
        aiSearchBtn.setLayoutParams(sbp);

        urlRow.addView(urlInput);
        urlRow.addView(aiSearchBtn);
        panel.addView(urlRow);

        // 搜索状态提示
        final TextView searchStatus = new TextView(MainActivity.this);
        searchStatus.setText("");
        searchStatus.setTextSize(12);
        searchStatus.setTextColor(textSub);
        searchStatus.setPadding(0, dip2px(4), 0, 0);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        ssLp.topMargin = dip2px(2);
        searchStatus.setLayoutParams(ssLp);

        // 背景图输入
        final EditText bgImgInput = new EditText(MainActivity.this);
        bgImgInput.setHint("背景图片URL（可留空，AI生成可自动补全）");
        bgImgInput.setTextSize(14);
        bgImgInput.setTextColor(textMain);
        bgImgInput.setHintTextColor(textSub);
        bgImgInput.setBackgroundColor(inputBg);
        bgImgInput.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams bgiLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bgiLp.topMargin = dip2px(10);
        bgImgInput.setLayoutParams(bgiLp);

        // 按钮行：确认 + 取消
        LinearLayout btnRow = new LinearLayout(MainActivity.this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.topMargin = dip2px(16);
        btnRow.setLayoutParams(brp);

        Button confirm = new Button(MainActivity.this);
        confirm.setText("添加");
        confirm.setTextSize(15);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackgroundColor(btnGreen);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dip2px(44), 1);
        confirm.setLayoutParams(cp);

        Button cancel = new Button(MainActivity.this);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setTextColor(textSub);
        cancel.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(0, dip2px(44), 1);
        cap.leftMargin = dip2px(8);
        cancel.setLayoutParams(cap);

        btnRow.addView(confirm);
        btnRow.addView(cancel);

        // AI 搜索逻辑
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        aiSearchBtn.setOnClickListener(v -> {
            final String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(MainActivity.this, "请先填写白噪音名称", Toast.LENGTH_SHORT).show();
                return;
            }
            // 禁用按钮，显示状态
            aiSearchBtn.setEnabled(false);
            aiSearchBtn.setText("生成中...");
            searchStatus.setText("正在通过 AI 生成音频和背景图...");
            searchStatus.setTextColor(Color.parseColor("#4A90D9"));

            new Thread(() -> {
                AI.MediaResult result = AI.searchMedia(MainActivity.this, name);
                mainHandler.post(() -> {
                    aiSearchBtn.setEnabled(true);
                    aiSearchBtn.setText("✨ AI生成");
                    if (result.error != null && !result.error.isEmpty()
                        && (result.audioUrl == null || result.audioUrl.isEmpty())) {
                        searchStatus.setText(result.error);
                        searchStatus.setTextColor(Color.parseColor("#E53935"));
                    } else {
                        if (result.audioUrl != null && !result.audioUrl.isEmpty()) {
                            urlInput.setText(result.audioUrl);
                            searchStatus.setText("✅ 已生成音频文件");
                            searchStatus.setTextColor(Color.parseColor("#07C160"));
                        }
                        if (result.bgImageUrl != null && !result.bgImageUrl.isEmpty()) {
                            bgImgInput.setText(result.bgImageUrl);
                        }
                        if (result.audioUrl == null && result.bgImageUrl == null) {
                            searchStatus.setText("生成失败，请手动填写");
                            searchStatus.setTextColor(Color.parseColor("#E53935"));
                        }
                    }
                });
            }).start();
        });

        // 确认逻辑
        confirm.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            String bgImg = bgImgInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(MainActivity.this, "请填写名称", Toast.LENGTH_SHORT).show();
                return;
            }
            // URL 可空，由 AI 生成补全；如都为空则提示
            if (url.isEmpty() && bgImg.isEmpty()) {
                Toast.makeText(MainActivity.this, "请先点击「AI生成」获取音频，或手动填写", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.isEmpty()
                && !url.startsWith("http://") && !url.startsWith("https://")
                && !url.startsWith("/") && !url.startsWith("file://")) {
                Toast.makeText(MainActivity.this, "音频必须是 URL 或 本地文件绝对路径", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!bgImg.isEmpty()
                && !bgImg.startsWith("http://") && !bgImg.startsWith("https://")
                && !bgImg.startsWith("/") && !bgImg.startsWith("file://")) {
                Toast.makeText(MainActivity.this, "图片必须是 URL 或 本地文件绝对路径", Toast.LENGTH_SHORT).show();
                return;
            }
            // 区分：本地文件路径 → localPath；网络 → url
            String newSoundId = "custom_" + System.currentTimeMillis();
            String urlForCustom = null;
            String localPathForCustom = null;
            String bgImgForCustom = bgImg.isEmpty() ? null : bgImg;
            if (!url.isEmpty()) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    urlForCustom = url;
                } else {
                    localPathForCustom = url;
                }
            }
            SoundStore.addCustom(MainActivity.this, newSoundId, name, urlForCustom, bgImgForCustom);
            if (localPathForCustom != null) {
                SoundStore.setLocalPath(MainActivity.this, newSoundId, localPathForCustom);
            }
            ((ViewGroup) container.getParent()).removeView(container);
            Toast.makeText(MainActivity.this, "已添加: " + name, Toast.LENGTH_SHORT).show();
            refresh();
        });

        cancel.setOnClickListener(v -> {
            ((ViewGroup) container.getParent()).removeView(container);
        });

        panel.addView(searchStatus);
        panel.addView(bgImgInput);
        panel.addView(btnRow);

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

        // 导出按钮
        if (!customs.isEmpty()) {
            Button exportBtn = new Button(this);
            exportBtn.setText("📤 导出全部到下载目录");
            exportBtn.setTextSize(14);
            exportBtn.setTextColor(Color.WHITE);
            exportBtn.setBackgroundColor(Color.parseColor("#10AEFF"));
            exportBtn.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
            LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            expLp.topMargin = dip2px(4);
            expLp.bottomMargin = dip2px(8);
            exportBtn.setLayoutParams(expLp);
            exportBtn.setOnClickListener(v -> doExportSounds());
            panel.addView(exportBtn);
        }

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

                // 下载按钮（优先显示）
                Button downloadBtn = new Button(this);
                final boolean hasLocal = s.localPath != null && !s.localPath.isEmpty()
                    && new java.io.File(s.localPath).exists();
                if (hasLocal) {
                    downloadBtn.setText("✓ 已下载");
                    downloadBtn.setTextSize(12);
                    downloadBtn.setTextColor(Color.parseColor("#888888"));
                    downloadBtn.setBackgroundColor(Color.parseColor("#444444"));
                } else {
                    downloadBtn.setText("⬇ 下载");
                    downloadBtn.setTextSize(12);
                    downloadBtn.setTextColor(Color.WHITE);
                    downloadBtn.setBackgroundColor(Color.parseColor("#34C759"));
                }
                downloadBtn.setPadding(dip2px(12), dip2px(4), dip2px(12), dip2px(4));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                dlp.rightMargin = dip2px(8);
                downloadBtn.setLayoutParams(dlp);
                downloadBtn.setOnClickListener(v -> {
                    if (hasLocal) {
                        Toast.makeText(MainActivity.this, "音频已缓存到本地", Toast.LENGTH_SHORT).show();
                    } else {
                        downloadSoundToLocal(s, container);
                    }
                });
                btns.addView(downloadBtn);

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
                Toast.makeText(this, "图片URL必须是http或https开头", Toast.LENGTH_SHORT).show();
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

    // -------- 下载音频到本地缓存 --------
    private void downloadSoundToLocal(final SoundStore.Sound sound, final ViewGroup container) {
        if (sound.url == null || sound.url.isEmpty()) {
            Toast.makeText(this, "无音频URL，无法下载", Toast.LENGTH_SHORT).show();
            return;
        }
        final String fileName = sound.id + ".mp3";
        final java.io.File cacheDir = new java.io.File(getCacheDir(), "sounds");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        final java.io.File targetFile = new java.io.File(cacheDir, fileName);

        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(sound.url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
                int code = conn.getResponseCode();
                if (code != 200) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "下载失败: HTTP " + code, Toast.LENGTH_SHORT).show());
                    return;
                }
                java.io.InputStream in = conn.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
                conn.disconnect();

                // 保存本地路径
                SoundStore.setLocalPath(MainActivity.this, sound.id, targetFile.getAbsolutePath());

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "下载完成: " + targetFile.getName(), Toast.LENGTH_SHORT).show();
                    // 关闭弹窗并刷新
                    if (container != null && container.getParent() instanceof ViewGroup) {
                        ((ViewGroup) container.getParent()).removeView(container);
                    }
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // -------- 导出自定义白噪音到下载目录 --------
    private void doExportSounds() {
        String json = SoundStore.exportAllToJson(this);
        if (json == null || json.isEmpty()) {
            Toast.makeText(this, "没有可导出的自定义白噪音", Toast.LENGTH_SHORT).show();
            return;
        }
        String fileName = "whitenoise_backup_" + System.currentTimeMillis() + ".json";
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, "已导出到下载目录: " + fileName, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "写入文件失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // -------- 导入白噪音：打开文件选择器 --------
    private void doImportSounds() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMPORT_FILE);
    }

    // -------- 处理导入：读取文件内容并解析 --------
    private void doProcessImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();
            String json = sb.toString();
            int count = SoundStore.importFromJson(this, json);
            if (count < 0) {
                Toast.makeText(this, "文件格式错误，无法导入", Toast.LENGTH_SHORT).show();
            } else if (count == 0) {
                Toast.makeText(this, "没有可导入的新白噪音（可能已存在）", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "成功导入 " + count + " 个白噪音", Toast.LENGTH_SHORT).show();
                refresh();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void doCheckUpdate() {
        UpdateChecker.check(this, new UpdateChecker.UpdateCallback() {
            @Override
            public void onResult(UpdateChecker.UpdateInfo info) {
                if (info.errorMessage != null) {
                    Toast.makeText(MainActivity.this, "检查失败: " + info.errorMessage, Toast.LENGTH_SHORT).show();
                } else if (info.isUpdateAvailable) {
                    UpdateChecker.openDownload(MainActivity.this, info.downloadUrl);
                } else {
                    Toast.makeText(MainActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // -------- onActivityResult --------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHAT) {
            refresh();
        } else if (requestCode == REQ_IMPORT_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    doProcessImport(uri);
                }
            }
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

    // 按不区分大小写的方式在 fullText 中高亮 keyword
    private static CharSequence highlightKeyword(String fullText, String keyword, int highlightColor, int defaultColor) {
        if (fullText == null) return "";
        if (keyword == null || keyword.isEmpty()) return fullText;
        String lowerFull = fullText.toLowerCase(java.util.Locale.getDefault());
        String lowerKey = keyword.toLowerCase(java.util.Locale.getDefault());
        int idx = lowerFull.indexOf(lowerKey);
        if (idx < 0) return fullText;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(fullText);
        while (idx >= 0) {
            int end = idx + keyword.length();
            if (end > fullText.length()) end = fullText.length();
            sb.setSpan(new android.text.style.ForegroundColorSpan(highlightColor),
                idx, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // 粗体突出匹配段
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                idx, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            idx = lowerFull.indexOf(lowerKey, end);
        }
        return sb;
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
