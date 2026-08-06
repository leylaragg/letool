package com.github.leyland.letool.file.validation;

/**
 * 根据有限文件头信息探测文件类型的扩展接口。
 */
@FunctionalInterface
public interface FileTypeDetector {

    /**
     * 探测文件类型。
     *
     * @param header 文件头副本，长度由门面限制
     * @param fileName 已清洗文件名
     * @param declaredContentType 调用方声明的媒体类型
     * @return 稳定类型名称；无法识别时返回 {@code UNKNOWN}
     */
    String detect(byte[] header, String fileName, String declaredContentType);
}
