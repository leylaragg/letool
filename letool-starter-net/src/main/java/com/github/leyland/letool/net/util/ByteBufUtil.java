package com.github.leyland.letool.net.util;

/**
 * 字节缓冲区工具 —— 提供字节数组的拼接、截取、查找、十六进制转换等底层操作.
 *
 * <p>所有方法均为空安全：传入 {@code null} 不会抛出 NPE，而是返回安全的默认值.</p>
 *
 * <p>该类主要用于协议编解码器内部，作为处理原始网络字节流的辅助工具.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class ByteBufUtil {

    private ByteBufUtil() {
    }

    // ======================== 拼接 ========================

    /**
     * 拼接多个字节数组.
     *
     * @param arrays 变长参数，待拼接的字节数组序列
     * @return 拼接后的新字节数组，若所有参数均为 {@code null} 或空返回长度为 0 的空数组
     */
    public static byte[] concat(byte[]... arrays) {
        if (arrays == null || arrays.length == 0) {
            return new byte[0];
        }
        // 计算总长度
        int totalLen = 0;
        for (byte[] arr : arrays) {
            if (arr != null) {
                totalLen += arr.length;
            }
        }
        if (totalLen == 0) {
            return new byte[0];
        }
        // 拷贝
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] arr : arrays) {
            if (arr != null && arr.length > 0) {
                System.arraycopy(arr, 0, result, pos, arr.length);
                pos += arr.length;
            }
        }
        return result;
    }

    // ======================== 截取 ========================

    /**
     * 从源数组中提取指定范围的子数组.
     *
     * @param src    源字节数组
     * @param offset 起始偏移（含）
     * @param length 提取长度
     * @return 子数组，若 {@code src} 为 {@code null} 返回 {@code null}
     * @throws ArrayIndexOutOfBoundsException 如果 offset 或 length 越界
     */
    public static byte[] subArray(byte[] src, int offset, int length) {
        if (src == null) {
            return null;
        }
        if (offset < 0 || length < 0 || offset + length > src.length) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset=" + offset + ", length=" + length + ", src.length=" + src.length);
        }
        byte[] result = new byte[length];
        System.arraycopy(src, offset, result, 0, length);
        return result;
    }

    // ======================== 查找 ========================

    /**
     * 在源数组中查找目标子数组首次出现的位置（Boyer-Moore 简化版）.
     *
     * @param src    源字节数组
     * @param target 待查找的目标字节数组
     * @return 首次出现的偏移量，未找到返回 -1，任意参数为 {@code null} 或空返回 -1
     */
    public static int indexOf(byte[] src, byte[] target) {
        if (src == null || target == null || src.length == 0 || target.length == 0 || src.length < target.length) {
            return -1;
        }
        outer:
        for (int i = 0; i <= src.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (src[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // ======================== 十六进制转换 ========================

    /**
     * 将字节数组转换为十六进制字符串.
     *
     * @param bytes 源字节数组
     * @return 大写十六进制字符串（无空格），{@code null} 或空数组返回空串
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    // ======================== 判空 ========================

    /**
     * 判断字节数组是否为空（{@code null} 或长度为 0）.
     *
     * @param bytes 待校验的字节数组
     * @return {@code true} 如果无有效数据
     */
    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    /**
     * 判断字节数组是否非空.
     *
     * @param bytes 待校验的字节数组
     * @return {@code true} 如果包含有效数据
     */
    public static boolean isNotEmpty(byte[] bytes) {
        return !isEmpty(bytes);
    }
}
