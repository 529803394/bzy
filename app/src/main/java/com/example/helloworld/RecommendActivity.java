package com.example.helloworld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 猜你喜欢 - 智能推荐白噪音
 * 核心逻辑：综合分析「当前时间 / 季节 / 天气（模拟） / 地点 / 用户对话历史 / 用户输入诉求」，
 * 输出推理过程 + 推荐结果，点击后可直接进入对应白噪音的聊天室。
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
    //  推荐引擎（规则 + 关键词 + 上下文）
    //  模拟 LLM 的推理过程：先列出观察 -> 分析诉求 -> 给出推荐及理由
    // ========================================================
    private static class Recommendation {
        String soundName;       // 推荐的白噪音名（需匹配 SoundStore 默认名）
        String shortReason;     // 一句话理由
        String detailedReason;  // 详细推理（多段）
    }

    private Recommendation recommendBasedOn(String userText) {
        Recommendation rec = new Recommendation();
        StringBuilder detail = new StringBuilder();

        // -------- 观察阶段（模拟 LLM 先观察上下文）--------
        detail.append("【观察到的上下文】\n");
        detail.append("• 当前时间：").append(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()))
            .append("（").append(timeOfDay).append("）\n");
        detail.append("• 季节：").append(season).append("\n");
        detail.append("• 天气：").append(weather).append("\n");
        detail.append("• 环境：").append(locationHint).append("\n");

        // 收集对话历史中的主题关键词
        List<String> historyKeywords = collectHistoryKeywords();
        if (!historyKeywords.isEmpty()) {
            detail.append("• 最近常聊的关键词：");
            for (int i = 0; i < historyKeywords.size(); i++) {
                if (i > 0) detail.append("、");
                detail.append(historyKeywords.get(i));
            }
            detail.append("\n");
        }
        if (reasoningHistory != null && !reasoningHistory.isEmpty()) {
            detail.append("• 本次会话中之前的推荐参考：");
            for (int i = 0; i < reasoningHistory.size(); i++) {
                if (i > 0) detail.append("、");
                detail.append(reasoningHistory.get(i));
            }
            detail.append("\n");
        }
        if (userText != null && !userText.isEmpty()) {
            detail.append("• 用户明确诉求：").append(userText).append("\n");
        }

        detail.append("\n【推理分析】\n");

        // -------- 用户显式诉求优先 --------
        String textLow = userText == null ? "" : userText.toLowerCase();
        String text = userText == null ? "" : userText;

        if (matchesAny(text, "雨", "rain", "下雨", "雨声")) {
            rec.soundName = "雨声";
            rec.shortReason = "你提到了雨，直接推荐『雨声』";
            detail.append("→ 用户明确提到了「雨」/「下雨」/「雨声」，这是最强信号，优先匹配。\n");
            detail.append("→ 雨声的宽频特性最适合屏蔽环境噪声、助眠。\n");
        } else if (matchesAny(text, "海", "海浪", "海边", "ocean", "wave")) {
            rec.soundName = "海浪";
            rec.shortReason = "你提到了海，推荐『海浪』";
            detail.append("→ 用户明确提到了海洋相关词汇，直接匹配海浪声。\n");
            detail.append("→ 海浪低频节奏与人的放松状态共振，有助于身心平静。\n");
        } else if (matchesAny(text, "森林", "树林", "树", "forest")) {
            rec.soundName = "森林";
            rec.shortReason = "自然系，推荐『森林』";
            detail.append("→ 用户想要自然环境，森林的鸟鸣 + 树叶沙沙是最佳选择。\n");
        } else if (matchesAny(text, "风", "wind")) {
            rec.soundName = "风声";
            rec.shortReason = "想要风声，推荐『风声』";
            detail.append("→ 用户提到了风。风声能制造包裹感，适合需要专注或想睡的情况。\n");
        } else if (matchesAny(text, "火", "篝火", "温暖", "暖", "cold")) {
            rec.soundName = "篝火";
            rec.shortReason = "想要温暖感，推荐『篝火』";
            detail.append("→ 用户提到火 / 温暖 / 冷。篝火的噼啪声带来安全、温暖的心理暗示。\n");
        } else if (matchesAny(text, "睡", "眠", "失眠", "助眠", "晚上", "熬夜", "睡不着")) {
            // 睡眠场景 → 根据季节/天气进一步判断
            rec.soundName = pickSleepBySeason();
            rec.shortReason = "睡眠场景：推荐『" + rec.soundName + "』";
            detail.append("→ 用户表达了睡眠/失眠诉求。\n");
            detail.append("→ 当前是").append(season).append("的").append(timeOfDay).append("，").append(weather).append("，").append(rec.soundName).append("最契合。\n");
            detail.append("→ 白噪音循环播放可屏蔽随机噪声，稳定情绪。\n");
        } else if (matchesAny(text, "工作", "学习", "专注", "专注", "focus", "study", "office", "办公室", "写代码", "做题")) {
            rec.soundName = pickFocusByWeather();
            rec.shortReason = "专注场景：推荐『" + rec.soundName + "』";
            detail.append("→ 用户需要专注/学习环境。\n");
            detail.append("→ 结合天气（").append(weather).append("）和时间（").append(timeOfDay).append("），").append(rec.soundName).append("可屏蔽干扰但不过度催眠。\n");
        } else if (matchesAny(text, "放松", "休息", "relax", "压力", "累", "焦虑")) {
            rec.soundName = "森林";
            rec.shortReason = "放松场景：推荐『森林』";
            detail.append("→ 用户想要放松。森林环境音经研究可降低皮质醇水平。\n");
            detail.append("→ 推荐闭上眼睛，放低音量，感受 10~15 分钟。\n");
        } else if (matchesAny(text, "热", "闷热", "cool", "夏天", "summer")) {
            rec.soundName = "雨声";
            rec.shortReason = "降温心理暗示：推荐『雨声』";
            detail.append("→ 用户提到闷热，雨声会带来心理上的降温感。\n");
        } else if (matchesAny(text, "冷", "cold", "冬天", "冬")) {
            rec.soundName = "篝火";
            rec.shortReason = "温暖心理暗示：推荐『篝火』";
            detail.append("→ 冷环境下，篝火声会带来温暖和陪伴感。\n");
        } else {
            // -------- 无明确诉求 → 完全基于上下文 + 历史 --------
            detail.append("→ 用户没有明确的关键词，综合时间/季节/天气/历史对话进行推荐。\n");

            // 深夜/夜晚 → 助眠
            if (hour >= 22 || hour < 5) {
                rec.soundName = pickSleepBySeason();
                rec.shortReason = timeOfDay + "，适合助眠：推荐『" + rec.soundName + "』";
                detail.append("→ 现在是").append(timeOfDay).append("，最适合放一首循环的助眠音。\n");
                detail.append("→ 考虑到").append(season).append("，").append(rec.soundName).append("的温度感最合适。\n");
            }
            // 清晨/上午 → 清新专注
            else if (hour >= 5 && hour < 11) {
                if (weather.contains("雨") || weather.contains("云")) {
                    rec.soundName = "雨声";
                    rec.shortReason = "阴雨天工作/学习，雨声最搭";
                    detail.append("→ 阴雨天的上午：雨声营造柔和专注氛围，帮助进入工作状态。\n");
                } else {
                    rec.soundName = "森林";
                    rec.shortReason = season + "上午，森林鸟鸣最提神";
                    detail.append("→ 晴朗的").append(season).append("上午：鸟鸣+树叶沙沙最提神。\n");
                }
            }
            // 中午/下午 → 专注/平静
            else if (hour >= 11 && hour < 17) {
                if (weather.contains("闷热") || month >= 6 && month <= 8) {
                    rec.soundName = "风声";
                    rec.shortReason = season + timeOfDay + "易犯困，风声让你保持清醒但不兴奋";
                    detail.append("→ 午后容易犯困，风声既安静又不会让你睡着。\n");
                } else if (weather.contains("风") || weather.contains("寒")) {
                    rec.soundName = "篝火";
                    rec.shortReason = weather + "天气，推荐篝火声获得温暖感";
                    detail.append("→ ").append(weather).append("，篝火声会带来心理温暖。\n");
                } else {
                    rec.soundName = pickFocusByWeather();
                    rec.shortReason = "工作/学习时段：推荐『" + rec.soundName + "』";
                    detail.append("→ 午后时段需要保持专注。\n");
                }
            }
            // 傍晚 → 放松
            else {
                if (weather.contains("雨")) {
                    rec.soundName = "雨声";
                    rec.shortReason = "傍晚 + 雨，经典搭配";
                    detail.append("→ 傍晚雨天：最适合开一盏灯、放雨声放松。\n");
                } else {
                    rec.soundName = "海浪";
                    rec.shortReason = season + "傍晚，海浪让你慢慢减速";
                    detail.append("→ 傍晚适合从一天的紧张中逐渐恢复，海浪的节奏最自然。\n");
                }
            }

            // 如果历史对话集中在某个关键词上，且刚刚没推荐过，二次加权
            if (!historyKeywords.isEmpty()) {
                // 若用户历史常聊「雨声」/「海浪」等，再以该关键词加权
                for (String kw : historyKeywords) {
                    if (kw.contains("雨") && !rec.soundName.equals("雨声") && reasoningHistory.size() < 1) {
                        rec.soundName = "雨声";
                        detail.append("→ 检测到你最近的聊天关键词『雨』较多，加权后改为推荐雨声。\n");
                        rec.shortReason = "结合你的聊天关键词，最终推荐『雨声』";
                    }
                    if (kw.contains("海") && !rec.soundName.equals("海浪") && reasoningHistory.size() < 1) {
                        rec.soundName = "海浪";
                        detail.append("→ 检测到你最近常『海』相关聊天关键词，加权后改为海浪。\n");
                        rec.shortReason = "结合你的聊天关键词，最终推荐『海浪』";
                    }
                }
            }
        }

        // 避免连续推荐同一个
        if (reasoningHistory != null && reasoningHistory.contains(rec.soundName) && reasoningHistory.size() >= 1) {
            // 换一个（从默认列表挑不同）
            String[] names = SoundStore.DEFAULT_NAMES;
            for (String n : names) {
                if (!n.equals(rec.soundName)) {
                    rec.soundName = n;
                    detail.append("→ 刚刚已经推荐过或聊过，换一首试试：").append(n).append("。\n");
                    break;
                }
            }
        }

        detail.append("\n【最终推荐】").append(rec.soundName).append("\n");
        detail.append(rec.shortReason).append("。");

        rec.detailedReason = detail.toString();
        // 记录本次推荐名
        if (reasoningHistory != null) {
            reasoningHistory.add(rec.soundName);
            if (reasoningHistory.size() > 5) reasoningHistory.remove(0);
        }

        return rec;
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

            msgContainer.addView(resultCard);
            msgScroller.post(() -> msgScroller.fullScroll(View.FOCUS_DOWN));
        }, 700);
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
