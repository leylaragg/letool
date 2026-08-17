package io.github.leylaragg.letool.cipher.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 加密模块稳定错误码。
 */
public enum CipherErrorCode implements ErrorCode {

    /** 调用参数不满足密码运算约束。 */
    INVALID_PARAMETER("CIPHER_001", "加密参数无效：{0}"),

    /** 密钥编码、类型或长度不合法。 */
    INVALID_KEY("CIPHER_002", "加密密钥无效：{0}"),

    /** 密文封装格式无法安全解析。 */
    INVALID_ENVELOPE("CIPHER_003", "密文封装无效：{0}"),

    /** 加密执行失败。 */
    ENCRYPTION_FAILED("CIPHER_004", "加密执行失败：{0}"),

    /** 解密或认证执行失败。 */
    DECRYPTION_FAILED("CIPHER_005", "解密执行失败：{0}"),

    /** 摘要、消息认证或数字签名执行失败。 */
    OPERATION_FAILED("CIPHER_006", "密码运算失败：{0}");

    private final String code;
    private final String defaultMessage;

    /**
     * 创建加密错误码。
     *
     * @param code 稳定错误码
     * @param defaultMessage 默认消息模板
     */
    CipherErrorCode(String code, String defaultMessage) {
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
     * 获取默认消息模板。
     *
     * @return 默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
