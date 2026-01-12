package com.github.leyland.data.desensitize.rule;

import com.github.leyland.data.desensitize.RegexDesensitizeRule;

/**
 * 邮箱正则脱敏规则
 * 只显示第一个字符和@之后的内容
 *
 * @author leyland
 * @date 2025-01-12
 */
public class EmailRegexRule implements RegexDesensitizeRule {

    @Override
    public String regex() {
        return "(^.)[^@]*(@.*)$";
    }

    @Override
    public String replacement() {
        return "$1****$2";
    }
}
