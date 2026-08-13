package com.github.leyland.letool.print.document;

/**
 * 跨格式稳定的可选文档元数据。
 *
 * @param title 文档标题；没有标题时为 {@code null}
 * @param author 文档作者；没有作者时为 {@code null}
 * @param language BCP 47 风格语言标签；没有语言时为 {@code null}
 * @author leyland
 */
public record DocumentMetadata(String title, String author, String language) {

    /**
     * 创建文档元数据。
     *
     * @param title 文档标题；没有标题时为 {@code null}
     * @param author 文档作者；没有作者时为 {@code null}
     * @param language 语言标签；没有语言时为 {@code null}
     * @throws IllegalArgumentException 非空值为空白或超过安全长度时抛出
     */
    public DocumentMetadata {
        title = optional("title", title, 256);
        author = optional("author", author, 128);
        language = optional("language", language, 35);
    }

    /**
     * 返回不包含任何值的元数据。
     *
     * @return 空文档元数据
     */
    public static DocumentMetadata empty() {
        return new DocumentMetadata(null, null, null);
    }

    /** 校验可选文本。 */
    private static String optional(String name, String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " 不能为空白且不能超过 " + maxLength + " 个字符");
        }
        return value;
    }
}
