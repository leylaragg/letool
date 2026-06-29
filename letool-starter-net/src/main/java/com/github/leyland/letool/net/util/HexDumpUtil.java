package com.github.leyland.letool.net.util;

/**
 * 十六进制转储工具 —— 以类似 Wireshark / tcpdump 风格格式化网络数据包的原始字节.
 *
 * <p>输出格式如下：</p>
 * <pre>{@code
 * 0000  48 65 6C 6C 6F 20 57 6F 72 6C 64 21 00 00 00 00   Hello World!....
 * 0010  0D 0A                                             ..
 * }</pre>
 *
 * <p>每行包含：偏移量（4 位十六进制）、16 字节十六进制值、可打印 ASCII 字符.
 * 不可打印字符用 {@code .} 代替.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class HexDumpUtil {

    /** 每行显示的字节数 */
    private static final int BYTES_PER_LINE = 16;

    private HexDumpUtil() {
    }

    // ======================== 公共方法 ========================

    /**
     * 将整个字节数组格式化为 Wireshark 风格的十六进制转储文本.
     *
     * @param bytes 待转储的字节数组
     * @return 格式化后的多行字符串，{@code bytes} 为 {@code null} 或空返回 "{empty}"
     */
    public static String dumpHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "{empty}";
        }
        return dumpHex(bytes, 0, bytes.length);
    }

    /**
     * 将字节数组的指定范围格式化为十六进制转储文本.
     *
     * @param bytes  源字节数组
     * @param offset 起始偏移
     * @param length 转储长度
     * @return 格式化后的多行字符串
     */
    public static String dumpHex(byte[] bytes, int offset, int length) {
        if (bytes == null || bytes.length == 0 || length <= 0) {
            return "{empty}";
        }
        if (offset < 0) {
            offset = 0;
        }
        int end = Math.min(offset + length, bytes.length);

        StringBuilder sb = new StringBuilder((end - offset) * 4 + 128);

        for (int base = offset; base < end; base += BYTES_PER_LINE) {
            // 偏移量
            sb.append(String.format("%04X  ", base - offset));

            int lineEnd = Math.min(base + BYTES_PER_LINE, end);
            // 十六进制部分
            StringBuilder hexPart = new StringBuilder();
            StringBuilder asciiPart = new StringBuilder();
            for (int i = base; i < lineEnd; i++) {
                int b = bytes[i] & 0xFF;
                hexPart.append(String.format("%02X ", b));
                asciiPart.append((b >= 0x20 && b < 0x7F) ? (char) b : '.');
            }
            // 补齐不足 16 字节的行
            int remaining = BYTES_PER_LINE - (lineEnd - base);
            for (int i = 0; i < remaining; i++) {
                hexPart.append("   ");
                asciiPart.append(' ');
            }
            sb.append(hexPart);
            sb.append(' ');
            sb.append(asciiPart);
            sb.append('\n');
        }
        return sb.toString();
    }
}
