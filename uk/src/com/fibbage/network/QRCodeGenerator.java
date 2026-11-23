package com.fibbage.network;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Self-contained pure-Java QR Code generator (ISO/IEC 18004), ZERO external dependencies.
 * Byte mode, error-correction level M, versions 1-9 (up to 180 bytes - plenty for a join URL).
 * Splits data into Reed-Solomon blocks and interleaves them exactly like the standard,
 * picks the best of the 8 mask patterns, and writes proper format/version information,
 * so ordinary phone cameras can read the code.
 */
public class QRCodeGenerator {

    // ECC level M, versions 1..9 (index = version - 1)
    private static final int[] ECC_PER_BLOCK = {10, 16, 26, 18, 24, 16, 18, 22, 22};
    private static final int[] NUM_BLOCKS    = { 1,  1,  1,  2,  2,  4,  4,  4,  5};
    private static final int MAX_VERSION = 9;

    // ---------- Reed-Solomon over GF(256), polynomial 0x11D ----------

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static int[] rsDivisor(int degree) {
        int[] result = new int[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = gfMul(result[j], root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static int[] rsRemainder(int[] data, int[] divisor) {
        int[] result = new int[divisor.length];
        for (int b : data) {
            int factor = b ^ result[0];
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= gfMul(divisor[i], factor);
            }
        }
        return result;
    }

    // ---------- capacity helpers ----------

    private static int rawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int ver) {
        return rawDataModules(ver) / 8 - ECC_PER_BLOCK[ver - 1] * NUM_BLOCKS[ver - 1];
    }

    private static int[] alignmentPositions(int ver) {
        if (ver == 1) return new int[0];
        int numAlign = ver / 7 + 2;
        int step = (ver * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = ver * 4 + 10; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    // ---------- main entry point ----------

    /** Returns the QR matrix: matrix[row][col] == true means a dark module. */
    public static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        int version = 1;
        while (version <= MAX_VERSION && 4 + 8 + bytes.length * 8 > dataCodewords(version) * 8) {
            version++;
        }
        if (version > MAX_VERSION) {
            version = MAX_VERSION;
            bytes = Arrays.copyOf(bytes, dataCodewords(version) - 2);
        }

        // 1. data bits: mode (byte) + length + payload + terminator + padding
        BitBuffer bb = new BitBuffer();
        bb.append(0b0100, 4);
        bb.append(bytes.length, 8);
        for (byte b : bytes) bb.append(b & 0xFF, 8);
        int capacityBits = dataCodewords(version) * 8;
        bb.append(0, Math.min(4, capacityBits - bb.length()));
        while (bb.length() % 8 != 0) bb.append(0, 1);
        for (int pad = 0xEC; bb.length() < capacityBits; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);

        int[] data = new int[capacityBits / 8];
        for (int i = 0; i < data.length; i++) data[i] = bb.getByte(i);

        // 2. split into blocks, add Reed-Solomon ECC, interleave
        int[] all = addEccAndInterleave(data, version);

        // 3. draw the symbol
        int size = version * 4 + 17;
        boolean[][] modules = new boolean[size][size];
        boolean[][] isFunction = new boolean[size][size];
        drawFunctionPatterns(modules, isFunction, version, size);
        drawCodewords(modules, isFunction, all, size);

        // 4. try all 8 masks, keep the one with the lowest penalty
        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(modules, isFunction, mask, size);
            drawFormatBits(modules, isFunction, mask, size);
            int penalty = penaltyScore(modules, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
            }
            applyMask(modules, isFunction, mask, size); // XOR again = undo
        }
        applyMask(modules, isFunction, bestMask, size);
        drawFormatBits(modules, isFunction, bestMask, size);
        return modules;
    }

    private static int[] addEccAndInterleave(int[] data, int version) {
        int numBlocks = NUM_BLOCKS[version - 1];
        int eccLen = ECC_PER_BLOCK[version - 1];
        int rawCodewords = rawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        int[][] dataBlocks = new int[numBlocks][];
        int[][] eccBlocks = new int[numBlocks][];
        int[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int len = shortBlockLen - eccLen + (i < numShortBlocks ? 0 : 1);
            dataBlocks[i] = Arrays.copyOfRange(data, k, k + len);
            eccBlocks[i] = rsRemainder(dataBlocks[i], divisor);
            k += len;
        }

        int[] result = new int[rawCodewords];
        int pos = 0;
        int maxDataLen = shortBlockLen - eccLen + 1;
        for (int i = 0; i < maxDataLen; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i < dataBlocks[j].length) result[pos++] = dataBlocks[j][i];
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < numBlocks; j++) result[pos++] = eccBlocks[j][i];
        }
        return result;
    }

    // ---------- drawing ----------

    private static void setFunction(boolean[][] m, boolean[][] f, int x, int y, boolean dark) {
        m[y][x] = dark;
        f[y][x] = true;
    }

    private static void drawFunctionPatterns(boolean[][] m, boolean[][] f, int version, int size) {
        // timing patterns
        for (int i = 0; i < size; i++) {
            setFunction(m, f, 6, i, i % 2 == 0);
            setFunction(m, f, i, 6, i % 2 == 0);
        }
        // three finder patterns (with separators)
        drawFinder(m, f, 3, 3, size);
        drawFinder(m, f, size - 4, 3, size);
        drawFinder(m, f, 3, size - 4, size);
        // alignment patterns
        int[] align = alignmentPositions(version);
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        setFunction(m, f, align[i] + dx, align[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormatBits(m, f, 0, size); // reserves the format areas (real bits are written later)
        drawVersionBits(m, f, version, size);
    }

    private static void drawFinder(boolean[][] m, boolean[][] f, int cx, int cy, int size) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    setFunction(m, f, x, y, dist != 2 && dist != 4);
                }
            }
        }
    }

    private static void drawFormatBits(boolean[][] m, boolean[][] f, int mask, int size) {
        int data = mask; // error-correction level M = 00, so (00 << 3) | mask
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;

        // first copy, around the top-left finder
        for (int i = 0; i <= 5; i++) setFunction(m, f, 8, i, bit(bits, i));
        setFunction(m, f, 8, 7, bit(bits, 6));
        setFunction(m, f, 8, 8, bit(bits, 7));
        setFunction(m, f, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(m, f, 14 - i, 8, bit(bits, i));

        // second copy, split between the other two finders
        for (int i = 0; i < 8; i++) setFunction(m, f, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(m, f, 8, size - 15 + i, bit(bits, i));
        setFunction(m, f, 8, size - 8, true); // the always-dark module
    }

    private static void drawVersionBits(boolean[][] m, boolean[][] f, int version, int size) {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3;
            int c = i / 3;
            setFunction(m, f, a, c, b);
            setFunction(m, f, c, a, b);
        }
    }

    private static void drawCodewords(boolean[][] m, boolean[][] f, int[] codewords, int size) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!f[y][x] && i < codewords.length * 8) {
                        m[y][x] = bit(codewords[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] f, int mask, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (!f[y][x] && invert) m[y][x] = !m[y][x];
            }
        }
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    // ---------- mask penalty (the four standard rules) ----------

    private static int penaltyScore(boolean[][] m, int size) {
        int result = 0;

        // Rule 1: runs of 5+ same-colour modules in a row / column
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? m[a][b] : m[b][a];
                    boolean prev = pass == 0 ? m[a][b - 1] : m[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    } else {
                        run = 1;
                    }
                }
            }
        }

        // Rule 2: 2x2 blocks of one colour
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = m[y][x];
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) result += 3;
            }
        }

        // Rule 3: finder-like 1:1:3:1:1 patterns
        boolean[] pA = {true, false, true, true, true, false, true, false, false, false, false};
        boolean[] pB = {false, false, false, false, true, false, true, true, true, false, true};
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                for (int b = 0; b <= size - 11; b++) {
                    boolean matchA = true, matchB = true;
                    for (int k = 0; k < 11; k++) {
                        boolean v = pass == 0 ? m[a][b + k] : m[b + k][a];
                        if (v != pA[k]) matchA = false;
                        if (v != pB[k]) matchB = false;
                    }
                    if (matchA) result += 40;
                    if (matchB) result += 40;
                }
            }
        }

        // Rule 4: dark / light balance
        int dark = 0;
        for (boolean[] row : m) for (boolean v : row) if (v) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += Math.max(0, k) * 10;
        return result;
    }

    /**
     * Converts a text/URL into a high-resolution, anti-aliased BufferedImage.
     */
    public static BufferedImage toBufferedImage(String text, int targetPixels) {
        boolean[][] matrix = encode(text);
        int modCount = matrix.length;
        int quietZone = 4; // the standard requires 4 blank modules around the code
        int totalCells = modCount + quietZone * 2;
        int scale = Math.max(1, targetPixels / totalCells);
        int finalSize = totalCells * scale;

        BufferedImage img = new BufferedImage(finalSize, finalSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, finalSize, finalSize);

        g.setColor(new Color(0x11, 0x11, 0x1A)); // Deep stylish dark modules
        for (int r = 0; r < modCount; r++) {
            for (int c = 0; c < modCount; c++) {
                if (matrix[r][c]) {
                    g.fillRect((c + quietZone) * scale, (r + quietZone) * scale, scale, scale);
                }
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Converts text/URL into PNG byte array for HTTP server.
     */
    public static byte[] toPngBytes(String text, int targetPixels) {
        BufferedImage img = toBufferedImage(text, targetPixels);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * Renders an ASCII text QR code suitable for terminal / console output.
     */
    public static String toAscii(String text) {
        boolean[][] matrix = encode(text);
        int size = matrix.length;
        int quiet = 2;
        StringBuilder sb = new StringBuilder();
        // Terminals are usually dark, so light modules are drawn as filled blocks:
        // the code then looks like normal black-on-white to a phone camera.
        for (int r = -quiet; r < size + quiet; r++) {
            sb.append("  ");
            for (int c = -quiet; c < size + quiet; c++) {
                boolean dark = r >= 0 && r < size && c >= 0 && c < size && matrix[r][c];
                sb.append(dark ? "  " : "\u2588\u2588");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static class BitBuffer {
        private final StringBuilder bits = new StringBuilder();

        public void append(int value, int length) {
            for (int i = length - 1; i >= 0; i--) {
                bits.append(((value >> i) & 1) == 1 ? '1' : '0');
            }
        }

        public int length() {
            return bits.length();
        }

        public boolean getBit(int index) {
            return bits.charAt(index) == '1';
        }

        public int getByte(int byteIdx) {
            int val = 0;
            int start = byteIdx * 8;
            for (int i = 0; i < 8; i++) {
                val = (val << 1) | (bits.charAt(start + i) - '0');
            }
            return val;
        }
    }
}
