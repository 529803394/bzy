package com.example.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ✨ 猜你喜欢 — 智能推荐白噪音
 * 核心逻辑：综合分析「当前时间 / 季节 / 天气（模拟） / 地点 / 用户对话历史 / 用户输入诉求」，
 * 调用 DeepSeek 真实 LLM 进行推理与推荐，点击后进入对应白噪音聊天室。
 *
 * 网络失败时自动退回规则基线。
 */
public class RecommendActivity extends Activity {

    private LinearLayout msgContainer;
    private ScrollView msgScroller;
    private List<String> reasoningHistory; // 推理历史，让后续推荐能感知上下文

    // ------ 上下文信息（由 computeContext 计算）------
    private int hour;
    private int month;
    private String season;       // 春/夏/秋/冬
    private String timeOfDay;    // 清晨/上午/中午/下午/傍晚/深夜
    private String weather;      // 模拟天气
    private String locationHint; // 地点暗示

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#666666");
        final int cardBg = dark ? Color.parseColor("#1e1e1e") : Color.parseColor("#FAFAFA");
        final int pageBg = dark ? Color.parseColor("#121212") : Color.parseColor("#F5F0E8");
        final int primary = Color.parseColor("#07C160");

        reasoningHistory = new ArrayList<>();

        // 计算上下文
        computeContext();

        // ===== 构建 UI =====
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pageBg);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));

        // --- 顶部栏 ---
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
        title.setText("✨ 猜你喜欢");
        title.setTextSize(17);
        title.setTextColor(textMain);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        title.setLayoutParams(titleLp);
        topBar.addView(title);

        TextView spacer = new TextView(this);
        spacer.setWidth(dip2px(60));
        topBar.addView(spacer);

        root.addView(topBar);

        // --- 消息/内容滚动区 ---
        msgScroller = new ScrollView(this);
        msgScroller.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        msgScroller.setPadding(dip2px(12), dip2px(12), dip2px(12), dip2px(4));

        msgContainer = new LinearLayout(this);
        msgContainer.setOrientation(LinearLayout.VERTICAL);
        msgScroller.addView(msgContainer);
        root.addView(msgScroller);

        // --- 底部输入栏 ---
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setBackgroundColor(cardBg);
        inputBar.setPadding(dip2px(8), dip2px(8), dip2px(8), dip2px(8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputBar.setLayoutParams(ilp);

        final EditText input = new EditText(this);
        input.setHint("描述一下你现在的状态，例如：晚上失眠，想放松，在办公室...");
        input.setTextSize(14);
        input.setTextColor(textMain);
        input.setHintTextColor(textSub);
        input.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#EDEDED"));
        input.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        input.setLayoutParams(inputLp);
        inputBar.addView(input);

        Button sendBtn = new Button(this);
        sendBtn.setText("推荐");
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
            if (!text.isEmpty()) {
                addUserBubble(text);
                input.setText("");
                // 基于用户输入 + 上下文推荐
                Recommendation rec = recommendBasedOn(text);
                showRecommendation(rec, text);
            } else {
                // 空输入：只基于上下文推荐
                Recommendation rec = recommendBasedOn(null);
                showRecommendation(rec, null);
            }
        });
        inputBar.addView(sendBtn);
        root.addView(inputBar);

        setContentView(root);

        // ---- 初始展示：上下文信息卡 + 首次推荐 ----
        msgContainer.post(() -> {
            showContextCard();
            // 稍停一下，模拟"思考"
            msgContainer.postDelayed(() -> {
                Recommendation r = recommendBasedOn(null);
                showRecommendation(r, null);
            }, 400);
        });
    }

    // ========================================================
    //  上下文计算
    // ========================================================
    private void computeContext() {
        Calendar cal = Calendar.getInstance();
        hour = cal.get(Calendar.HOUR_OF_DAY);
        month = cal.get(Calendar.MONTH) + 1; // 1-12

        // 时间段
        if (hour >= 5 && hour < 8) timeOfDay = "清晨";
        else if (hour >= 8 && hour < 11) timeOfDay = "上午";
        else if (hour >= 11 && hour < 13) timeOfDay = "中午";
        else if (hour >= 13 && hour < 17) timeOfDay = "下午";
        else if (hour >= 17 && hour < 20) timeOfDay = "傍晚";
        else if (hour >= 20 && hour < 24) timeOfDay = "夜晚";
        else timeOfDay = "深夜";

        // 季节
        if (month >= 3 && month <= 5) season = "春季";
        else if (month >= 6 && month <= 8) season = "夏季";
        else if (month >= 9 && month <= 11) season = "秋季";
        else season = "冬季";

        // 模拟天气（简单伪随机，基于日期让同一天稳定）
        int day = cal.get(Calendar.DAY_OF_YEAR);
        String[] weathers = {"晴朗", "多云", "小雨", "闷热", "凉爽", "寒风"};
        weather = weathers[(day + hour) % weathers.length];

        // 地点提示（简单启发式）
        String[] locs = {"城市公寓", "郊区别墅", "高层办公室", "学生宿舍", "温馨小家"};
        locationHint = locs[day % locs.length] + "（推测）";
    }

    // ========================================================
    //  推荐引擎（调用 DeepSeek 真实 LLM）
    //  将 时间/季节/天气/环境/用户诉求 发送给 AI，拿到推荐及理由
    // ========================================================
    private static class Recommendation {
        String soundName;       // 推荐的白噪音名（需匹配 SoundStore 默认名）
        String shortReason;     // 一句话理由
        String detailedReason;  // 详细推理（多段）
    }

    private Recommendation recommendBasedOn(String userText) {
        Recommendation rec = new Recommendation();

        // 1. 构造上下文
        String timeNow = new SimpleDateFormat("EEEE HH:mm", Locale.CHINA).format(new Date());
        String envHint = locationHint;

        // 2. 收集最近一天（24小时）的所有聊天记录（所有白噪音）
        List<AI.ChatMessage> history = collectRecentChatHistory();

        // 3. 调用大模型（DeepSeek V4 Flash）
        AI.RecResult ai = AI.recommend(
                RecommendActivity.this,
                timeNow, season, weather, envHint,
                userText,
                history
        );

        // 4. 装填结果
        rec.soundName = (ai == null || ai.soundName == null || ai.soundName.isEmpty())
                ? "雨声" : ai.soundName;
        rec.shortReason = (ai == null || ai.shortReason == null || ai.shortReason.isEmpty())
                ? ("根据你当前状态推荐「" + rec.soundName + "」") : ai.shortReason;

        // 5. 拼接详细推理（含配方理由，用户能看到 AI 的完整分析）
        StringBuilder detail = new StringBuilder();
        if (ai != null && ai.detailedReason != null && !ai.detailedReason.isEmpty()) {
            detail.append("💡 ").append(ai.detailedReason);
        }
        if (ai != null && ai.recipe != null && !ai.recipe.isEmpty()) {
            detail.append("\n\n🔧 配方参考：").append(ai.recipe);
            if (ai.recipeReason != null && !ai.recipeReason.isEmpty()) {
                detail.append("\n").append(ai.recipeReason);
            }
        }
        if (ai == null || detail.length() == 0) {
            detail.append(rec.shortReason);
        }
        rec.detailedReason = detail.toString();

        if (reasoningHistory != null) {
            reasoningHistory.add(rec.soundName);
            if (reasoningHistory.size() > 5) reasoningHistory.remove(0);
        }

        return rec;
    }

    // 从所有白噪音中收集最近 24 小时的用户/助手对话
    private List<AI.ChatMessage> collectRecentChatHistory() {
        List<AI.ChatMessage> result = new ArrayList<>();
        try {
            long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            List<SoundStore.Sound> all = SoundStore.getAll(this);
            for (SoundStore.Sound s : all) {
                List<SoundStore.Message> msgs = SoundStore.loadMessages(this, s.id);
                for (SoundStore.Message m : msgs) {
                    if (m.time >= cutoff) {
                        result.add(new AI.ChatMessage("[" + s.name + "] " + m.text, m.fromUser));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    // 睡眠场景：按季节选最舒服的白噪音
    private String pickSleepBySeason() {
        if (season.equals("夏季") || weather.contains("闷热")) return "雨声";
        if (season.equals("冬季") || weather.contains("寒")) return "篝火";
        if (season.equals("春季")) return "森林";
        return "海浪"; // 秋天或默认
    }

    // 专注场景：按天气/时间选，核心原则是「有能量但不刺激」
    private String pickFocusByWeather() {
        if (weather.contains("雨") || weather.contains("云")) return "雨声";
        if (weather.contains("风") || weather.contains("寒")) return "风声";
        if (hour >= 12 && hour <= 14) return "风声"; // 午后不犯困
        return "海浪"; // 默认
    }

    // 从所有白噪音的聊天历史中提取关键词
    private List<String> collectHistoryKeywords() {
        List<String> result = new ArrayList<>();
        try {
            List<SoundStore.Sound> all = SoundStore.getAll(this);
            int[] counter = new int[SoundStore.DEFAULT_NAMES.length];
            int total = 0;
            for (SoundStore.Sound s : all) {
                List<SoundStore.Message> msgs = SoundStore.loadMessages(this, s.id);
                for (SoundStore.Message m : msgs) {
                    if (m.fromUser) {
                        total++;
                        for (int i = 0; i < SoundStore.DEFAULT_NAMES.length; i++) {
                            if (m.text.contains(SoundStore.DEFAULT_NAMES[i])) counter[i]++;
                        }
                    }
                }
            }
            // 只在有一定对话量后启用历史加权，避免噪声
            if (total >= 3) {
                for (int i = 0; i < counter.length; i++) {
                    if (counter[i] >= 1) result.add(SoundStore.DEFAULT_NAMES[i]);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static boolean matchesAny(String text, String... kws) {
        if (text == null) return false;
        for (String kw : kws) if (text.contains(kw)) return true;
        return false;
    }

    // ========================================================
    //  UI 渲染
    // ========================================================
    private void showContextCard() {
        final boolean dark = isDarkMode(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(dark ? Color.parseColor("#2a2a3a") : Color.parseColor("#FFF7ED"));
        card.setPadding(dip2px(16), dip2px(14), dip2px(16), dip2px(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dip2px(10);
        card.setLayoutParams(clp);

        // 圆角化
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? Color.parseColor("#2a2a3a") : Color.parseColor("#FFF7ED"));
        bg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            card.setBackground(bg);
        } else {
            card.setBackgroundDrawable(bg);
        }

        TextView head = new TextView(this);
        head.setText("📊 当前信息");
        head.setTextSize(15);
        head.setTextColor(dark ? Color.WHITE : Color.BLACK);
        head.getPaint().setFakeBoldText(true);
        card.addView(head);

        String[] lines = {
            "⏰ 时间：" + new SimpleDateFormat("HH:mm (EEEE)", Locale.CHINA).format(new Date()),
            "🌤 季节：" + season + "，" + weather,
            "🏠 环境：" + locationHint,
            "💬 已记录聊天：" + countTotalMessages() + " 条"
        };
        for (String l : lines) {
            TextView t = new TextView(this);
            t.setText(l);
            t.setTextSize(13);
            t.setTextColor(dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555"));
            t.setPadding(0, dip2px(4), 0, 0);
            card.addView(t);
        }

        TextView hint = new TextView(this);
        hint.setText("💡 提示：告诉我你现在在做什么 / 需要什么，我会更精准地推荐。");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#f59e0b"));
        hint.setPadding(0, dip2px(8), 0, 0);
        card.addView(hint);

        msgContainer.addView(card);
    }

    private int countTotalMessages() {
        int n = 0;
        try {
            for (SoundStore.Sound s : SoundStore.getAll(this)) {
                n += SoundStore.loadMessages(this, s.id).size();
            }
        } catch (Exception ignored) {}
        return n;
    }

    private void addUserBubble(String text) {
        final boolean dark = isDarkMode(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dip2px(6);
        row.setLayoutParams(rlp);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackgroundColor(Color.parseColor("#07C160"));
        bubble.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.75);
        bubble.setMaxWidth(maxW);

        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.parseColor("#07C160"));
        bbg.setCornerRadius(dip2px(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            bubble.setBackground(bbg);
        } else {
            bubble.setBackgroundDrawable(bbg);
        }
        row.addView(bubble);
        msgContainer.addView(row);
        msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));
    }

    private void showRecommendation(final Recommendation rec, String userText) {
        final boolean dark = isDarkMode(this);
        final int textMain = dark ? Color.WHITE : Color.BLACK;
        final int textSub = dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#666666");

        // ---- AI 气泡（模拟 LLM 回复：先思考，再给出推荐）----
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dip2px(8);
        row.setLayoutParams(rlp);

        // (1) "思考中..." 临时气泡，表示推理过程
        final TextView thinkingBubble = new TextView(this);
        thinkingBubble.setText("🤖 正在分析你的情况...");
        thinkingBubble.setTextSize(13);
        thinkingBubble.setTextColor(textSub);
        thinkingBubble.setBackgroundColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#FFFFFF"));
        thinkingBubble.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(dark ? Color.parseColor("#2a2a2a") : Color.parseColor("#FFFFFF"));
        tb.setCornerRadius(dip2px(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) thinkingBubble.setBackground(tb);
        else thinkingBubble.setBackgroundDrawable(tb);
        LinearLayout.LayoutParams tblp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        thinkingBubble.setLayoutParams(tblp);
        row.addView(thinkingBubble);
        msgContainer.addView(row);
        msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));

        // 延迟一会儿后替换为详细推理 + 结果卡片
        msgContainer.postDelayed(() -> {
            thinkingBubble.setText("🤖 " + rec.detailedReason);
            msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));

            // ---- 结果卡片 ----
            LinearLayout resultCard = new LinearLayout(this);
            resultCard.setOrientation(LinearLayout.VERTICAL);
            resultCard.setPadding(dip2px(16), dip2px(16), dip2px(16), dip2px(16));
            resultCard.setBackgroundColor(dark ? Color.parseColor("#1f2937") : Color.parseColor("#FFFFFF"));
            LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rclp.topMargin = dip2px(12);
            rclp.bottomMargin = dip2px(6);
            resultCard.setLayoutParams(rclp);

            GradientDrawable rcbg = new GradientDrawable();
            rcbg.setColor(dark ? Color.parseColor("#1f2937") : Color.parseColor("#FFFFFF"));
            rcbg.setCornerRadius(dip2px(12));
            int strokeColor = Color.parseColor("#f59e0b");
            rcbg.setStroke(dip2px(2), strokeColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) resultCard.setBackground(rcbg);
            else resultCard.setBackgroundDrawable(rcbg);

            TextView rTitle = new TextView(this);
            rTitle.setText("🎯 推荐：" + rec.soundName);
            rTitle.setTextSize(17);
            rTitle.setTextColor(Color.parseColor("#f59e0b"));
            rTitle.getPaint().setFakeBoldText(true);
            resultCard.addView(rTitle);

            TextView rShort = new TextView(this);
            rShort.setText(rec.shortReason);
            rShort.setTextSize(13);
            rShort.setTextColor(textMain);
            rShort.setPadding(0, dip2px(6), 0, dip2px(10));
            resultCard.addView(rShort);

            Button enterBtn = new Button(this);
            enterBtn.setText("▶ 立即进入「" + rec.soundName + "」聊天室");
            enterBtn.setTextSize(14);
            enterBtn.setTextColor(Color.WHITE);
            enterBtn.setBackgroundColor(Color.parseColor("#07C160"));
            enterBtn.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
            LinearLayout.LayoutParams eblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            enterBtn.setLayoutParams(eblp);
            enterBtn.setOnClickListener(v -> {
                // 根据推荐名找到内置 sound 并进入
                SoundStore.Sound target = null;
                for (SoundStore.Sound s : SoundStore.getAll(this)) {
                    if (s.name.equals(rec.soundName)) { target = s; break; }
                }
                if (target == null) {
                    Toast.makeText(this, "未找到该白噪音", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(RecommendActivity.this, ChatActivity.class);
                i.putExtra("sound_id", target.id);
                startActivity(i);
            });
            resultCard.addView(enterBtn);

            Button switchBtn = new Button(this);
            switchBtn.setText("🔄 换一个推荐");
            switchBtn.setTextSize(13);
            switchBtn.setTextColor(textMain);
            switchBtn.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sblp.topMargin = dip2px(6);
            switchBtn.setLayoutParams(sblp);
            switchBtn.setOnClickListener(v -> {
                // 加入一个"换一个"的用户气泡
                addUserBubble("换一个试试");
                // 在 history 里标记避免重复
                if (!reasoningHistory.contains(rec.soundName)) reasoningHistory.add(rec.soundName);
                Recommendation r2 = recommendBasedOn(userText);
                showRecommendation(r2, userText);
            });
            resultCard.addView(switchBtn);

            // ================= 新增：AI 生成新白噪音 =================
            Button genBtn = new Button(this);
            genBtn.setText("✨ AI 生成一个新白噪音");
            genBtn.setTextSize(13);
            genBtn.setTextColor(Color.parseColor("#f59e0b"));
            genBtn.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams gblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            gblp.topMargin = dip2px(4);
            genBtn.setLayoutParams(gblp);
            genBtn.setOnClickListener(v -> {
                addUserBubble("帮我 AI 生成一个新白噪音");

                // 思考气泡（AI 生成声音用）
                final TextView genThinking = new TextView(this);
                genThinking.setText("🤖 正在构思声音配方并合成音频...");
                genThinking.setTextSize(13);
                genThinking.setTextColor(textSub);
                genThinking.setPadding(dip2px(12), dip2px(8), dip2px(12), dip2px(8));
                GradientDrawable tbg = new GradientDrawable();
                tbg.setColor(isDarkMode(this) ? Color.parseColor("#2a2a2a") : Color.parseColor("#FFFFFF"));
                tbg.setCornerRadius(dip2px(8));
                genThinking.setBackground(tbg);
                LinearLayout thinkingRow = new LinearLayout(this);
                thinkingRow.setPadding(0, dip2px(4), 0, 0);
                thinkingRow.addView(genThinking);
                msgContainer.addView(thinkingRow);
                msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));

                new AsyncTask<Void, Void, AI.CreativeSound>() {
                    private String errorMsg = null;
                    private File generatedFile = null;
                    @Override
                    protected AI.CreativeSound doInBackground(Void... p) {
                        try {
                            String timeNow = new SimpleDateFormat("EEEE HH:mm", Locale.CHINA).format(new Date());
                            List<AI.ChatMessage> history = collectRecentChatHistory();
                            // 1. 调用大模型（DeepSeek V4 Flash）生成创意
                            AI.CreativeSound cs = AI.generateCreativeSound(
                                    RecommendActivity.this, userText, history,
                                    timeNow, season, weather, locationHint
                            );
                            if (cs == null || cs.name == null || cs.name.isEmpty()) {
                                errorMsg = "生成失败，用默认声音";
                                cs = new AI.CreativeSound();
                                cs.name = "静谧时光";
                                cs.description = "一段宁静的环境音";
                                cs.recipe = "rain:0.6,wind:0.4";
                                cs.reason = "模型未返回有效结果，已走兜底路径。";
                            }

                            // 2. 根据配方合成 WAV
                            File dir = getExternalFilesDir(null);
                            if (dir == null) dir = getFilesDir();
                            generatedFile = new File(dir, "ai_sound_" + System.currentTimeMillis() + ".wav");
                            SoundGenerator.generateWav(generatedFile, cs.recipe, 45);
                            return cs;
                        } catch (Throwable e) {
                            errorMsg = "生成失败: " + e.getMessage();
                            return null;
                        }
                    }
                    @Override
                    protected void onPostExecute(AI.CreativeSound cs) {
                        try { ViewGroup parent = (ViewGroup)thinkingRow.getParent(); if (parent != null) parent.removeView(thinkingRow); } catch (Exception ignored) {}
                        if (cs == null) {
                            Toast.makeText(RecommendActivity.this, errorMsg == null ? "生成失败" : errorMsg, Toast.LENGTH_LONG).show();
                            return;
                        }
                        // 3. 写入自定义白噪音
                        String fileUrl = "file://" + generatedFile.getAbsolutePath();
                        SoundStore.addCustom(RecommendActivity.this, cs.name, fileUrl, null);

                        // 4. 显示结果卡片（带名字/描述/配方/理由）
                        showGeneratedSoundCard(cs, fileUrl);
                    }
                }.execute();
            });
            resultCard.addView(genBtn);

            msgContainer.addView(resultCard);
            msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));
        }, 700);
    }

    // ========================================================
    //  AI 生成声音的结果卡片
    // ========================================================
    private void showGeneratedSoundCard(AI.CreativeSound cs, String fileUrl) {
        boolean dark = isDarkMode(this);
        int textMain = dark ? Color.WHITE : Color.BLACK;
        int textSub = dark ? Color.parseColor("#CCCCCC") : Color.parseColor("#666666");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dip2px(16), dip2px(16), dip2px(16), dip2px(16));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(dark ? Color.parseColor("#1f2937") : Color.parseColor("#FFFFFF"));
        cbg.setCornerRadius(dip2px(12));
        cbg.setStroke(dip2px(2), Color.parseColor("#f59e0b"));
        card.setBackground(cbg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dip2px(10);
        card.setLayoutParams(clp);

        TextView title = new TextView(this);
        title.setText("✨ AI 新声音：" + cs.name);
        title.setTextSize(17);
        title.setTextColor(Color.parseColor("#f59e0b"));
        title.getPaint().setFakeBoldText(true);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(cs.description);
        desc.setTextSize(13);
        desc.setTextColor(textMain);
        desc.setPadding(0, dip2px(6), 0, dip2px(4));
        card.addView(desc);

        TextView recipe = new TextView(this);
        recipe.setText("配方: " + cs.recipe);
        recipe.setTextSize(11);
        recipe.setTextColor(textSub);
        card.addView(recipe);

        Button enterBtn = new Button(this);
        enterBtn.setText("▶ 立即进入「" + cs.name + "」聊天室");
        enterBtn.setTextSize(14);
        enterBtn.setTextColor(Color.WHITE);
        enterBtn.setBackgroundColor(Color.parseColor("#07C160"));
        enterBtn.setPadding(dip2px(12), dip2px(10), dip2px(12), dip2px(10));
        LinearLayout.LayoutParams eblp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        eblp.topMargin = dip2px(10);
        enterBtn.setLayoutParams(eblp);
        enterBtn.setOnClickListener(v -> {
            // 根据文件名/路径找到最新添加的自定义 sound 并进入
            SoundStore.Sound target = null;
            for (SoundStore.Sound s : SoundStore.getAll(this)) {
                if (s.isCustom && fileUrl.equals(s.url)) { target = s; break; }
            }
            if (target == null) {
                Toast.makeText(this, "未找到声音", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(RecommendActivity.this, ChatActivity.class);
            i.putExtra("sound_id", target.id);
            startActivity(i);
        });
        card.addView(enterBtn);

        msgContainer.addView(card);
        msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));
    }

    // ========================================================
    //  工具函数
    // ========================================================
    private static boolean isDarkMode(Activity ctx) {
        int mode = ctx.getSharedPreferences("whitenoise_settings", MODE_PRIVATE).getInt("theme_mode", 0);
        if (mode == 1) return false;
        if (mode == 2) return true;
        int uiMode = ctx.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int dip2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
