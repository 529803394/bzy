package com.example.helloworld;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * 程序化白噪音合成器：根据配方生成 WAV 音频文件
 *
 * 支持的组件（recipe 用逗号分隔，如 "rain:0.7,wind:0.3"）：
 *   rain       - 雨声（高通噪声 + 随机滴点）
 *   wind       - 风声（慢变调制的低通噪声）
 *   ocean      - 海浪（周期性起伏的噪声）
 *   white      - 纯白噪音
 *   pink       - 粉噪音（柔和）
 *   brown      - 棕噪音（低沉）
 *   forest     - 森林（风声 + 偶尔鸟鸣）
 *   fire       - 壁炉（爆裂声 + 低沉噪声）
 *   rain_thunder - 雷雨（雨声 + 偶尔雷鸣）
 *   cafe       - 咖啡馆（低沉环境声 + 偶尔杯具声）
 *   train      - 火车（节奏性低频 + 轨道咔嗒）
 *   heartbeat  - 心跳（规律的低频脉冲）
 *   bell       - 钟声（正弦波衰减）
 *
 * 使用：
 *   String path = SoundGenerator.generateWav(context, "rain:0.7,wind:0.3", 30);
 *   // path 为本地文件绝对路径，可用 "file://" + path 给 MediaPlayer 播放
 */
public class SoundGenerator {

    private static final int SAMPLE_RATE = 22050;
    private static final int BITS = 16;
    private static final int CHANNELS = 1;

    public static String generateWav(File outFile, String recipe, int seconds) throws IOException {
        int totalSamples = SAMPLE_RATE * seconds;
        short[] samples = new short[totalSamples];

        // 解析配方："rain:0.7,wind:0.3" -> 多个带权重的组件
        String[] parts = (recipe == null || recipe.isEmpty()) ? new String[]{"rain:1.0"} : recipe.split(",");
        double totalWeight = 0;
        String[] compNames = new String[parts.length];
        double[] weights = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            double w = 1.0;
            String name = p;
            int colon = p.indexOf(':');
            if (colon > 0) {
                try { w = Double.parseDouble(p.substring(colon + 1)); } catch (Exception ignored) {}
                name = p.substring(0, colon);
            }
            compNames[i] = name.toLowerCase();
            weights[i] = Math.max(0.05, w);
            totalWeight += weights[i];
        }
        if (totalWeight <= 0) totalWeight = 1.0;
        for (int i = 0; i < weights.length; i++) weights[i] /= totalWeight;

        // 逐个组件生成并混合
        Random rand = new Random();
        for (int c = 0; c < compNames.length; c++) {
            double w = weights[c];
            if (w <= 0) continue;
            short[] cs = generateComponent(compNames[c], totalSamples, rand);
            for (int i = 0; i < totalSamples; i++) {
                int v = samples[i] + (int)(cs[i] * w);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                samples[i] = (short)v;
            }
        }

        // 归一化（避免削波）
        int peak = 1;
        for (int s : samples) { int a = Math.abs(s); if (a > peak) peak = a; }
        double scale = 28000.0 / peak;
        if (scale > 1.0) scale = 1.0;
        for (int i = 0; i < totalSamples; i++) {
            samples[i] = (short)(samples[i] * scale);
        }

        // 写入 WAV
        writeWav(outFile, samples);
        return outFile.getAbsolutePath();
    }

    // ========================================================
    //  各个声音组件
    // ========================================================
    private static short[] generateComponent(String name, int n, Random rand) {
        short[] out = new short[n];
        if (name.equals("rain")) return genRain(n, rand);
        if (name.equals("wind")) return genWind(n, rand);
        if (name.equals("ocean")) return genOcean(n, rand);
        if (name.equals("white")) return genWhite(n, rand);
        if (name.equals("pink")) return genPink(n, rand);
        if (name.equals("brown")) return genBrown(n, rand);
        if (name.equals("forest")) return genForest(n, rand);
        if (name.equals("fire")) return genFire(n, rand);
        if (name.equals("rain_thunder") || name.equals("thunder")) return genThunder(n, rand);
        if (name.equals("cafe")) return genCafe(n, rand);
        if (name.equals("train")) return genTrain(n, rand);
        if (name.equals("heartbeat")) return genHeartbeat(n, rand);
        if (name.equals("bell")) return genBell(n, rand);
        // 默认：雨声
        return genRain(n, rand);
    }

    private static short[] genWhite(int n, Random rand) {
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short)(rand.nextGaussian() * 8000);
        return out;
    }

    private static short[] genPink(int n, Random rand) {
        short[] out = new short[n];
        double b0=0,b1=0,b2=0,b3=0,b4=0,b5=0,b6=0;
        for (int i = 0; i < n; i++) {
            double white = rand.nextGaussian();
            b0 = 0.99886 * b0 + white * 0.0555179;
            b1 = 0.99332 * b1 + white * 0.0750759;
            b2 = 0.96900 * b2 + white * 0.1538520;
            b3 = 0.86650 * b3 + white * 0.3104856;
            b4 = 0.55000 * b4 + white * 0.5329522;
            b5 = -0.7616 * b5 - white * 0.0168980;
            double pink = b0+b1+b2+b3+b4+b5+b6 + white*0.5362;
            b6 = white * 0.115926;
            out[i] = (short)(pink * 4000);
        }
        return out;
    }

    private static short[] genBrown(int n, Random rand) {
        short[] out = new short[n];
        double last = 0;
        for (int i = 0; i < n; i++) {
            double w = rand.nextGaussian();
            last = 0.99 * last + 0.01 * w;
            out[i] = (short)(last * 25000);
        }
        return out;
    }

    // 雨声：粉噪音 + 高通 + 偶尔随机滴点
    private static short[] genRain(int n, Random rand) {
        short[] pink = genPink(n, rand);
        short[] out = new short[n];
        // 简单高通：y[i] = 0.9*x[i] - 0.9*x[i-1] + 0.8*y[i-1]
        double yPrev = 0, xPrev = 0;
        for (int i = 0; i < n; i++) {
            double x = pink[i];
            double y = 0.9 * x - 0.9 * xPrev + 0.8 * yPrev;
            xPrev = x; yPrev = y;
            // 随机滴点（小爆裂）
            int drip = 0;
            if (rand.nextDouble() < 0.001) drip = (short)(rand.nextGaussian() * 12000);
            out[i] = (short)Math.max(-32768, Math.min(32767, y * 0.9 + drip));
        }
        return out;
    }

    // 风声：棕噪音 + 慢变振幅
    private static short[] genWind(int n, Random rand) {
        short[] brown = genBrown(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double env = 0.5 + 0.5 * Math.sin(i * 0.0003) + 0.2 * Math.sin(i * 0.0017);
            env = Math.max(0.1, env);
            out[i] = (short)(brown[i] * env);
        }
        return out;
    }

    // 海浪：周期起伏的棕噪音
    private static short[] genOcean(int n, Random rand) {
        short[] brown = genBrown(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            // 波浪周期约 6 秒 (SAMPLE_RATE*6 = 132300)
            double t = (i % 132300) / 132300.0;
            double env = 0.3 + 0.7 * Math.pow(Math.sin(t * Math.PI), 2);
            out[i] = (short)(brown[i] * env);
        }
        return out;
    }

    // 森林：轻风声 + 偶尔鸟鸣
    private static short[] genForest(int n, Random rand) {
        short[] wind = genWind(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short)(wind[i] * 0.7);
        // 加入鸟鸣（每 4-8 秒一个短促 chirp）
        int next = SAMPLE_RATE * (4 + rand.nextInt(5));
        int chirpLen = 0; int chirpStart = 0; double chirpFreq = 800;
        for (int i = 0; i < n; i++) {
            if (chirpLen > 0) {
                double t = (i - chirpStart) / (double)SAMPLE_RATE;
                double freq = chirpFreq + 400 * Math.sin(t * 15);
                double env = Math.sin(Math.PI * (i - chirpStart) / chirpLen);
                int chirp = (short)(Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE) * 6000 * env);
                int v = out[i] + chirp;
                out[i] = (short)Math.max(-32768, Math.min(32767, v));
                chirpLen--;
            } else if (i > next) {
                chirpStart = i;
                chirpLen = SAMPLE_RATE / 3 + rand.nextInt(SAMPLE_RATE / 3);
                chirpFreq = 700 + rand.nextInt(800);
                next = i + SAMPLE_RATE * (4 + rand.nextInt(6));
            }
        }
        return out;
    }

    // 壁炉：棕噪音 + 爆裂噼啪
    private static short[] genFire(int n, Random rand) {
        short[] brown = genBrown(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            int crack = 0;
            if (rand.nextDouble() < 0.002) crack = (short)(rand.nextGaussian() * 9000);
            out[i] = (short)(brown[i] * 0.7 + crack);
        }
        return out;
    }

    // 雷雨：雨声 + 偶尔低频雷鸣
    private static short[] genThunder(int n, Random rand) {
        short[] rain = genRain(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = rain[i];
        int nextThunder = SAMPLE_RATE * (10 + rand.nextInt(20));
        int thLen = 0; int thStart = 0;
        for (int i = 0; i < n; i++) {
            if (thLen > 0) {
                double t = (i - thStart) / (double)SAMPLE_RATE;
                double env = Math.exp(-t * 0.7) * (1 - Math.exp(-t * 5));
                // 低频正弦 + 随机噪声
                double boom = Math.sin(2 * Math.PI * 40 * i / SAMPLE_RATE) * 12000
                            + rand.nextGaussian() * 4000;
                int v = out[i] + (short)(boom * env);
                out[i] = (short)Math.max(-32768, Math.min(32767, v));
                thLen--;
            } else if (i > nextThunder) {
                thStart = i;
                thLen = SAMPLE_RATE * 2;
                nextThunder = i + SAMPLE_RATE * (15 + rand.nextInt(20));
            }
        }
        return out;
    }

    // 咖啡馆：低音量棕噪音 + 偶尔瓷器碰撞
    private static short[] genCafe(int n, Random rand) {
        short[] brown = genBrown(n, rand);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short)(brown[i] * 0.4);
        // 偶尔叮一声
        int next = SAMPLE_RATE * (3 + rand.nextInt(8));
        int ringLen = 0; int ringStart = 0; double ringFreq = 0;
        for (int i = 0; i < n; i++) {
            if (ringLen > 0) {
                double t = (i - ringStart) / (double)SAMPLE_RATE;
                double env = Math.exp(-t * 8);
                int ring = (short)(Math.sin(2 * Math.PI * ringFreq * i / SAMPLE_RATE) * 6000 * env);
                int v = out[i] + ring;
                out[i] = (short)Math.max(-32768, Math.min(32767, v));
                ringLen--;
            } else if (i > next) {
                ringStart = i;
                ringLen = SAMPLE_RATE / 2;
                ringFreq = 1200 + rand.nextInt(2400);
                next = i + SAMPLE_RATE * (4 + rand.nextInt(10));
            }
        }
        return out;
    }

    // 火车：节奏性低频 + 轨道咔嗒
    private static short[] genTrain(int n, Random rand) {
        short[] brown = genBrown(n, rand);
        short[] out = new short[n];
        // 火车节奏：每秒一个咔嗒（SAMPLE_RATE samples）
        for (int i = 0; i < n; i++) {
            double beatPos = (i % (SAMPLE_RATE / 2)) / (double)(SAMPLE_RATE / 2);
            double beat = Math.exp(-beatPos * 4);
            double lowRumble = Math.sin(2 * Math.PI * 55 * i / SAMPLE_RATE) * 4000;
            int click = (short)(rand.nextGaussian() * 8000 * beat);
            out[i] = (short)(brown[i] * 0.3 + lowRumble + click);
        }
        return out;
    }

    // 心跳：规律低频脉冲
    private static short[] genHeartbeat(int n, Random rand) {
        short[] out = new short[n];
        int beatPeriod = SAMPLE_RATE / 2; // 约 120 bpm
        for (int i = 0; i < n; i++) {
            double bp = (i % beatPeriod) / (double)beatPeriod;
            double lub = Math.exp(-bp * 10) * (bp < 0.15 ? 1.0 : 0);
            double dub = Math.exp(-(bp - 0.3) * 10) * (bp > 0.25 && bp < 0.4 ? 1.0 : 0);
            double freq = 80 + 30 * Math.sin(bp * Math.PI * 2);
            double tone = Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE);
            out[i] = (short)(tone * 12000 * (lub * 0.8 + dub * 0.6) + rand.nextGaussian() * 200);
        }
        return out;
    }

    // 钟声：多频率正弦衰减
    private static short[] genBell(int n, Random rand) {
        short[] out = new short[n];
        int bellPeriod = SAMPLE_RATE * 6;
        double[] freqs = {880, 1320, 1760, 2640};
        for (int i = 0; i < n; i++) {
            double bp = (i % bellPeriod) / (double)bellPeriod;
            double env = Math.exp(-bp * 2.5);
            double val = 0;
            for (double f : freqs) val += Math.sin(2 * Math.PI * f * i / SAMPLE_RATE);
            out[i] = (short)(val / freqs.length * 15000 * env);
        }
        return out;
    }

    // ========================================================
    //  WAV 文件写入
    // ========================================================
    private static void writeWav(File file, short[] samples) throws IOException {
        int byteRate = SAMPLE_RATE * CHANNELS * BITS / 8;
        int blockAlign = CHANNELS * BITS / 8;
        int dataSize = samples.length * CHANNELS * BITS / 8;
        int totalSize = 36 + dataSize;

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        writeString(bos, "RIFF");
        writeInt(bos, totalSize);
        writeString(bos, "WAVE");
        writeString(bos, "fmt ");
        writeInt(bos, 16);          // fmt chunk size
        writeShort(bos, 1);          // PCM
        writeShort(bos, CHANNELS);
        writeInt(bos, SAMPLE_RATE);
        writeInt(bos, byteRate);
        writeShort(bos, blockAlign);
        writeShort(bos, BITS);
        writeString(bos, "data");
        writeInt(bos, dataSize);
        for (short s : samples) writeShort(bos, s);
        bos.flush();
        bos.close();
    }

    private static void writeString(BufferedOutputStream bos, String s) throws IOException {
        bos.write(s.getBytes("US-ASCII"));
    }

    private static void writeInt(BufferedOutputStream bos, int v) throws IOException {
        bos.write(v & 0xFF);
        bos.write((v >> 8) & 0xFF);
        bos.write((v >> 16) & 0xFF);
        bos.write((v >> 24) & 0xFF);
    }

    private static void writeShort(BufferedOutputStream bos, int v) throws IOException {
        bos.write(v & 0xFF);
        bos.write((v >> 8) & 0xFF);
    }
}
