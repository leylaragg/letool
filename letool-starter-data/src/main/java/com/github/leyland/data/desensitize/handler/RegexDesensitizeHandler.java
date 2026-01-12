package com.github.leyland.data.desensitize.handler;

import com.github.leyland.data.desensitize.rule.RegexDesensitizeRule;

/**
 * 正则表达式脱敏处理器
 * 基于正则表达式进行灵活脱敏
 *
 * @author leyland
 * @date 2025-01-12
 */
public class RegexDesensitizeHandler implements DesensitizeHandler {

    @Override
    public String mask(String origin) {
        // 默认不做处理
        return origin;
    }

    /**
     * 使用正则脱敏
     *
     * @param origin 原始值
     * @param regex 正则表达式
     * @param replacement 替换内容
     * @return 脱敏后的值
     */
    public String mask(String origin, String regex, String replacement) {
        if (origin == null || regex == null) {
            return origin;
        }
        return origin.replaceAll(regex, replacement);
    }

    /**
     * 使用规则进行正则脱敏
     *
     * @param origin 原始值
     * @param rule 正则脱敏规则
     * @return 脱敏后的值
     */
    public String mask(String origin, RegexDesensitizeRule rule) {
        if (rule == null) {
            return origin;
        }
        return mask(origin, rule.regex(), rule.replacement());
    }
}
