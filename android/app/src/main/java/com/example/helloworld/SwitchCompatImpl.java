package com.example.helloworld;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

// 简单的开关控件：绿色=开，灰色=关
public class SwitchCompatImpl {
    private final Context ctx;
    private final LinearLayout track;
    private final TextView thumb;
    private boolean isOn;
    private Runnable onChange;

    public SwitchCompatImpl(Context context) {
        this.ctx = context;
        this.isOn = false;

        track = new LinearLayout(ctx);
        track.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
            dip2px(48), dip2px(28));
        track.setLayoutParams(tlp);
        track.setGravity(Gravity.CENTER_VERTICAL);

        thumb = new TextView(ctx);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            dip2px(24), dip2px(24));
        hlp.leftMargin = dip2px(2);
        thumb.setLayoutParams(hlp);
        thumb.setGravity(Gravity.CENTER);
        track.addView(thumb);

        track.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setChecked(!isOn);
                if (onChange != null) onChange.run();
            }
        });

        updateUI();
    }

    public void setChecked(boolean on) {
        isOn = on;
        updateUI();
    }

    public boolean isChecked() {
        return isOn;
    }

    public void setOnCheckedChanged(Runnable cb) {
        this.onChange = cb;
    }

    public View getView() {
        return track;
    }

    private void updateUI() {
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dip2px(14));
        trackBg.setColor(isOn ? Color.parseColor("#07C160") : Color.parseColor("#CCCCCC"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            track.setBackground(trackBg);
        } else {
            track.setBackgroundDrawable(trackBg);
        }

        // 滑动按钮位置
        LinearLayout.LayoutParams hlp = (LinearLayout.LayoutParams) thumb.getLayoutParams();
        hlp.leftMargin = isOn ? dip2px(22) : dip2px(2);
        thumb.setLayoutParams(hlp);

        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.WHITE);
        thumbBg.setCornerRadius(dip2px(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            thumb.setBackground(thumbBg);
        } else {
            thumb.setBackgroundDrawable(thumbBg);
        }
    }

    private int dip2px(float dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
