package com.github.leyland.letool.rule.util;

import java.util.Map;
import java.util.UUID;

/**
 * 规则引擎工具类
 *
 * @author leyland
 * @since 2026/02/14
 */
public final class RuleUtils {

    private RuleUtils() {
    }

    /**
     * 生成批次ID
     */
    public static String generateBatchId() {
        return "BATCH_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成验证ID
     */
    public static String generateValidationId() {
        return "VAL_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 安全获取字符串值
     */
    public static String getSafeString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 安全获取整数值
     */
    public static Integer getSafeInteger(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全获取长整数值
     */
    public static Long getSafeLong(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全获取双精度浮点值
     */
    public static Double getSafeDouble(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全获取布尔值
     */
    public static Boolean getSafeBoolean(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String strValue = value.toString().toLowerCase();
        return "true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue);
    }

    /**
     * 格式化消息
     */
    public static String formatMessage(String template, Object... args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        return String.format(template.replace("{}", "%s"), args);
    }

    /**
     * 判断是否为空
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof CharSequence) {
            return ((CharSequence) obj).length() == 0;
        }
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).isEmpty();
        }
        if (obj instanceof Iterable) {
            return !((Iterable<?>) obj).iterator().hasNext();
        }
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        return false;
    }

    /**
     * 判断是否不为空
     */
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
}
