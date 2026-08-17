package io.github.leylaragg.letool.file.validation;

import io.github.leylaragg.letool.file.util.FileTypeUtil;

/**
 * 使用有限魔数表进行轻量文件类型识别的默认实现。
 *
 * <p>该实现不尝试区分 DOCX、XLSX 等 ZIP 容器，也不替代病毒扫描或内容审核。</p>
 */
public final class MagicNumberFileTypeDetector implements FileTypeDetector {

    /**
     * 根据文件头执行轻量探测。
     *
     * @param header 文件头副本
     * @param fileName 已清洗文件名
     * @param declaredContentType 声明媒体类型
     * @return 探测类型名称
     */
    @Override
    public String detect(byte[] header, String fileName, String declaredContentType) {
        return FileTypeUtil.detect(header);
    }
}
