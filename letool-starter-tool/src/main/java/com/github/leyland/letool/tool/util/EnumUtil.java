package com.github.leyland.letool.tool.util;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 枚举工具类——按 name、code 或自定义字段查找枚举值.
 *
 * <h3>约定的字段命名</h3>
 * <p>枚举类中通常定义 {@code code}（编码）和 {@code description}（描述）字段，
 * 本工具类的方法遵循此约定，但也支持任意自定义字段.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 枚举定义
 * public enum StatusEnum {
 *     ENABLED(1, "启用"),
 *     DISABLED(0, "禁用");
 *
 *     private final int code;
 *     private final String description;
 *     // getter...
 * }
 *
 * // 按名称查找
 * StatusEnum s = EnumUtil.getByName(StatusEnum.class, "ENABLED");
 *
 * // 按 code 字段查找
 * StatusEnum s = EnumUtil.getByCode(StatusEnum.class, 1);
 *
 * // 按任意字段查找
 * StatusEnum s = EnumUtil.getBy(StatusEnum.class, "description", "禁用");
 *
 * // 转为前端下拉框所需的 Map
 * Map<String, Object> map = EnumUtil.toMap(StatusEnum.class);
 * // → {"启用": 1, "禁用": 0}
 * }</pre>
 */
public final class EnumUtil {

    private EnumUtil() {}

    /**
     * 按枚举名称（{@link Enum#name()}）查找.
     *
     * @param enumClass 枚举类型
     * @param name      枚举名称（大小写敏感）
     * @param <E>       枚举泛型
     * @return 枚举值，找不到或 {@code name} 为 {@code null} 返回 {@code null}
     */
    public static <E extends Enum<E>> E getByName(Class<E> enumClass, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 按 {@code code} 字段查找枚举值.
     *
     * <p>要求枚举类定义名为 {@code code} 的字段，反射读取并与传入的 {@code code} 做 {@code equals} 比较.</p>
     *
     * @param enumClass 枚举类型
     * @param code      编码值
     * @param <E>       枚举泛型
     * @return 匹配的枚举值，找不到或 {@code code} 为 {@code null} 返回 {@code null}
     */
    public static <E extends Enum<E>> E getByCode(Class<E> enumClass, Object code) {
        if (code == null) return null;
        for (E e : enumClass.getEnumConstants()) {
            if (code.equals(getFieldValue(e, "code"))) return e;
        }
        return null;
    }

    /**
     * 按任意字段值查找枚举值.
     *
     * @param enumClass 枚举类型
     * @param field     字段名
     * @param value     期望值
     * @param <E>       枚举泛型
     * @return 匹配的枚举值，找不到或 {@code value} 为 {@code null} 返回 {@code null}
     */
    public static <E extends Enum<E>> E getBy(Class<E> enumClass, String field, Object value) {
        if (value == null) return null;
        for (E e : enumClass.getEnumConstants()) {
            if (value.equals(getFieldValue(e, field))) return e;
        }
        return null;
    }

    /**
     * 将枚举转为 label-value 映射，默认 label 取自 {@code description} 字段，value 取自 {@code code} 字段.
     *
     * <p>如果枚举没有 {@code description} 字段，label 降级为 {@link Enum#name()}.
     * 如果枚举没有 {@code code} 字段，value 降级为 {@link Enum#name()}.</p>
     *
     * @param enumClass 枚举类型
     * @param <E>       枚举泛型
     * @return label → value 的 {@link LinkedHashMap}（保持枚举声明顺序）
     */
    public static <E extends Enum<E>> Map<String, Object> toMap(Class<E> enumClass) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (E e : enumClass.getEnumConstants()) {
            String label = String.valueOf(getFieldValue(e, "description", e.name()));
            Object value = getFieldValue(e, "code", e.name());
            result.put(label, value);
        }
        return result;
    }

    /**
     * 反射获取字段值（字段不存在则返回 null）.
     */
    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射获取字段值，字段不存在则返回默认值.
     */
    private static Object getFieldValue(Object obj, String fieldName, Object defaultValue) {
        Object val = getFieldValue(obj, fieldName);
        return val != null ? val : defaultValue;
    }
}
