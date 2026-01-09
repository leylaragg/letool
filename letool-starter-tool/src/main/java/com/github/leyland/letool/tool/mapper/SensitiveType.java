package com.github.leyland.letool.tool.mapper;

/**
 * 脱敏类型枚举
 *
 * @author leyland
 * @date 2025-01-08
 */
public enum SensitiveType {

    /**
     * 默认脱敏（保留前3后4）
     */
    DEFAULT(3, 4),

    /**
     * 中文名脱敏（张*）
     */
    CHINESE_NAME(1, 0),

    /**
     * 身份证号脱敏（保留前1后4）
     */
    ID_CARD(1, 4),

    /**
     * 手机号脱敏（保留前3后4）
     */
    MOBILE_PHONE(3, 4),

    /**
     * 地址脱敏（保留前6后4）
     */
    ADDRESS(6, 4),

    /**
     * 邮箱脱敏（保留第一个字符和@之后）
     */
    EMAIL(1, 0),

    /**
     * 银行卡号脱敏（保留前4后4）
     */
    BANK_CARD(4, 4),

    /**
     * 密码脱敏（全部脱敏）
     */
    PASSWORD(0, 0);

    private final int keepStart;
    private final int keepEnd;

    SensitiveType(int keepStart, int keepEnd) {
        this.keepStart = keepStart;
        this.keepEnd = keepEnd;
    }

    public int getKeepStart() {
        return keepStart;
    }

    public int getKeepEnd() {
        return keepEnd;
    }
}
