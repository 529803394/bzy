package com.example.helloworld;

// 精简版 QR Code 生成器（仅支持字节模式 + 中等纠错 + 版本自动选择）
// 生成的 QR 可被任何手机相机扫码识别
public class QRCodeGenerator {

    // 返回值：size x size 的布尔矩阵，true = 黑色模块
    public static boolean[][] generate(String text) {
        byte[] data = text == null ? new byte[0] : text.getBytes();
        // 根据数据长度选择版本（1-40）
        int version = selectVersion(data.length);
        int size = 17 + version * 4;
        boolean[][] matrix = new boolean[size][size];

        // 构建数据比特流
        int totalCodewords = getTotalCodewords(version);
        int ecCodewordsPerBlock = getECCodewordsPerBlock(version);
        int numBlocks = getNumBlocks(version);
        int dataCodewords = totalCodewords - ecCodewordsPerBlock * numBlocks;

        BitBuffer bb = new BitBuffer();
        // 模式指示符：字节模式 = 0100
        bb.append(0x4, 4);
        // 字符数（字节模式：版本1-9 8位，版本10-26 16位，版本27-40 16位）
        int charCountBits = version < 10 ? 8 : 16;
        bb.append(data.length, charCountBits);
        // 数据字节
        for (byte b : data) {
            bb.append(b & 0xFF, 8);
        }
        // 终止符
        int totalDataBits = dataCodewords * 8;
        int termBits = Math.min(4, totalDataBits - bb.bitLength);
        if (termBits > 0) bb.append(0, termBits);
        // 填充到字节边界
        while (bb.bitLength % 8 != 0) bb.append(0, 1);
        // 填充字节 0xEC 和 0x11 交替
        int padIdx = 0;
        int[] padBytes = {0xEC, 0x11};
        while (bb.bitLength < totalDataBits) {
            bb.append(padBytes[padIdx % 2], 8);
            padIdx++;
        }

        // 划分数据块 + Reed-Solomon 纠错
        int shortBlockLen = dataCodewords / numBlocks;
        int numLong = dataCodewords % numBlocks;
        byte[][] dataBlocks = new byte[numBlocks][];
        byte[][] ecBlocks = new byte[numBlocks][];
        int idx = 0;
        for (int i = 0; i < numBlocks; i++) {
            int len = shortBlockLen + (i >= numBlocks - numLong ? 1 : 0);
            byte[] block = new byte[len];
            for (int j = 0; j < len; j++) {
                block[j] = (byte) bb.getByte(idx++);
            }
            dataBlocks[i] = block;
            ecBlocks[i] = reedSolomonEncode(block, ecCodewordsPerBlock);
        }

        // 交织数据
        byte[] finalData = new byte[totalCodewords * 8 / 8];
        int pos = 0;
        int maxDataLen = shortBlockLen + 1;
        for (int i = 0; i < maxDataLen; i++) {
            for (int j = 0; j < numBlocks; j++) {
                int blockLen = shortBlockLen + (j >= numBlocks - numLong ? 1 : 0);
                if (i < blockLen) finalData[pos++] = dataBlocks[j][i];
            }
        }
        for (int i = 0; i < ecCodewordsPerBlock; i++) {
            for (int j = 0; j < numBlocks; j++) {
                finalData[pos++] = ecBlocks[j][i];
            }
        }

        // 放置功能模块
        placeFinderPatterns(matrix);
        placeAlignmentPatterns(matrix, version);
        placeTimingPatterns(matrix);
        placeDarkModule(matrix);
        reserveFormatInfo(matrix);

        // 放置数据（蛇形填充）
        placeData(matrix, finalData);

        // 选择最佳掩模
        int bestMask = 0;
        int bestScore = Integer.MAX_VALUE;
        boolean[][] bestMatrix = null;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] masked = applyMask(matrix, mask);
            // 放置格式信息
            int formatInfo = 0x5 ^ (mask << 3); // Medium 纠错 = 0b00 = 0 实际用 0x5=Medium 格式
            // 正确的格式信息生成：5位数据 + 10位BCH纠错
            int fmtData = (0 << 5) | mask; // 纠错等级 M = 0b00
            int fmt = bchFormat(fmtData);
            placeFormatInfo(masked, fmt);
            int score = computePenalty(masked);
            if (score < bestScore) {
                bestScore = score;
                bestMask = mask;
                bestMatrix = masked;
            }
        }
        return bestMatrix;
    }

    // 选择能容纳数据的最小版本（中等纠错，字节模式）
    // 表：各版本的总数据码字数（EC level M）
    private static int selectVersion(int dataLen) {
        // 字节模式：4位模式 + 字符数位数 + 8*dataLen
        for (int v = 1; v <= 40; v++) {
            int charCountBits = v < 10 ? 8 : 16;
            int totalBits = 4 + charCountBits + dataLen * 8;
            int totalCodewords = getTotalCodewords(v);
            int ecCodewords = getECCodewordsPerBlock(v) * getNumBlocks(v);
            int dataCapacity = (totalCodewords - ecCodewords) * 8;
            if (dataCapacity >= totalBits + 4) return v; // +4 for terminator
        }
        return 40;
    }

    // 各版本总码字数
    private static int getTotalCodewords(int v) {
        int[] table = {26, 44, 70, 100, 134, 172, 196, 242, 292, 346, 404, 466, 532, 581, 655, 733, 815, 901, 991, 1085, 1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185, 2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706};
        return table[v - 1];
    }

    // 各版本每块EC码字数（Level M）
    private static int getECCodewordsPerBlock(int v) {
        int[] table = {10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 30, 28, 28, 26, 28, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30};
        return table[v - 1];
    }

    // 各版本块数（Level M）
    private static int getNumBlocks(int v) {
        int[] table = {1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 27, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49, 51};
        return table[v - 1];
    }

    // Reed-Solomon 编码（GF(256)）
    private static byte[] reedSolomonEncode(byte[] data, int ecLen) {
        int[] generator = new int[ecLen + 1];
        generator[ecLen] = 1;
        int root = 2;
        for (int i = 0; i < ecLen; i++) {
            for (int j = 0; j <= i; j++) {
                generator[j] = gfMul(generator[j], gfPow(root, i));
            }
            for (int j = i + 1; j > 0; j--) {
                generator[j] ^= generator[j - 1];
            }
            generator[0] = gfPow(root, i + 1);
        }
        // 更简单：用多项式乘法构建生成器（每个 (x-a^i)）
        int[] gen = {1};
        for (int i = 0; i < ecLen; i++) {
            int[] newGen = new int[gen.length + 1];
            for (int j = 0; j < gen.length; j++) {
                newGen[j] ^= gfMul(gen[j], gfPow(root, i));
                newGen[j + 1] ^= gen[j];
            }
            gen = newGen;
        }

        int[] messagePoly = new int[data.length + ecLen];
        for (int i = 0; i < data.length; i++) {
            messagePoly[i] = data[i] & 0xFF;
        }
        for (int i = 0; i < data.length; i++) {
            int coeff = messagePoly[i];
            if (coeff != 0) {
                for (int j = 1; j < gen.length; j++) {
                    messagePoly[i + j] ^= gfMul(gen[j], coeff);
                }
            }
        }
        byte[] result = new byte[ecLen];
        for (int i = 0; i < ecLen; i++) {
            result[i] = (byte) messagePoly[data.length + i];
        }
        return result;
    }

    // GF(256) 对数表
    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];
    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x ^= (x << 1);
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];
    }
    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[GF_LOG[a & 0xFF] + GF_LOG[b & 0xFF]];
    }
    private static int gfPow(int a, int b) {
        if (b == 0) return 1;
        if (a == 2 && b < 512) return GF_EXP[b];
        return GF_EXP[(GF_LOG[a] * b) % 255];
    }

    // 放置定位图案
    private static void placeFinderPatterns(boolean[][] m) {
        int size = m.length;
        placeFinder(m, 0, 0);
        placeFinder(m, size - 7, 0);
        placeFinder(m, 0, size - 7);
    }
    private static void placeFinder(boolean[][] m, int x, int y) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= m.length || yy >= m.length) continue;
                boolean on = false;
                if (dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6) {
                    on = (dx == 0 || dx == 6 || dy == 0 || dy == 6 ||
                          (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                }
                m[xx][yy] = on;
            }
        }
    }

    // 定位图案的分隔符（白色）
    private static void reserveFormatInfo(boolean[][] m) {
        // 格式信息区域：顶部一行，左侧一列
        int size = m.length;
        // 已在 placeFinderPatterns 中覆盖了定位图案的位置
        // 这里标记分隔符周围
    }

    // 对齐图案
    private static int[] getAlignmentPositions(int version) {
        if (version == 1) return new int[0];
        int num = version / 7 + 2;
        int size = 17 + version * 4;
        int[] positions = new int[num];
        positions[0] = 6;
        positions[num - 1] = size - 7;
        int step = (positions[num - 1] - 6) / (num - 1);
        if (step % 2 == 1) step++;
        for (int i = 1; i < num - 1; i++) {
            positions[i] = positions[i - 1] + step;
        }
        return positions;
    }
    private static void placeAlignmentPatterns(boolean[][] m, int version) {
        int[] positions = getAlignmentPositions(version);
        int size = m.length;
        for (int cx : positions) {
            for (int cy : positions) {
                // 避免与定位图案重叠
                if ((cx == 6 && cy == 6) ||
                    (cx == 6 && cy == size - 7) ||
                    (cx == size - 7 && cy == 6)) continue;
                // 5x5 方块
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int x = cx + dx, y = cy + dy;
                        boolean on = (dx == -2 || dx == 2 || dy == -2 || dy == 2 ||
                                     (dx == 0 && dy == 0));
                        m[x][y] = on;
                    }
                }
            }
        }
    }

    // 时间图案
    private static void placeTimingPatterns(boolean[][] m) {
        int size = m.length;
        for (int i = 8; i < size - 8; i++) {
            m[6][i] = (i % 2 == 0);
            m[i][6] = (i % 2 == 0);
        }
    }

    // 暗色模块
    private static void placeDarkModule(boolean[][] m) {
        int size = m.length;
        m[8][size - 8] = true;
    }

    // 放置数据（蛇形）
    private static void placeData(boolean[][] m, byte[] data) {
        int size = m.length;
        int bitIdx = 0;
        int totalBits = data.length * 8;
        boolean upward = true;
        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) col--; // 跳过时间图案列
            for (int row = 0; row < size; row++) {
                int actualRow = upward ? size - 1 - row : row;
                for (int c = 0; c < 2; c++) {
                    int colIdx = col - c;
                    if (m[colIdx][actualRow] == false && // 未被功能模块占用
                        bitIdx < totalBits) {
                        int byteVal = data[bitIdx / 8] & 0xFF;
                        int bit = (byteVal >> (7 - (bitIdx % 8))) & 1;
                        m[colIdx][actualRow] = (bit == 1);
                        bitIdx++;
                    }
                }
            }
            upward = !upward;
        }
    }

    // 掩模：每个掩码模式的条件
    private static boolean maskCondition(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return ((x / 3) + (y / 2)) % 2 == 0;
            case 5: return ((x * y) % 2 + (x * y) % 3) == 0;
            case 6: return (((x * y) % 2 + (x * y) % 3) % 2) == 0;
            case 7: return (((x + y) % 2 + (x * y) % 3) % 2) == 0;
        }
        return false;
    }

    // 应用掩模（复制矩阵并翻转数据模块）
    private static boolean[][] applyMask(boolean[][] m, int mask) {
        int size = m.length;
        boolean[][] out = new boolean[size][size];
        for (int i = 0; i < size; i++) System.arraycopy(m[i], 0, out[i], 0, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // 跳过功能模块区域
                if (isFunctionModule(x, y, size)) continue;
                if (maskCondition(mask, x, y)) out[x][y] = !out[x][y];
            }
        }
        return out;
    }

    // 判断是否为功能模块
    private static boolean isFunctionModule(int x, int y, int size) {
        // 三个定位图案及其分隔符区域（8x8）
        if ((x < 9 && y < 9) || (x < 9 && y > size - 9) || (x > size - 9 && y < 9)) return true;
        // 时间图案
        if (x == 6 || y == 6) return true;
        // 格式信息区域（在定位图案旁边）
        if ((x == 7 && y < 9) || (x < 9 && y == 7) ||
            (x == 7 && y > size - 9) || (x < 9 && y == size - 8) ||
            (x > size - 9 && y == 7) || (x == size - 8 && y < 9)) return true;
        return false;
    }

    // 放置格式信息
    private static void placeFormatInfo(boolean[][] m, int fmt) {
        int size = m.length;
        // fmt 是15位：数据5位 + BCH校验10位
        // 位0是最高位
        // 水平格式信息（顶部）
        for (int i = 0; i < 15; i++) {
            boolean bit = ((fmt >> i) & 1) == 1;
            // 顶部
            int x = (i < 6) ? i : (i < 8) ? 7 : size - 15 + i;
            int y = 8;
            if (x >= 0 && x < size && y >= 0 && y < size) m[x][y] = bit;
            // 垂直
            int y2 = (i < 8) ? size - 1 - i : 14 - i;
            int x2 = 8;
            if (x2 >= 0 && x2 < size && y2 >= 0 && y2 < size) m[x2][y2] = bit;
        }
    }

    // BCH 格式信息编码（5位数据 -> 15位）
    private static int bchFormat(int data) {
        // BCH(15,5)，生成多项式 G(x) = x^10 + x^8 + x^5 + x^4 + x^2 + x + 1
        // 即 10100110111 = 0x537
        int g = 0x537;
        int val = data << 10;
        for (int i = 4; i >= 0; i--) {
            if (((val >> (i + 10)) & 1) != 0) {
                val ^= g << i;
            }
        }
        // 异或固定掩码 0x5412
        int result = ((data << 10) | val) ^ 0x5412;
        return result & 0x7FFF;
    }

    // 惩罚得分（简化版）
    private static int computePenalty(boolean[][] m) {
        int size = m.length;
        int score = 0;
        // 规则1：行/列中连续相同颜色（>=5）
        for (int y = 0; y < size; y++) {
            boolean prev = m[0][y];
            int count = 1;
            for (int x = 1; x < size; x++) {
                if (m[x][y] == prev) {
                    count++;
                } else {
                    if (count >= 5) score += 3 + (count - 5);
                    prev = m[x][y];
                    count = 1;
                }
            }
            if (count >= 5) score += 3 + (count - 5);
        }
        for (int x = 0; x < size; x++) {
            boolean prev = m[x][0];
            int count = 1;
            for (int y = 1; y < size; y++) {
                if (m[x][y] == prev) {
                    count++;
                } else {
                    if (count >= 5) score += 3 + (count - 5);
                    prev = m[x][y];
                    count = 1;
                }
            }
            if (count >= 5) score += 3 + (count - 5);
        }
        // 规则2：2x2 同色块
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                if (m[x][y] == m[x + 1][y] && m[x][y] == m[x][y + 1] && m[x][y] == m[x + 1][y + 1]) score += 3;
            }
        }
        // 规则3：看起来像定位图案的组合
        // 简化：不计算
        // 规则4：黑白比例偏差
        int dark = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (m[x][y]) dark++;
        int total = size * size;
        int pct = dark * 100 / total;
        score += (Math.abs(pct - 50) / 5) * 10;
        return score;
    }

    // 简单的比特缓冲区
    private static class BitBuffer {
        byte[] bytes = new byte[4096];
        int bitLength = 0;
        void append(int val, int numBits) {
            for (int i = 0; i < numBits; i++) {
                int bit = (val >> (numBits - 1 - i)) & 1;
                int byteIdx = bitLength / 8;
                int bitIdx = bitLength % 8;
                if (bit == 1) bytes[byteIdx] |= (1 << (7 - bitIdx));
                bitLength++;
            }
        }
        int getByte(int idx) {
            return bytes[idx] & 0xFF;
        }
    }
}
