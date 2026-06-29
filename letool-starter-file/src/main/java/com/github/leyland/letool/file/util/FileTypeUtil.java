package com.github.leyland.letool.file.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于文件魔术数字（Magic Number）的文件类型检测工具。
 *
 * <p>通过读取文件头部的固定字节序列来判断文件真实类型，而非依赖文件扩展名。
 * 这种方式可以防止用户通过修改扩展名绕过类型限制，比扩展名校验更可靠。</p>
 *
 * <p><b>MAGIC_MAP 中注册的常见类型：</b></p>
 * <ul>
 *   <li>图片 — PNG, JPEG, GIF, BMP, PSD</li>
 *   <li>压缩 — ZIP (含三种变体), RAR, GZIP</li>
 *   <li>文档 — PDF, DOC, XML</li>
 *   <li>音视频 — MP3, MPEG</li>
 *   <li>Java — CLASS (0xCAFEBABE)</li>
 *   <li>文本 — UTF-8 BOM, UTF-16LE, UTF-16BE</li>
 * </ul>
 *
 * <p>注意：{@code detect()} 方法会消耗输入流的前 5 个字节，调用后流指针已移动。
 * 如需后续继续读取，请在调用前通过 {@link InputStream#mark(int)} 和 {@link InputStream#reset()}
 * 来重置，或重新创建流。</p>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class FileTypeUtil {

    /**
     * 魔术数字 -> 文件类型名称的映射表。
     *
     * <p>Key 为大写的十六进制字符串（文件头部字节），Value 为可读的文件类型标识。
     * 匹配时按前缀匹配（startsWith），因此 Key 长度可变。</p>
     */
    private static final Map<String, String> MAGIC_MAP = new HashMap<>();

    static {
        // ---- 图片类型 ----
        MAGIC_MAP.put("89504E47", "PNG");
        MAGIC_MAP.put("FFD8FF", "JPEG");
        MAGIC_MAP.put("47494638", "GIF");
        MAGIC_MAP.put("424D", "BMP");
        MAGIC_MAP.put("38425053", "PSD");

        // ---- 文档类型 ----
        MAGIC_MAP.put("255044462D", "PDF");
        MAGIC_MAP.put("D0CF11E0", "DOC");          // OLE2 复合文档（.doc / .xls 等）
        MAGIC_MAP.put("3C3F786D6C", "XML");

        // ---- 压缩/归档类型 ----
        MAGIC_MAP.put("504B0304", "ZIP");          // ZIP 文件头
        MAGIC_MAP.put("504B0506", "ZIP");          // ZIP 空归档
        MAGIC_MAP.put("504B0708", "ZIP");          // ZIP 跨卷归档
        MAGIC_MAP.put("52617221", "RAR");
        MAGIC_MAP.put("1F8B08", "GZIP");

        // ---- 音视频类型 ----
        MAGIC_MAP.put("494433", "MP3");
        MAGIC_MAP.put("000001BA", "MPEG");
        MAGIC_MAP.put("000001B3", "MPEG");

        // ---- Java 类型 ----
        MAGIC_MAP.put("CAFEBABE", "CLASS");

        // ---- 文本（Byte Order Mark） ----
        MAGIC_MAP.put("EFBBBF", "TXT_UTF8_BOM");
        MAGIC_MAP.put("FFFE", "TXT_UTF16LE");
        MAGIC_MAP.put("FEFF", "TXT_UTF16BE");
    }

    private FileTypeUtil() {}

    // ===== 类型检测 =====

    /**
     * 检测输入流对应文件的真实类型。
     *
     * <p>读取文件头部最多 5 个字节，将其转为十六进制，然后与 MAGIC_MAP 中的
     * 所有 Key 做前缀匹配，返回第一个匹配到的类型名。若无匹配则返回 {@code "UNKNOWN"}。</p>
     *
     * <p>特殊处理：以 {@code 504B} 开头且 hex 长度大于 6 时返回 {@code "DOCX"}
     * （因为 DOCX 本质是 ZIP 格式，需要额外判断以区分普通 ZIP 和 Office 文档）。</p>
     *
     * @param inputStream 文件输入流
     * @return 文件类型名称，如 "PNG"、"PDF"、"UNKNOWN"
     */
    public static String detect(InputStream inputStream) {
        try {
            byte[] header = new byte[5];
            int read = inputStream.read(header, 0, 5);
            if (read <= 0) return "UNKNOWN";

            String hex = bytesToHex(header, read).toUpperCase();

            // 前缀匹配魔术数字
            for (Map.Entry<String, String> entry : MAGIC_MAP.entrySet()) {
                if (hex.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // ZIP 格式扩展：504B 开头且长度足够 -> 判定为 DOCX
            if (hex.startsWith("504B") && hex.length() > 6) {
                return "DOCX";
            }

            return "UNKNOWN";
        } catch (IOException e) {
            return "UNKNOWN";
        }
    }

    /**
     * 判断文件是否为图片类型。
     *
     * @param inputStream 文件输入流
     * @return {@code true} 类型为 PNG/JPEG/GIF/BMP 之一
     */
    public static boolean isImage(InputStream inputStream) {
        String type = detect(inputStream);
        return "PNG".equals(type) || "JPEG".equals(type) || "GIF".equals(type) || "BMP".equals(type);
    }

    /**
     * 判断文件是否为压缩包类型。
     *
     * @param inputStream 文件输入流
     * @return {@code true} 类型为 ZIP/RAR/GZIP 之一
     */
    public static boolean isArchive(InputStream inputStream) {
        String type = detect(inputStream);
        return "ZIP".equals(type) || "RAR".equals(type) || "GZIP".equals(type);
    }

    // ===== 内部工具方法 =====

    /**
     * 将字节数组转换为大写十六进制字符串。
     *
     * @param bytes 字节数组
     * @param len   有效字节长度
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
