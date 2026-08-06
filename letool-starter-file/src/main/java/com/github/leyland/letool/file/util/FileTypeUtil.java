package com.github.leyland.letool.file.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.List;

/**
 * 使用有限魔数表进行轻量文件类型识别的工具。
 *
 * <p>该工具只报告可以从固定文件头可靠判断的容器类型。DOCX、XLSX 和 PPTX
 * 都是 ZIP 容器，不能只读取几个字节就可靠区分，因此统一报告为 {@code ZIP}。</p>
 */
public final class FileTypeUtil {

    private static final int HEADER_LENGTH = 16;
    private static final List<Signature> SIGNATURES = List.of(
            new Signature("89504E470D0A1A0A", "PNG"),
            new Signature("89504E47", "PNG"),
            new Signature("FFD8FF", "JPEG"),
            new Signature("47494638", "GIF"),
            new Signature("424D", "BMP"),
            new Signature("38425053", "PSD"),
            new Signature("255044462D", "PDF"),
            new Signature("D0CF11E0", "OLE2"),
            new Signature("3C3F786D6C", "XML"),
            new Signature("504B0304", "ZIP"),
            new Signature("504B0506", "ZIP"),
            new Signature("504B0708", "ZIP"),
            new Signature("526172211A070100", "RAR"),
            new Signature("526172211A0700", "RAR"),
            new Signature("52617221", "RAR"),
            new Signature("1F8B08", "GZIP"),
            new Signature("494433", "MP3"),
            new Signature("000001BA", "MPEG"),
            new Signature("000001B3", "MPEG"),
            new Signature("CAFEBABE", "CLASS"),
            new Signature("EFBBBF", "TXT_UTF8_BOM"),
            new Signature("FFFE", "TXT_UTF16LE"),
            new Signature("FEFF", "TXT_UTF16BE"));

    private FileTypeUtil() {
    }

    /**
     * 从字节数组探测文件类型。
     *
     * @param header 文件头字节
     * @return 类型名称；无法识别时返回 {@code UNKNOWN}
     */
    public static String detect(byte[] header) {
        if (header == null || header.length == 0) {
            return "UNKNOWN";
        }
        int length = Math.min(header.length, HEADER_LENGTH);
        String hexadecimal = HexFormat.of().withUpperCase().formatHex(header, 0, length);
        for (Signature signature : SIGNATURES) {
            if (hexadecimal.startsWith(signature.hexadecimal())) {
                return signature.type();
            }
        }
        return "UNKNOWN";
    }

    /**
     * 从输入流读取有限文件头进行探测。
     *
     * <p>当输入流支持标记时会恢复读取位置；不支持标记时会消费文件头。</p>
     *
     * @param inputStream 文件输入流
     * @return 类型名称；读取失败时返回 {@code UNKNOWN}
     */
    public static String detect(InputStream inputStream) {
        if (inputStream == null) {
            return "UNKNOWN";
        }
        boolean resettable = inputStream.markSupported();
        if (resettable) {
            inputStream.mark(HEADER_LENGTH + 1);
        }
        try {
            byte[] header = inputStream.readNBytes(HEADER_LENGTH);
            if (resettable) {
                inputStream.reset();
            }
            return detect(header);
        } catch (IOException exception) {
            return "UNKNOWN";
        }
    }

    /**
     * 判断文件是否为常见图片。
     *
     * @param inputStream 文件输入流
     * @return 是否为已识别图片类型
     */
    public static boolean isImage(InputStream inputStream) {
        return switch (detect(inputStream)) {
            case "PNG", "JPEG", "GIF", "BMP" -> true;
            default -> false;
        };
    }

    /**
     * 判断文件是否为常见归档容器。
     *
     * @param inputStream 文件输入流
     * @return 是否为已识别归档类型
     */
    public static boolean isArchive(InputStream inputStream) {
        return switch (detect(inputStream)) {
            case "ZIP", "RAR", "GZIP" -> true;
            default -> false;
        };
    }

    /**
     * 不可变魔数签名。
     *
     * @param hexadecimal 大写十六进制文件头
     * @param type 类型名称
     */
    private record Signature(String hexadecimal, String type) {
    }
}
