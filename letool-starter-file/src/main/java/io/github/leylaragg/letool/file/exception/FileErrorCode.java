package io.github.leylaragg.letool.file.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 文件模块稳定错误码。
 */
public enum FileErrorCode implements ErrorCode {

    /** 文件操作参数不合法。 */
    PARAMETER_INVALID("FILE_001", "文件参数不合法：{0}"),

    /** 路径或存储键越过安全边界。 */
    UNSAFE_PATH("FILE_002", "文件路径或存储键不安全"),

    /** 上传内容不符合配置的接收规则。 */
    UPLOAD_REJECTED("FILE_003", "上传文件不符合接收规则：{0}"),

    /** 目标文件不存在。 */
    FILE_NOT_FOUND("FILE_004", "文件不存在"),

    /** 底层存储操作失败。 */
    STORAGE_OPERATION_FAILED("FILE_005", "文件存储操作失败"),

    /** 文件传输失败。 */
    TRANSFER_FAILED("FILE_006", "文件传输失败"),

    /** 压缩或解压操作失败。 */
    ARCHIVE_OPERATION_FAILED("FILE_007", "文件归档操作失败：{0}"),

    /** 文件模块配置不合法。 */
    CONFIGURATION_INVALID("FILE_008", "文件模块配置不合法：{0}"),

    /** 当前存储实现不支持所需能力。 */
    CAPABILITY_UNSUPPORTED("FILE_009", "当前文件存储不支持所需能力：{0}"),

    /** 断点续传状态发生冲突。 */
    RESUMABLE_STATE_CONFLICT("FILE_010", "断点续传状态冲突：{0}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建文件错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    FileErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 稳定错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认中文消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
