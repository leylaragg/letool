package com.github.leyland.data.desensitize.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引脱敏处理器
 * 支持复杂的索引范围规则，如 "1,3-5,9-" 表示第2位、第4-6位、第10位及之后全部脱敏
 *
 * @author leyland
 * @date 2025-01-12
 */
public class IndexDesensitizeHandler implements DesensitizeHandler {

    @Override
    public String mask(String origin) {
        return origin;
    }

    /**
     * 使用索引规则脱敏
     *
     * @param origin 原始值
     * @param indexRules 索引规则数组，如 {"1", "3-5", "9-"}
     * @return 脱敏后的值
     */
    public String mask(String origin, String... indexRules) {
        return mask(origin, false, indexRules);
    }

    /**
     * 使用索引规则脱敏
     *
     * @param origin 原始值
     * @param reverse 是否反转规则
     * @param indexRules 索引规则数组
     * @return 脱敏后的值
     */
    public String mask(String origin, boolean reverse, String... indexRules) {
        return mask(origin, '*', reverse, indexRules);
    }

    /**
     * 使用索引规则脱敏
     *
     * @param origin 原始值
     * @param maskChar 掩码字符
     * @param reverse 是否反转规则
     * @param indexRules 索引规则数组
     * @return 脱敏后的值
     */
    public String mask(String origin, char maskChar, boolean reverse, String... indexRules) {
        if (origin == null || origin.isEmpty() || indexRules == null || indexRules.length == 0) {
            return origin;
        }

        char[] chars = origin.toCharArray();
        int length = chars.length;
        boolean[] maskFlags = new boolean[length];

        // 解析规则并标记需要脱敏的位置
        for (String rule : indexRules) {
            parseAndMarkRule(rule, length, maskFlags);
        }

        // 执行脱敏
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            boolean shouldMask = maskFlags[i];
            sb.append(reverse ? !shouldMask : shouldMask ? maskChar : chars[i]);
        }

        return sb.toString();
    }

    /**
     * 解析并标记规则
     *
     * @param rule 规则字符串
     * @param length 字符串长度
     * @param maskFlags 掩码标记数组
     */
    private void parseAndMarkRule(String rule, int length, boolean[] maskFlags) {
        // 匹配单个索引，如 "5"
        if (rule.matches("^\\d+$")) {
            int index = Integer.parseInt(rule);
            if (index >= 0 && index < length) {
                maskFlags[index] = true;
            }
            return;
        }

        // 匹配范围索引，如 "3-7" 或 "5-"
        Pattern pattern = Pattern.compile("^(\\d+)-(\\d*)$");
        Matcher matcher = pattern.matcher(rule);
        if (matcher.matches()) {
            int start = Integer.parseInt(matcher.group(1));
            String endStr = matcher.group(2);

            if (endStr.isEmpty()) {
                // 无结束索引，如 "5-"，表示从第5位开始到末尾
                for (int i = start; i < length; i++) {
                    if (i >= 0 && i < length) {
                        maskFlags[i] = true;
                    }
                }
            } else {
                // 有结束索引，如 "3-7"
                int end = Integer.parseInt(endStr);
                for (int i = start; i <= end && i < length; i++) {
                    if (i >= 0 && i < length) {
                        maskFlags[i] = true;
                    }
                }
            }
        }
    }
}
