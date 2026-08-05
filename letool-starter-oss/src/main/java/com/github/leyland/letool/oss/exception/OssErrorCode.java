package com.github.leyland.letool.oss.exception;

import com.github.leyland.letool.exception.code.ErrorCode;

/**
 * OSS 模块稳定错误码。
 */
public enum OssErrorCode implements ErrorCode {

    /** OSS 配置不合法。 */
    CONFIGURATION_INVALID("OSS_CONFIG_INVALID", "OSS 配置不合法：{0}"),

    /** 对象上传失败。 */
    UPLOAD_FAILED("OSS_UPLOAD_FAILED", "通过 {0} 上传对象失败：{1}/{2}"),

    /** 对象下载失败。 */
    DOWNLOAD_FAILED("OSS_DOWNLOAD_FAILED", "通过 {0} 下载对象失败：{1}/{2}"),

    /** 对象删除失败。 */
    DELETE_FAILED("OSS_DELETE_FAILED", "通过 {0} 删除对象失败：{1}/{2}"),

    /** 对象存在性检查失败。 */
    EXISTS_CHECK_FAILED("OSS_EXISTS_CHECK_FAILED", "通过 {0} 检查对象失败：{1}/{2}"),

    /** 预签名地址生成失败。 */
    PRESIGN_FAILED("OSS_PRESIGN_FAILED", "通过 {0} 生成对象预签名地址失败：{1}/{2}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建 OSS 错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认中文消息模板
     */
    OssErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 错误码
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
