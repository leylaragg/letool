package com.github.leyland.letool.data.desensitize.handler;

import com.github.leyland.letool.data.desensitize.rule.SlideDesensitizeRule;

/**
 * 滑块脱敏处理器
 * 支持保留前N位和后M位，中间脱敏
 *
 * @author leyland
 * @date 2025-01-12
 */
public class SlideDesensitizeHandler implements DesensitizeHandler {

    @Override
    public String mask(String origin) {
        // 默认保留前3后4
        return mask(origin, 3, 4);
    }

    /**
     * 滑块脱敏
     *
     * @param origin 原始值
     * @param leftPlainTextLen 左边保留长度
     * @param rightPlainTextLen 右边保留长度
     * @return 脱敏后的值
     */
    public String mask(String origin, int leftPlainTextLen, int rightPlainTextLen) {
        return mask(origin, leftPlainTextLen, rightPlainTextLen, false);
    }

    /**
     * 滑块脱敏
     *
     * @param origin 原始值
     * @param leftPlainTextLen 左边保留长度
     * @param rightPlainTextLen 右边保留长度
     * @param reverse 是否反转
     * @return 脱敏后的值
     */
    public String mask(String origin, int leftPlainTextLen, int rightPlainTextLen, boolean reverse) {
        return mask(origin, leftPlainTextLen, rightPlainTextLen, "*", reverse);
    }

    /**
     * 滑块脱敏
     *
     * @param origin 原始值
     * @param leftPlainTextLen 左边保留长度
     * @param rightPlainTextLen 右边保留长度
     * @param maskString 掩码字符串
     * @param reverse 是否反转
     * @return 脱敏后的值
     */
    public String mask(String origin, int leftPlainTextLen, int rightPlainTextLen, String maskString, boolean reverse) {
        if (origin == null || origin.isEmpty()) {
            return origin;
        }

        int length = origin.length();
        // 如果保留长度超过总长度，直接返回
        if (leftPlainTextLen + rightPlainTextLen >= length) {
            return origin;
        }

        StringBuilder sb = new StringBuilder(length);
        char[] chars = origin.toCharArray();

        for (int i = 0; i < length; i++) {
            if (i < leftPlainTextLen || i >= length - rightPlainTextLen) {
                // 保留部分
                sb.append(reverse ? maskString : chars[i]);
            } else {
                // 脱敏部分
                sb.append(reverse ? chars[i] : maskString);
            }
        }

        return sb.toString();
    }

    /**
     * 使用规则进行滑块脱敏
     *
     * @param origin 原始值
     * @param rule 滑块脱敏规则
     * @return 脱敏后的值
     */
    public String mask(String origin, SlideDesensitizeRule rule) {
        return mask(origin, rule, false);
    }

    /**
     * 使用规则进行滑块脱敏
     *
     * @param origin 原始值
     * @param rule 滑块脱敏规则
     * @param reverse 是否反转
     * @return 脱敏后的值
     */
    public String mask(String origin, SlideDesensitizeRule rule, boolean reverse) {
        if (rule == null) {
            return mask(origin);
        }
        return mask(origin, rule.leftKeep(), rule.rightKeep(), rule.maskChar(), reverse);
    }
}
