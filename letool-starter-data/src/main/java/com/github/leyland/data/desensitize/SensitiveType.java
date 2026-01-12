package com.github.leyland.data.desensitize;

/**
 * 脱敏类型枚举
 * 定义常用的脱敏类型
 *
 * @author leyland
 * @date 2025-01-12
 */
public enum SensitiveType {

    /**
     * 默认脱敏（保留前3后4）
     */
    DEFAULT,

    /**
     * 中文名脱敏（张*）
     */
    CHINESE_NAME,

    /**
     * 身份证号脱敏（保留前6后4）
     */
    ID_CARD,

    /**
     * 手机号脱敏（保留前3后4）
     */
    MOBILE_PHONE,

    /**
     * 地址脱敏（保留前6位）
     */
    ADDRESS,

    /**
     * 邮箱脱敏（保留第一个字符和@之后）
     */
    EMAIL,

    /**
     * 银行卡号脱敏（保留前6后4）
     */
    BANK_CARD,

    /**
     * 密码脱敏（全部脱敏）
     */
    PASSWORD,

    /**
     * 自定义滑块脱敏
     */
    CUSTOM_SLIDE,

    /**
     * 自定义正则脱敏
     */
    CUSTOM_REGEX,

    /**
     * 自定义索引脱敏
     */
    CUSTOM_INDEX
}
