package com.github.leyland.data.desensitize;

/**
 * 正则脱敏规则接口
 * 定义基于正则表达式的脱敏规则
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface RegexDesensitizeRule {

    /**
     * 正则表达式
     *
     * @return 正则表达式
     */
    String regex();

    /**
     * 替换内容
     *
     * @return 替换内容
     */
    String replacement();
}
