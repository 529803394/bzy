package com.example.helloworld;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class FlowLightView extends View {
    private Paint paint, particlePaint, glowPaint;
    private float time = 0;
    private boolean running = true;
    private int sceneIndex = 0;
    private Bitmap bgBitmap;
    private float bgOffset = 0;
    private float[] particleAngles = new float[40];
    private float[] particleSpeeds = new float[40];
    private float[] particlePhases = new float[40];

    // Per-scene color palettes (sceneIndex maps to 0-4)
    private final int[][] PALETTES = {
        // Rain - deep blues and purples
        { 0xFF0D1B2A, 0xFF1B263B, 0xFF415A77, 0xFF778DA9, 0xFF1B98E0, 0xFF086375, 0xFF00B4D8, 0xFF90E0EF },
        // Ocean - teals and deep blues
        { 0xFF023E8A, 0xFF0077B6, 0xFF00B4D8, 0xFF48CAE4, 0xFF90E0EF, 0xFF03045E, 0xFF0096C7, 0xFFADE8F4 },
        // Forest - greens and earth tones
        { 0xFF1A2F1A, 0xFF2D5A27, 0xFF40916C, 0xFF52B788, 0xFF74C69D, 0xFF95D5B2, 0xFF606C38, 0xFFDDA15E },
        // Wind - silver and sky blues
        { 0xFF1A1A2E, 0xFF16213E, 0xFF0F3460, 0xFF94A3B8, 0xFFCFD2DF, 0xFFE0E1DD, 0xFF5C6B73, 0xFF9DB4C0 },
        // Campfire - warm oranges and reds
        { 0xFF1A0A00, 0xFF3D1308, 0xFF7A2000, 0xFFB84000, 0xFFE65C00, 0xFFFF8C00, 0xFFFFB347, 0xFFFFD700 }
    };

    public FlowLightView(Context context) {
        super(context);
        init();
    }

    public FlowLightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < particleAngles.length; i++) {
            particleAngles[i] = (float) (Math.random() * 360);
            particleSpeeds[i] = 0.2f + (float) (Math.random() * 0.8f);
            particlePhases[i] = (float) (Math.random() * Math.PI * 2);
        }
    }

    public void setScene(int index) {
        this.sceneIndex = index % PALETTES.length;
        time = 0;
        bgOffset = 0;
    }

    public void start() {
        running = true;
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int[] colors = PALETTES[sceneIndex];

        // 1. Draw dark base
        paint.setColor(colors[0]);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, paint);

        // 2. Horizontal flowing waves (left to right)
        drawHorizontalWaves(canvas, w, h, colors, time);

        // 3. Vertical flowing waves (top to bottom)
        drawVerticalWaves(canvas, w, h, colors, time);

        // 4. Diagonal waves (top-left to bottom-right)
        drawDiagonalWaves1(canvas, w, h, colors, time);

        // 5. Diagonal waves (top-right to bottom-left)
        drawDiagonalWaves2(canvas, w, h, colors, time);

        // 6. Floating particles with trails
        drawParticles(canvas, w, h, colors);

        // 7. Light streaks
        drawLightStreaks(canvas, w, h, colors);

        time += 0.015f;
        bgOffset += 0.5f;
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawHorizontalWaves(Canvas canvas, int w, int h, int[] colors, float t) {
        int bands = 5;
        float bandH = h / (float) bands;
        for (int i = 0; i < bands; i++) {
            Path path = new Path();
            path.moveTo(0, h);

            for (int x = 0; x <= w; x += 15) {
                float wave = (float) Math.sin((x * 0.006f) + t * 1.2f + i * 1.3f) * bandH * 0.35f;
                float wave2 = (float) Math.sin((x * 0.012f) + t * 0.8f + i * 0.7f) * bandH * 0.2f;
                float y = i * bandH + bandH * 0.5f + wave + wave2;
                if (x == 0) path.moveTo(0, y);
                else path.lineTo(x, y);
            }

            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();

            int ci = (i * 2 + (int)(t * 8)) % colors.length;
            int cj = (ci + 1) % colors.length;
            LinearGradient lg = new LinearGradient(0, i * bandH, w, i * bandH + bandH, colors[ci], colors[cj], Shader.TileMode.CLAMP);
            paint.setShader(lg);
            paint.setAlpha(60);
            canvas.drawPath(path, paint);
        }
        paint.setShader(null);
    }

    private void drawVerticalWaves(Canvas canvas, int w, int h, int[] colors, float t) {
        int bands = 4;
        float bandW = w / (float) bands;
        for (int i = 0; i < bands; i++) {
            Path path = new Path();
            path.moveTo(w, 0);

            for (int y = 0; y <= h; y += 15) {
                float wave = (float) Math.sin((y * 0.006f) + t * 1.0f + i * 1.1f) * bandW * 0.35f;
                float wave2 = (float) Math.sin((y * 0.01f) + t * 0.6f + i * 0.9f) * bandW * 0.2f;
                float x = i * bandW + bandW * 0.5f + wave + wave2;
                if (y == 0) path.moveTo(x, 0);
                else path.lineTo(x, y);
            }

            path.lineTo(w, h);
            path.lineTo(w, 0);
            path.close();

            int ci = (i * 2 + (int)(t * 6) + 2) % colors.length;
            int cj = (ci + 1) % colors.length;
            LinearGradient lg = new LinearGradient(i * bandW, 0, i * bandW + bandW, h, colors[ci], colors[cj], Shader.TileMode.CLAMP);
            paint.setShader(lg);
            paint.setAlpha(50);
            canvas.drawPath(path, paint);
        }
        paint.setShader(null);
    }

    private void drawDiagonalWaves1(Canvas canvas, int w, int h, int[] colors, float t) {
        int bands = 3;
        float diagLen = (float) Math.sqrt(w * w + h * h);
        float bandLen = diagLen / bands;
        for (int i = 0; i < bands; i++) {
            Path path = new Path();
            float offset = (t * 30 + i * bandLen) % diagLen;

            // Draw diagonal band as a series of points
            for (int j = 0; j <= 20; j++) {
                float ratio = (j / 20f);
                float x = ratio * w;
                float y = ratio * h;
                float wave = (float) Math.sin(ratio * Math.PI * 4 + t * 1.5f + i) * 30;
                float nx = x - wave * 0.5f;
                float ny = y + wave * 0.3f;
                if (j == 0) path.moveTo(nx, ny);
                else path.lineTo(nx, ny);
            }
            for (int j = 20; j >= 0; j--) {
                float ratio = (j / 20f);
                float x = ratio * w;
                float y = ratio * h;
                float wave = (float) Math.sin(ratio * Math.PI * 4 + t * 1.5f + i) * 30;
                float nx = x + wave * 0.5f + 20;
                float ny = y + wave * 0.3f + 20;
                path.lineTo(nx, ny);
            }
            path.close();

            int ci = (i + (int)(t * 4) + 1) % colors.length;
            int cj = (ci + 2) % colors.length;
            paint.setColor(colors[ci]);
            paint.setAlpha(40);
            canvas.drawPath(path, paint);
        }
    }

    private void drawDiagonalWaves2(Canvas canvas, int w, int h, int[] colors, float t) {
        int bands = 3;
        for (int i = 0; i < bands; i++) {
            Path path = new Path();
            for (int j = 0; j <= 20; j++) {
                float ratio = (j / 20f);
                float x = ratio * w;
                float y = (1 - ratio) * h;
                float wave = (float) Math.sin(ratio * Math.PI * 4 + t * 1.2f + i + 2) * 25;
                float nx = x + wave * 0.4f;
                float ny = y - wave * 0.5f;
                if (j == 0) path.moveTo(nx, ny);
                else path.lineTo(nx, ny);
            }
            for (int j = 20; j >= 0; j--) {
                float ratio = (j / 20f);
                float x = ratio * w;
                float y = (1 - ratio) * h;
                float wave = (float) Math.sin(ratio * Math.PI * 4 + t * 1.2f + i + 2) * 25;
                float nx = x + wave * 0.4f + 15;
                float ny = y - wave * 0.5f + 15;
                path.lineTo(nx, ny);
            }
            path.close();

            int ci = (i + (int)(t * 3) + 3) % colors.length;
            int cj = (ci + 1) % colors.length;
            paint.setColor(colors[ci]);
            paint.setAlpha(35);
            canvas.drawPath(path, paint);
        }
    }

    private void drawParticles(Canvas canvas, int w, int h, int[] colors) {
        for (int i = 0; i < particleAngles.length; i++) {
            float angle = particleAngles[i] + time * particleSpeeds[i] * 30;
            float rad = (float) Math.toRadians(angle);
            float cx = ((float) Math.sin(rad * 0.7f + particlePhases[i]) * 0.5f + 0.5f) * w;
            float cy = ((float) Math.cos(rad * 0.5f + particlePhases[i]) * 0.5f + 0.5f) * h;

            float pulse = (float) (Math.sin(time * 3 + i * 0.5f) * 0.3f + 1.0f);
            float radius = (1.5f + i % 3) * pulse;

            int ci = i % colors.length;
            particlePaint.setColor(colors[ci]);
            particlePaint.setAlpha(100 + (int)(pulse * 80));

            // Glow effect
            glowPaint.setColor(colors[ci]);
            glowPaint.setAlpha(30);
            canvas.drawCircle(cx, cy, radius * 3, glowPaint);

            canvas.drawCircle(cx, cy, radius, particlePaint);
        }
    }

    private void drawLightStreaks(Canvas canvas, int w, int h, int[] colors) {
        // Horizontal streaks
        for (int i = 0; i < 6; i++) {
            float y = ((time * 20 + i * h / 6f) % h);
            float streakH = 2 + (float) Math.sin(time + i) * 1.5f;
            int ci = (i + (int)(time * 2)) % colors.length;

            LinearGradient lg = new LinearGradient(0, y, w, y, colors[ci], 0x00000000, Shader.TileMode.CLAMP);
            glowPaint.setShader(lg);
            glowPaint.setAlpha(25);
            canvas.drawRect(0, y - streakH / 2, w, y + streakH / 2, glowPaint);
        }

        // Vertical streaks
        for (int i = 0; i < 4; i++) {
            float x = ((-time * 15 + i * w / 4f + w) % w);
            float streakW = 2 + (float) Math.sin(time * 1.3f + i) * 1.5f;
            int ci = (i + (int)(time * 1.5f) + 2) % colors.length;

            LinearGradient lg = new LinearGradient(x, 0, x, h, colors[ci], 0x00000000, Shader.TileMode.CLAMP);
            glowPaint.setShader(lg);
            glowPaint.setAlpha(20);
            canvas.drawRect(x - streakW / 2, 0, x + streakW / 2, h, glowPaint);
        }
        glowPaint.setShader(null);
    }
}
