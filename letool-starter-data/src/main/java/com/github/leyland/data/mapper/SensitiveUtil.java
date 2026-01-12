package com.github.leyland.data.mapper;

/**
 * 数据脱敏工具类
 *
 * @author leyland
 * @date 2025-01-08
 */
public class SensitiveUtil {

    /**
     * 根据注解进行脱敏
     *
     * @param value 原始值
     * @param annotation 脱敏注解
     * @return 脱敏后的值
     */
    public static String desensitize(String value, Sensitive annotation) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        SensitiveType type = annotation.value();
        char maskChar = annotation.maskChar();
        String pattern = annotation.pattern();

        // 如果指定了自定义模式
        if (pattern != null && !pattern.isEmpty()) {
            return desensitizeWithPattern(value, pattern, maskChar, type);
        }

        // 使用默认策略
        return desensitizeWithType(value, type, maskChar);
    }

    /**
     * 使用自定义模式进行脱敏
     */
    private static String desensitizeWithPattern(String value, String pattern, char maskChar, SensitiveType type) {
        int start = type.getKeepStart();
        int end = type.getKeepEnd();

        // 对于邮箱特殊处理
        if (type == SensitiveType.EMAIL) {
            int atIndex = value.indexOf('@');
            if (atIndex > 0) {
                // @之前的保留start个字符，@之后全部保留
                String prefix = value.substring(0, Math.min(start, atIndex));
                String suffix = value.substring(atIndex);
                int maskLength = atIndex - start;
                return prefix + repeat(maskChar, maskLength) + suffix;
            }
        }

        return desensitizeWithType(value, type, maskChar);
    }

    /**
     * 使用类型进行脱敏
     */
    private static String desensitizeWithType(String value, SensitiveType type, char maskChar) {
        int length = value.length();
        int start = type.getKeepStart();
        int end = type.getKeepEnd();

        // 特殊处理：密码全部脱敏
        if (type == SensitiveType.PASSWORD) {
            return repeat(maskChar, length);
        }

        // 处理保留长度超过总长度的情况
        if (start + end >= length) {
            return value;
        }

        String prefix = value.substring(0, start);
        String suffix = value.substring(length - end);
        int maskLength = length - start - end;

        return prefix + repeat(maskChar, maskLength) + suffix;
    }

    /**
     * 重复字符
     */
    private static String repeat(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
